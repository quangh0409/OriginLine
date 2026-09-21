"use client";

import { useState } from "react";
import { Input } from "antd";
import { useTranslations } from "next-intl";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { SendOutlined } from "@ant-design/icons";
import { FormField } from "@/components/person-form/form-field";
import { zodResolver } from "@/lib/form/zod-resolver";
import {
  claimFailureOf,
  claimValidationDetail,
  isClaimValidationError,
  type ClaimFailure,
} from "@/lib/api/claim";
import { useRouter } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { ClaimButton, ClaimNotice, ClaimParagraph } from "./claim-chrome";
import { claimRoutes } from "./routes";
import { useSubmitClaim, type ClaimTarget } from "./use-claims";

/**
 * Biểu mẫu **"đây là tôi"** — hai câu hỏi, và chúng không ngang giá nhau.
 *
 * <h2>Phần tự giới thiệu là thứ quyết định; số điện thoại chỉ để gọi</h2>
 * Checklist §1.3 nói thẳng: vài dòng <em>con ông nào, bà nào, quê quán</em>
 * "mới là thứ Trưởng chi dùng để đối chiếu; số điện thoại chỉ giúp gọi kiểm
 * chứng". Giao diện phải phản ánh đúng thứ bậc ấy — nên phần tự giới thiệu là
 * một ô nhiều dòng, có câu hỏi gợi ý thật và một ví dụ viết sẵn, chứ không phải
 * ô "ghi chú thêm" nép ở cuối. Một lá đơn chỉ có số điện thoại là một lá đơn
 * Trưởng chi không quyết được, và nó sẽ nằm mãi trong hàng chờ.
 *
 * <h2>Vì sao có một ví dụ viết sẵn trong `placeholder`</h2>
 * "Vài dòng tự giới thiệu" là một yêu cầu <em>mở</em>, và yêu cầu mở là chỗ
 * người dùng thưa bỏ cuộc: họ không biết viết bao nhiêu thì đủ. Một câu mẫu
 * dài đúng ba dòng trả lời câu hỏi ấy mà không phải giảng giải.
 *
 * <h2>Ràng buộc độ dài tối thiểu là có chủ đích, và nó phải giải thích được</h2>
 * Một chữ "vâng" cũng vượt qua được `required`. Sàn độ dài chặn đúng lá đơn
 * rỗng nghĩa ấy — nhưng câu báo lỗi phải nói <b>viết thêm gì</b> ("ít nhất là
 * con ông nào, bà nào, quê quán"), chứ không phải "tối thiểu 30 ký tự". Con số
 * ký tự là ngôn ngữ của phần mềm, không phải của dòng họ (00 §2.5).
 */

/** Sàn để một lá đơn còn đối chiếu được. Xem javadoc ở trên. */
const TOI_THIEU_TU_GIOI_THIEU = 30;
/** Trần, khớp với `reason` của luồng đính chính — cùng một loại ô văn bản dài. */
const TOI_DA_TU_GIOI_THIEU = 2000;
/** Số ngắn nhất còn gọi được ở Việt Nam sau khi bỏ dấu cách và dấu gạch. */
const TOI_THIEU_SO_DIEN_THOAI = 9;

interface GiaTri {
  phone: string;
  introduction: string;
}

type Dich = (key: string, values?: Record<string, string | number>) => string;

function luocDo(t: Dich) {
  return z.object({
    phone: z
      .string()
      .trim()
      .min(1, t("form.errors.phoneRequired"))
      // Đếm CHỮ SỐ, không đếm ký tự: "0903 111 222" và "+84 903 111 222" đều
      // hợp lệ, còn "abcdefghij" thì không — mà cả hai cùng dài mười ký tự.
      .refine(
        (v) => (v.match(/\d/g)?.length ?? 0) >= TOI_THIEU_SO_DIEN_THOAI,
        t("form.errors.phoneTooShort")
      ),
    introduction: z
      .string()
      .trim()
      .min(1, t("form.errors.introductionRequired"))
      .min(TOI_THIEU_TU_GIOI_THIEU, t("form.errors.introductionTooShort"))
      .max(TOI_DA_TU_GIOI_THIEU, t("form.errors.introductionTooLong", { max: TOI_DA_TU_GIOI_THIEU })),
  });
}

export interface ClaimExistingFormProps {
  readonly target: ClaimTarget;
  /**
   * Máy chủ vừa từ chối vì một luật nghiệp vụ.
   *
   * Báo ngược lên thay vì tự vẽ lời từ chối: quyết định "ca nào trong bốn ca"
   * phải nằm ở <b>một</b> chỗ ({@code ClaimScreen}), nếu không tấm thẻ "ô đã
   * chọn" ở trên và khối từ chối ở dưới sẽ cùng mọc ra một nút "Chọn ô khác
   * trên phả đồ" — hai nút giống hệt nhau cạnh nhau, và người đọc phải đoán
   * chúng có khác nhau không.
   */
  readonly onBlocked: (failure: ClaimFailure) => void;
}

export function ClaimExistingForm({ target, onBlocked }: ClaimExistingFormProps) {
  const t = useTranslations("claim");
  const router = useRouter();
  const submit = useSubmitClaim();
  const [failure, setFailure] = useState<ClaimFailure | null>(null);
  /**
   * Câu máy chủ nói về CHÍNH thân yêu cầu vừa gửi — `detail` của một
   * `VALIDATION_FAILED` trần. Ở lại TRONG BIỂU MẪU, không thay cả màn: đây là
   * lỗi hình dạng dữ liệu, không phải một luật nghiệp vụ về ô đã chọn.
   */
  const [loiHinhDang, setLoiHinhDang] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<GiaTri>({
    defaultValues: { phone: "", introduction: "" },
    resolver: zodResolver(luocDo(t)),
    // Kiểm lúc rời ô, không kiểm theo từng phím gõ: một dòng đỏ nhấp nháy dưới
    // ô trong lúc người ta mới gõ được hai chữ là cách làm người dùng thưa bỏ
    // cuộc, và nhóm người dùng chính của màn này mở ứng dụng lần đầu.
    mode: "onBlur",
  });

  const onSubmit = handleSubmit((values) => {
    setFailure(null);
    setLoiHinhDang(null);
    submit.mutate(
      {
        kind: "EXISTING",
        personId: target.id,
        // Gửi NGUYÊN VĂN. Chuẩn hoá đầu số là luật của máy chủ; làm lại ở client
        // là bản sao thứ hai của một luật, và bản sao thứ hai là bản sẽ lệch.
        phone: values.phone.trim(),
        introduction: values.introduction.trim(),
      },
      {
        onSuccess: () => {
          // Đi thẳng sang màn "đang chờ duyệt". Đó chính là lý do màn ấy tồn
          // tại: gửi xong mà rơi vào im lặng thì người dùng sẽ gửi lại lần hai.
          // `replace` chứ không `push` — bấm Quay lại phải về phả đồ, không
          // quay về một biểu mẫu đã gửi rồi.
          router.replace(claimRoutes.pending);
        },
        onError: (error) => {
          // `VALIDATION_FAILED` TRẦN là lỗi HÌNH DẠNG (điện thoại quá 32 ký
          // tự, giới thiệu quá 2000) — không phải một luật về ô đã chọn. Hiện
          // thẳng lý do thật của máy chủ, ở lại trong biểu mẫu, thay vì rơi
          // vào nhánh UNAVAILABLE bên dưới và bảo người dùng "thử lại sau" cho
          // một thứ sẽ không bao giờ tự hết.
          if (isClaimValidationError(error)) {
            setLoiHinhDang(claimValidationDetail(error) ?? t("block.unavailable.body"));
            return;
          }
          const failed = claimFailureOf(error);
          setFailure(failed);
          // Lỗi đường truyền ở lại trong biểu mẫu (người dùng bấm gửi lại là
          // xong, và những gì họ vừa gõ phải còn nguyên). Lỗi nghiệp vụ thì
          // đổi cả màn, nên nó đi lên trên.
          if (failed !== "UNAVAILABLE" && failed !== "RATE_LIMITED") onBlocked(failed);
        },
      }
    );
  });

  // Lỗi nghiệp vụ đã được báo lên `ClaimScreen`, và màn ấy thay cả biểu mẫu
  // bằng câu trả lời mới — người dùng cần biết trạng thái MỚI, không chỉ biết
  // "bấm không được". Ở đây chỉ còn lại lỗi đường truyền, và nó ở lại trong
  // biểu mẫu để những gì vừa gõ không mất.

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <ClaimNotice titleId="claim-form-title" title={t("form.title")}>
        <ClaimParagraph>{t("form.lead")}</ClaimParagraph>

        <div className="space-y-5 pt-1">
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
                    // `autoSize` bị tắt có chủ ý: ô tự cao lên làm nút Gửi nhảy
                    // xuống dưới tầm nhìn trên màn 400px, ngay giữa lúc gõ.
                    placeholder={t("form.introductionPlaceholder")}
                    aria-describedby={describedBy}
                    required
                  />
                )}
              </FormField>
            )}
          />
        </div>

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
          <ClaimButton
            type="submit"
            disabled={submit.isPending}
            icon={<SendOutlined />}
          >
            {submit.isPending ? t("form.submitting") : t("form.submit")}
          </ClaimButton>
        </div>
      </ClaimNotice>
    </form>
  );
}
