"use client";

import { useState } from "react";
import { Input, Radio, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { ApartmentOutlined, ClockCircleOutlined, InfoCircleOutlined, SendOutlined } from "@ant-design/icons";
import { FormField } from "@/components/person-form/form-field";
import { PersonPicker } from "@/components/person/person-picker";
import { zodResolver } from "@/lib/form/zod-resolver";
import {
  claimFailureOf,
  claimsOf,
  claimValidationDetail,
  isClaimValidationError,
  quotaOf,
  type ClaimFailure,
  type RelativeKind,
} from "@/lib/api/claim";
import { useRouter } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import type { Gender } from "@/types/api";
import { ClaimBlockNotice } from "./claim-block-notice";
import { ClaimButton, ClaimLink, ClaimNotice, ClaimParagraph } from "./claim-chrome";
import { ClaimQuotaLine } from "./claim-quota-line";
import { claimRoutes } from "./routes";
import { useMyClaims, useSubmitClaim } from "./use-claims";

/**
 * Lối **"Tôi chưa có trong phả"** — `/nhan-dien/chua-co`.
 *
 * <h2>Ba tình huống có thật, và chúng lặp lại VĨNH VIỄN</h2>
 * Con dâu mới về · cháu mới sinh · nhánh ở xa nhiều đời. Cả ba cầm mã mời hợp
 * lệ, đăng ký được, mở phả đồ ra và không tìm thấy mình. Checklist §1.5 nêu cái
 * giá của việc không có lối đi: họ sẽ <em>chọn bừa một người gần đúng</em> —
 * thường là người cùng tên, hoặc bố mình — và Trưởng chi nhận một lá đơn vô
 * nghĩa không hiểu vì sao. Việc ấy lặp lại với mọi đám cưới và mọi đứa trẻ mới
 * sinh, nên màn này không phải việc của đợt triển khai mà là việc thường ngày.
 * Vì thế phần "lối này dành cho ai" đứng <b>đầu trang</b>, gọi tên đủ ba người.
 *
 * <h2>Đây là lối GHI VÀO PHẢ, và hai ràng buộc của nó nhìn thấy được trên màn</h2>
 * <ul>
 *   <li><b>Không tạo nhân khẩu lúc gửi đơn</b> (ràng buộc 1). Khối "gửi đơn
 *       chưa phải là được ghi vào phả" nói thẳng điều đó, và nó <b>không</b> là
 *       một chi tiết kỹ thuật: người ta cần biết mình đang ở trạng thái nào.
 *       Một người tưởng mình đã vào phả sẽ không gọi cho ai cả, và sẽ chờ mãi.</li>
 *   <li><b>Bắt buộc chỉ ra người thân đã có trong phả</b> (ràng buộc 2) — và
 *       màn này <b>giải thích vì sao</b> thay vì gắn một dấu sao. Lý do thật
 *       (node mồ côi: không tính được đời, không tra được danh xưng, không chi
 *       nào biết đơn là của mình) là thứ người dùng hiểu được và chấp nhận
 *       được; một dấu sao thì chỉ là một rào cản không lời.</li>
 * </ul>
 *
 * Hai ràng buộc còn lại của §1.5 — chạy bộ dò trùng, và ghi nhân khẩu cùng ghi
 * quan hệ trong một transaction — sống hoàn toàn ở máy chủ. Kết quả dò trùng là
 * thông tin về <em>người khác trong phả</em> và người gửi đơn chưa được duyệt,
 * nên giao diện này không thấy nó và <b>không được</b> thấy nó.
 *
 * <h2>SAU KHI GỬI: hiện SỐ LƯỢNG nghi trùng, không hiện DANH TÍNH — và đây là
 * quyết định cố ý lệch khỏi câu chữ đề xuất trong tài liệu BA</h2>
 * Đề xuất viết: giao diện "nên hiện ngay cho người gửi: có phải bạn là người
 * này không?". Đọc theo nghĩa đen thì màn này phải liệt kê tên, năm sinh, đời
 * và chi của từng {@code ClaimDuplicateSuspectDto} — và đó chính xác là thứ
 * {@code PersonClaimService.KHONG_NHAN_DUOC} (mã gộp
 * {@code CLAIM_TARGET_UNAVAILABLE}) sinh ra để chặn: người gửi đơn <b>theo
 * định nghĩa là người chưa được duyệt</b>, và cho họ đọc tên + đời + chi của
 * những nhân khẩu nghi trùng chính là công cụ dò xem ai đã có trong phả và ai
 * chưa — gõ thử từng ô rồi đọc kết quả. Nhóm màn "tôi là ai trong phả"
 * ({@code claim-screen.tsx}, {@code claim-block-notice.tsx}) đã từ chối đúng
 * bề mặt này ở lối {@code EXISTING} (xem {@code PERSON_UNAVAILABLE}); mở nó ra
 * ở lối {@code NEW_PERSON} qua một cửa khác là tự mâu thuẫn với chính mình.
 *
 * <p>Cái được phép hiện, và chỉ chừng đó: <b>số lượng</b> hồ sơ nghi trùng,
 * cộng một câu điều hướng — "Trưởng chi sẽ đối chiếu trước khi duyệt; nếu ông/
 * bà biết rõ mình là ai trên phả đồ, xin quay lại chọn đúng ô ấy" — và một nút
 * quay lại phả đồ. Số lượng không định danh được ai (không tra ngược ra tên từ
 * một con số), nên nó không mở lại bề mặt dò mà {@code KHONG_NHAN_DUOC} đóng.
 * Ca kiểm của màn này ghim đúng ranh giới ấy: không một tên, năm sinh, đời hay
 * chi nào của bất kỳ nghi phạm nào được phép xuất hiện trong DOM.</p>
 */

const TOI_THIEU_TU_GIOI_THIEU = 30;
const TOI_DA_TU_GIOI_THIEU = 2000;
const TOI_THIEU_SO_DIEN_THOAI = 9;
/** Không ai còn sống sinh trước mốc này; chặn lỗi gõ nhầm bốn chữ số. */
const NAM_SINH_SOM_NHAT = 1900;

const QUAN_HE: readonly RelativeKind[] = ["FATHER", "MOTHER", "SPOUSE"];
const GIOI_TINH: readonly Gender[] = ["MALE", "FEMALE", "UNKNOWN"];

const KHOA_QUAN_HE: Readonly<Record<RelativeKind, string>> = {
  FATHER: "relationFather",
  MOTHER: "relationMother",
  SPOUSE: "relationSpouse",
};

const KHOA_GIOI_TINH: Readonly<Record<Gender, string>> = {
  MALE: "genderMale",
  FEMALE: "genderFemale",
  UNKNOWN: "genderUnknown",
};

interface GiaTri {
  fullName: string;
  birthYear: string;
  gender: Gender;
  phone: string;
  relativePersonId: string;
  relation: RelativeKind | "";
  introduction: string;
}

type Dich = (key: string, values?: Record<string, string | number>) => string;

function luocDo(t: Dich, namHienTai: number) {
  return z.object({
    fullName: z.string().trim().min(1, t("newPerson.errors.fullNameRequired")),
    birthYear: z
      .string()
      .trim()
      .min(1, t("newPerson.errors.birthYearRequired"))
      .refine((v) => {
        const n = Number(v);
        return Number.isInteger(n) && n >= NAM_SINH_SOM_NHAT && n <= namHienTai;
      }, t("newPerson.errors.birthYearRange", { min: NAM_SINH_SOM_NHAT, max: namHienTai })),
    gender: z.enum(["MALE", "FEMALE", "UNKNOWN"]),
    phone: z
      .string()
      .trim()
      .min(1, t("form.errors.phoneRequired"))
      .refine(
        (v) => (v.match(/\d/g)?.length ?? 0) >= TOI_THIEU_SO_DIEN_THOAI,
        t("form.errors.phoneTooShort")
      ),
    // Ô BẮT BUỘC, và câu báo lỗi nói ra **hệ quả** chứ không nói "trường bắt
    // buộc": thiếu nó thì hồ sơ mới không nối được vào cây. Đây là chỗ duy nhất
    // trong biểu mẫu mà một lời từ chối cụt lủn sẽ làm người dùng bỏ cuộc,
    // vì họ không hiểu vì sao phần mềm lại hỏi chuyện gia đình người khác.
    relativePersonId: z.string().trim().min(1, t("newPerson.errors.relativeRequired")),
    relation: z
      .string()
      .refine((v) => QUAN_HE.includes(v as RelativeKind), t("newPerson.errors.relationRequired")),
    introduction: z
      .string()
      .trim()
      .min(1, t("form.errors.introductionRequired"))
      .min(TOI_THIEU_TU_GIOI_THIEU, t("form.errors.introductionTooShort"))
      .max(TOI_DA_TU_GIOI_THIEU, t("form.errors.introductionTooLong", { max: TOI_DA_TU_GIOI_THIEU })),
  });
}

export function ClaimNewPersonScreen() {
  const t = useTranslations("claim");
  const router = useRouter();
  const mine = useMyClaims();
  const submit = useSubmitClaim();
  const [failure, setFailure] = useState<ClaimFailure | null>(null);
  /** `detail` của một `VALIDATION_FAILED` trần — xem javadoc `claimValidationDetail`. */
  const [loiHinhDang, setLoiHinhDang] = useState<string | null>(null);
  /**
   * Số hồ sơ nghi trùng trong ảnh chụp {@code ClaimView.duplicateSuspects} của
   * phản hồi gửi đơn thành công — KHÔNG BAO GIỜ danh tính. Xem javadoc đầu tệp
   * ("SAU KHI GỬI") cho lý do đây là ranh giới cố ý, không phải một thiếu sót.
   *
   * `null` = chưa gửi thành công (còn ở biểu mẫu, hoặc gửi thẳng vì không ai
   * bị nghi). Khác {@code 0}, thứ có nghĩa "đã gửi, không nghi ai" — nhưng ca
   * ấy đi thẳng sang màn "đang chờ duyệt" nên state này không bao giờ giữ số
   * 0 lâu hơn một lượt render.
   */
  const [soNghiTrung, setSoNghiTrung] = useState<number | null>(null);
  const namHienTai = new Date().getFullYear();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<GiaTri>({
    defaultValues: {
      fullName: "",
      birthYear: "",
      gender: "UNKNOWN",
      phone: "",
      relativePersonId: "",
      relation: "",
      introduction: "",
    },
    resolver: zodResolver(luocDo(t, namHienTai)),
    mode: "onBlur",
  });

  const onSubmit = handleSubmit((values) => {
    setFailure(null);
    setLoiHinhDang(null);
    submit.mutate(
      {
        kind: "NEW_PERSON",
        fullName: values.fullName.trim(),
        birthYear: Number(values.birthYear),
        gender: values.gender,
        phone: values.phone.trim(),
        relativePersonId: values.relativePersonId,
        relativeKind: values.relation as RelativeKind,
        introduction: values.introduction.trim(),
      },
      {
        onSuccess: (data) => {
          const soLuong = data.duplicateSuspects?.length ?? 0;
          // Rỗng là kết quả mong đợi của tuyệt đại đa số đơn: đi thẳng sang
          // màn "đang chờ duyệt" như trước. Có phần tử thì dừng lại một nhịp —
          // xem javadoc đầu tệp cho lý do chỉ hiện SỐ LƯỢNG.
          if (soLuong > 0) {
            setSoNghiTrung(soLuong);
            return;
          }
          router.replace(claimRoutes.pending);
        },
        onError: (error) => {
          // `VALIDATION_FAILED` TRẦN là lỗi HÌNH DẠNG thân yêu cầu (họ tên quá
          // 160 ký tự, giới thiệu quá 2000 …) — khác hẳn hai mã nghiệp vụ dưới
          // đây, và phải ở lại trong biểu mẫu thay vì rơi vào UNAVAILABLE.
          if (isClaimValidationError(error)) {
            setLoiHinhDang(claimValidationDetail(error) ?? t("block.unavailable.body"));
            return;
          }
          // `flow: "NEW_PERSON"` — máy chủ trả CÙNG mã `VALIDATION_FAILED` cho
          // "ô này không nhận đơn" và "người thân không dùng được"; chỉ lối
          // đang chạy mới phân biệt được hai câu ấy. Xem `claimFailureOf`.
          setFailure(claimFailureOf(error, "NEW_PERSON"));
        },
      }
    );
  });

  // Vừa gửi thành công VÀ có nghi vấn trùng: dừng lại một nhịp trước khi sang
  // màn "đang chờ duyệt". Đặt TRƯỚC mọi nhánh đọc `mine` bên dưới có chủ ý —
  // gửi xong thì `useSubmitClaim` vô hiệu hoá khoá `mine`, truy vấn nạp lại,
  // và lá đơn vừa tạo giờ có mặt trong đó với trạng thái PENDING. Nếu nhánh
  // này đứng sau `chanTheoTaiKhoan`, cùng phép kiểm ấy sẽ đọc ra
  // `CLAIM_ALREADY_PENDING` và đè mất đúng màn hình cần hiện ngay sau khi gửi.
  if (soNghiTrung !== null) {
    return (
      <div className="space-y-4">
        <ClaimNotice
          as="h1"
          tone="hophach"
          live
          dataState="DUPLICATE_SUSPECTS"
          titleId="claim-new-duplicate-title"
          icon={<InfoCircleOutlined />}
          title={t("newPerson.duplicateTitle")}
        >
          <ClaimParagraph>
            {t("newPerson.duplicateBody", { count: soNghiTrung })}
          </ClaimParagraph>
          <div className="flex flex-wrap gap-3">
            <ClaimButton onClick={() => router.replace(claimRoutes.pending)} icon={<ClockCircleOutlined />}>
              {t("newPerson.duplicateContinue")}
            </ClaimButton>
            {/* Nút quay lại màn chọn — cho người biết rõ mình là ai trên phả
                đồ một lối ra khỏi đơn "tôi chưa có trong phả" mà họ không cần
                gửi tới nơi. */}
            <ClaimLink href="/tree" icon={<ApartmentOutlined />}>
              {t("newPerson.duplicateBack")}
            </ClaimLink>
          </div>
        </ClaimNotice>
      </div>
    );
  }

  if (mine.isPending) {
    return (
      <div data-claim-state="LOADING" aria-busy="true">
        <p className="m-0 mb-3 text-than text-text-muted">{t("loading")}</p>
        <Skeleton active paragraph={{ rows: 5 }} />
      </div>
    );
  }

  // Không đọc được danh sách đơn thì KHÔNG dựng biểu mẫu: bộ đếm lượt và trạng
  // thái "đang có đơn mở" đều nằm trong phản hồi ấy, và dựng biểu mẫu mà không
  // biết hai điều đó là mời người dùng gõ xong một lá đơn chắc chắn bị từ chối.
  if (mine.isError) {
    return (
      <ClaimBlockNotice
        as="h1"
        failure={claimFailureOf(mine.error, "NEW_PERSON")}
        onRetry={() => void mine.refetch()}
      />
    );
  }

  const claims = claimsOf(mine.data);
  const quota = quotaOf(mine.data);

  /**
   * Hai lý do thuộc về **tài khoản** chặn cả lối này, y như chúng chặn lối kia —
   * một tài khoản chỉ ghép được vào một nhân khẩu, nên "tôi chưa có trong phả"
   * không phải một cửa sau đi vòng qua giới hạn số lần. Máy chủ gom hai phép
   * kiểm ấy vào {@code requireUnlinkedMember()} dùng chung cho cả hai lối,
   * chính vì lý do này.
   *
   * {@code quota} bắt buộc ở contract, nhưng {@link quotaOf} vẫn trả
   * {@code null} khi **truy vấn chưa xong** — và {@code null} phải đọc là "chưa
   * biết", không phải "còn 0 lần". Ngưỡng sống trong cấu hình máy chủ
   * ({@code giapha.membership.claim.max-rejected}) nên client **không được
   * đoán**: bước gửi trả {@code CLAIM_LIMIT_REACHED} dứt khoát, còn một phép
   * đoán sai ở đây sẽ chặn nhầm một người vẫn còn lượt.
   */
  const chanTheoTaiKhoan: ClaimFailure | null = claims.some((c) => c.status === "PENDING")
    ? "CLAIM_ALREADY_PENDING"
    : quota !== null && quota.remaining <= 0
      ? "CLAIM_LIMIT_REACHED"
      : null;

  if (chanTheoTaiKhoan) {
    return <ClaimBlockNotice as="h1" failure={chanTheoTaiKhoan} />;
  }

  /**
   * Ba lời từ chối **kết thúc lối đi** — tài khoản đã ghép, đang có đơn mở, hết
   * lượt — thay cả màn. Không lời nào trong ba lời ấy sửa được bằng cách gõ lại.
   *
   * {@code RELATIVE_UNUSABLE} thì <b>không</b>: nó sửa được bằng cách chọn một
   * người thân khác, và chỉ mỗi ô ấy sai. Thay cả màn ở đây sẽ ném đi họ tên,
   * năm sinh, số điện thoại và cả bài tự giới thiệu người dùng vừa gõ — để rồi
   * lối đi tiếp duy nhất là quay lại đúng biểu mẫu này và gõ lại từ đầu. Nên nó
   * ở lại, ngay cạnh ô nó nói về.
   */
  if (
    failure &&
    failure !== "UNAVAILABLE" &&
    failure !== "RATE_LIMITED" &&
    failure !== "RELATIVE_UNUSABLE"
  ) {
    return <ClaimBlockNotice as="h1" failure={failure} />;
  }

  return (
    <div className="space-y-4">
      <section
        aria-labelledby="claim-new-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
      >
        <h1 id="claim-new-title" className="m-0 font-serif text-de font-bold text-text-main">
          {t("newPerson.title")}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("newPerson.lead")}
        </p>

        <h2 className="m-0 mt-5 text-than font-semibold text-text-muted">
          {t("newPerson.casesTitle")}
        </h2>
        <ul className="m-0 mt-2 list-disc space-y-2 pl-6 text-dan leading-relaxed text-text-main">
          <li>{t("newPerson.caseSpouse")}</li>
          <li>{t("newPerson.caseChild")}</li>
          <li>{t("newPerson.caseFar")}</li>
        </ul>
      </section>

      {/* "Nhân khẩu CHƯA được tạo" đứng TRƯỚC biểu mẫu, không phải sau nút Gửi:
          nó là điều kiện để người dùng hiểu đúng việc mình sắp làm, chứ không
          phải một lời thanh minh sau khi đã làm xong. */}
      <ClaimNotice
        tone="hophach"
        dataState="NOT_CREATED_YET"
        titleId="claim-new-not-created"
        icon={<InfoCircleOutlined />}
        title={t("newPerson.notCreatedTitle")}
      >
        <ClaimParagraph>{t("newPerson.notCreatedBody")}</ClaimParagraph>
      </ClaimNotice>

      <ClaimQuotaLine quota={quota} showWhy />

      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {/* Tiêu đề KHÁC tiêu đề trang ở trên. `ClaimNotice` đặt
            `aria-labelledby`, nên trùng chữ là tạo ra hai vùng cùng tên khả
            truy cập — trình đọc màn hình đọc hai lần và mọi phép tra theo nhãn
            đều mơ hồ. Ở đây nó cũng đúng nghĩa hơn: trang nói "tôi chưa có
            trong phả", còn khối này khai NGƯỜI cần thêm. */}
        <ClaimNotice titleId="claim-new-form-title" title={t("newPerson.formTitle")}>
          <div className="space-y-5 pt-1">
            <Controller
              control={control}
              name="fullName"
              render={({ field }) => (
                <FormField
                  label={t("newPerson.fullName")}
                  error={errors.fullName?.message}
                  required
                >
                  {({ id, status, describedBy }) => (
                    <Input
                      {...field}
                      id={id}
                      size="large"
                      status={status}
                      autoComplete="name"
                      placeholder={t("newPerson.fullNamePlaceholder")}
                      aria-describedby={describedBy}
                      required
                    />
                  )}
                </FormField>
              )}
            />

            <Controller
              control={control}
              name="birthYear"
              render={({ field }) => (
                <FormField
                  label={t("newPerson.birthYear")}
                  hint={t("newPerson.birthYearHint")}
                  error={errors.birthYear?.message}
                  required
                >
                  {({ id, status, describedBy }) => (
                    <Input
                      {...field}
                      id={id}
                      size="large"
                      status={status}
                      // `inputMode="numeric"` mở bàn phím số trên điện thoại mà
                      // KHÔNG dùng `type="number"`: ô số của trình duyệt có hai
                      // mũi tên tăng/giảm cao chưa tới 44px và cuộn chuột trên
                      // nó đổi giá trị — hai cách hỏng thầm lặng với một con số
                      // mà người dùng chỉ gõ đúng một lần.
                      inputMode="numeric"
                      maxLength={4}
                      aria-describedby={describedBy}
                      required
                    />
                  )}
                </FormField>
              )}
            />

            <Controller
              control={control}
              name="gender"
              render={({ field }) => (
                <FormField label={t("newPerson.gender")} error={errors.gender?.message}>
                  {({ id, describedBy }) => (
                    <Radio.Group
                      id={id}
                      value={field.value}
                      onChange={(e) => field.onChange(e.target.value)}
                      aria-describedby={describedBy}
                      className="flex flex-wrap gap-2"
                    >
                      {/* `Radio.Button` chứ không phải `Radio` tròn: vùng chạm
                          của nó là cả ô, cao 44px theo `controlHeight` của chủ
                          đề — không đặt chiều cao bằng tay ở đây, vì một con số
                          chép tay là một bản sao sẽ lệch khỏi sàn chung. Nhãn
                          luôn là chữ đầy đủ, không phải ký hiệu. */}
                      {GIOI_TINH.map((g) => (
                        <Radio.Button key={g} value={g}>
                          {t(`newPerson.${KHOA_GIOI_TINH[g]}`)}
                        </Radio.Button>
                      ))}
                    </Radio.Group>
                  )}
                </FormField>
              )}
            />

            <Controller
              control={control}
              name="phone"
              render={({ field }) => (
                <FormField
                  label={t("form.phone")}
                  hint={t("form.phoneHint")}
                  error={errors.phone?.message}
                  required
                >
                  {({ id, status, describedBy }) => (
                    <Input
                      {...field}
                      id={id}
                      size="large"
                      status={status}
                      inputMode="tel"
                      autoComplete="tel"
                      aria-describedby={describedBy}
                      required
                    />
                  )}
                </FormField>
              )}
            />
          </div>
        </ClaimNotice>

        {/* Người thân đứng riêng một khối, có tiêu đề và có LỜI GIẢI THÍCH — xem
            javadoc đầu tệp. Gộp nó vào khối trên sẽ biến ràng buộc quan trọng
            nhất của cả lối này thành ô thứ năm trong một danh sách. */}
        <ClaimNotice titleId="claim-new-relative" title={t("newPerson.relativeTitle")}>
          <div
            className="rounded-lg border px-4 py-3"
            style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
          >
            <p className="m-0 flex items-start gap-2 text-than font-semibold text-text-main">
              <span aria-hidden className="mt-0.5 shrink-0" style={{ color: colorVars.accentText }}>
                <InfoCircleOutlined />
              </span>
              {t("newPerson.relativeWhyTitle")}
            </p>
            <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-main">
              {t("newPerson.relativeWhy")}
            </p>
          </div>

          <div className="space-y-5 pt-1">
            <Controller
              control={control}
              name="relativePersonId"
              render={({ field }) => (
                <FormField
                  label={t("newPerson.relative")}
                  error={errors.relativePersonId?.message}
                  required
                >
                  {({ id, status }) => (
                    <PersonPicker
                      id={id}
                      status={status}
                      value={field.value === "" ? undefined : field.value}
                      onChange={(next) => field.onChange(next ?? "")}
                      placeholder={t("newPerson.relativePlaceholder")}
                    />
                  )}
                </FormField>
              )}
            />

            {failure === "RELATIVE_UNUSABLE" && (
              <p
                role="alert"
                className="m-0 max-w-prose text-than leading-relaxed"
                style={{ color: colorVars.danger }}
              >
                {t("block.relativeUnusable.body")}
              </p>
            )}

            <Controller
              control={control}
              name="relation"
              render={({ field }) => (
                <FormField
                  label={t("newPerson.relation")}
                  hint={t("newPerson.relationHint")}
                  error={errors.relation?.message}
                  required
                >
                  {({ id, describedBy }) => (
                    <Radio.Group
                      id={id}
                      value={field.value === "" ? undefined : field.value}
                      onChange={(e) => field.onChange(e.target.value)}
                      aria-describedby={describedBy}
                      className="flex flex-wrap gap-2"
                    >
                      {QUAN_HE.map((r) => (
                        <Radio.Button key={r} value={r}>
                          {t(`newPerson.${KHOA_QUAN_HE[r]}`)}
                        </Radio.Button>
                      ))}
                    </Radio.Group>
                  )}
                </FormField>
              )}
            />
          </div>
        </ClaimNotice>

        {/* Tiêu đề khối KHÁC nhãn của ô bên trong, và đó không phải chuyện thẩm
            mỹ: `ClaimNotice` đặt `aria-labelledby`, nên một khối trùng tên với
            ô duy nhất trong nó tạo ra HAI phần tử cùng tên khả truy cập. Trình
            đọc màn hình đọc hai lần, và mọi phép tra theo nhãn đều mơ hồ. */}
        <ClaimNotice titleId="claim-new-intro" title={t("newPerson.introTitle")}>
          <Controller
            control={control}
            name="introduction"
            render={({ field }) => (
              <FormField
                label={t("form.introduction")}
                hint={t("form.introductionHint")}
                error={errors.introduction?.message}
                required
              >
                {({ id, status, describedBy }) => (
                  <Input.TextArea
                    {...field}
                    id={id}
                    size="large"
                    status={status}
                    rows={5}
                    placeholder={t("form.introductionPlaceholder")}
                    aria-describedby={describedBy}
                    required
                  />
                )}
              </FormField>
            )}
          />

          {loiHinhDang !== null && (
            <p
              role="alert"
              data-claim-error="validation"
              className="m-0 max-w-prose text-than"
              style={{ color: colorVars.danger }}
            >
              {loiHinhDang}
            </p>
          )}

          {(failure === "UNAVAILABLE" || failure === "RATE_LIMITED") && (
            <p
              role="alert"
              className="m-0 max-w-prose text-than"
              style={{ color: colorVars.danger }}
            >
              {t(`block.${failure === "RATE_LIMITED" ? "rateLimited" : "unavailable"}.body`)}
            </p>
          )}

          <div>
            <ClaimButton type="submit" disabled={submit.isPending} icon={<SendOutlined />}>
              {submit.isPending ? t("form.submitting") : t("newPerson.submit")}
            </ClaimButton>
          </div>
        </ClaimNotice>
      </form>
    </div>
  );
}
