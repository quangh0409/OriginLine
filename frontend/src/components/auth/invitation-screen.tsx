"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Skeleton } from "antd";
import { CheckCircleOutlined } from "@ant-design/icons";
import { useAuth } from "@/lib/auth/auth-context";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import {
  invitationApi,
  invitationFailureOf,
  type InvitationAcceptedDto,
  type InvitationFailure,
  type InvitationPreviewDto,
} from "@/lib/api/invitation";
import { colorVars } from "@/styles/tokens";
import { ContactInviterNotice } from "./contact-inviter-notice";
import { InvitationCard } from "./invitation-card";
import { InvitationProblem } from "./invitation-problem";

export interface InvitationScreenProps {
  /** Mã lấy từ đoạn đường dẫn `/moi/<mã>`. */
  readonly code: string;
  /**
   * Rời khỏi ứng dụng để sang trang đặt mật khẩu của Keycloak.
   *
   * Tiêm được vào là cố ý: mặc định {@code window.location.assign} là một lối
   * ra khỏi SPA mà jsdom không thực hiện được, nên ca kiểm sẽ chỉ in một cảnh
   * báo "Not implemented: navigation" rồi <b>xanh mà không kiểm được gì</b>.
   * Tham số này cho phép khẳng định đúng điều quan trọng nhất của luồng: đi
   * đúng URL máy chủ trả về, không phải một URL trình duyệt tự ghép.
   */
  readonly onLeaveForPasswordSetup?: (url: string) => void;
}

/**
 * Màn nhận lời mời — `/moi/<mã>`, thiết kế 06 §5, dựng theo nhóm `invitations`
 * của `contracts/openapi.yaml`.
 *
 * <h2>Ba ca hỏng của mã, và ba ca hỏng của bước nhận</h2>
 * `/lookup` cho ra hết hạn · đã dùng · sai mã (cộng đã thu hồi). `/accept` cho
 * ra thêm ba ca nữa, tất cả đều <b>bình thường</b> và đều có câu chữ riêng:
 * chưa đăng nhập, tài khoản đã gắn người khác, nhân khẩu đã bị người khác nhận.
 * Phân nhánh đi theo {@code code} của RFC 7807 chứ không theo mã HTTP và không
 * theo {@code detail}.
 *
 * <h2>Hai kết cục của "nhận lời mời", và giao diện phải làm đúng cả hai</h2>
 * Bản thiết kế §5.2 khung 2 muốn máy chủ phát một URL một lần của Keycloak để
 * người nhận tự đặt mật khẩu. Backend hôm nay <b>không phát được</b> — nó là
 * resource server thuần, và sinh execute-actions token đòi Keycloak Admin REST
 * API đang được dựng. Nên:
 * <ul>
 *   <li><b>có {@code setPasswordUrl}</b> → rời SPA, sang Keycloak. Đây là hình
 *       dạng cuối và nó sẽ tự đúng vào ngày backend thêm trường ấy, không cần
 *       sửa một dòng nào ở đây;</li>
 *   <li><b>không có</b> → ở lại, và nói rõ việc đã xong: tài khoản đã nối,
 *       không chờ duyệt, đây là hai lối đi tiếp. Chuyển hướng tới
 *       {@code undefined} là cách chắc chắn nhất để hỏng vào đúng giây đầu tiên
 *       người dùng gặp hệ thống.</li>
 * </ul>
 *
 * <h2>Mã mời là bí mật; màn này không được nhân bản nó</h2>
 * Mã nằm sẵn trong URL của trình duyệt (không tránh được — đó là thứ in trên
 * phiếu mời). Việc của màn này là <b>không tạo thêm bản sao nào</b>: nó không
 * in mã ra màn hình, nó gửi mã trong <b>thân POST</b> chứ không trong path
 * (access log, bộ đệm trung gian), và khoá React Query không mang mã. Tiêu đề
 * trang, {@code referrer} và {@code robots} được xử ở tầng trang.
 *
 * <h2>Không tự thử lại</h2>
 * {@code retry: false}: gần hết các nhánh hỏng là câu trả lời <b>cuối cùng</b>
 * của máy chủ, và thử lại chỉ làm người dùng chờ ba lượt mạng để nhận cùng một
 * câu — tệ hơn nữa là ba lượt ấy đi thẳng vào bộ đếm chặn tần suất.
 */
export function InvitationScreen({ code, onLeaveForPasswordSetup }: InvitationScreenProps) {
  const t = useTranslations("auth.invitation");
  const { isAuthenticated, login } = useAuth();

  const [declined, setDeclined] = useState(false);
  const [accepted, setAccepted] = useState<InvitationAcceptedDto | null>(null);
  const [acceptFailure, setAcceptFailure] = useState<InvitationFailure | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const preview = useQuery<InvitationPreviewDto>({
    // Khoá truy vấn KHÔNG chứa mã mời: khoá của React Query đi vào DevTools và
    // vào mọi ảnh chụp trạng thái — một bản sao nữa của bí mật, ở nơi không ai
    // nghĩ tới. Màn này chỉ bao giờ mở đúng một lời mời nên không cần phân biệt.
    queryKey: ["invitation-preview"],
    queryFn: () => invitationApi.preview(code),
    retry: false,
    gcTime: 0,
    staleTime: 0,
    // Mã dùng một lần: một lượt nạp lại khi cửa sổ lấy lại tiêu điểm chỉ tổ
    // đẩy thêm một lượt gọi vào bộ đếm chặn tần suất.
    refetchOnWindowFocus: false,
  });

  const accept = useMutation({
    mutationFn: () => invitationApi.accept(code),
    onSuccess: (result) => {
      setActionError(null);
      setAcceptFailure(null);
      if (result.setPasswordUrl) {
        const leave = onLeaveForPasswordSetup ?? ((url: string) => window.location.assign(url));
        // Đi tới URL MÁY CHỦ TRẢ VỀ. Không ghép URL Keycloak ở client: chỗ ấy
        // mang token hành động một lần, và một URL tự ghép thì hoặc sai realm,
        // hoặc thiếu token, hoặc cả hai.
        leave(result.setPasswordUrl);
        return;
      }
      setAccepted(result);
    },
    onError: (error: unknown) => {
      const failure = invitationFailureOf(error);

      // Ba ca của riêng bước nhận: người dùng chưa đăng nhập, hoặc một trong
      // hai đầu của mối nối đã bị chiếm. Cả ba đều cần câu chữ riêng và một
      // lối đi tiếp — và KHÔNG được đọc thành "mã của ông/bà sai".
      if (
        failure === "NEEDS_ACCOUNT" ||
        failure === "ACCOUNT_ALREADY_LINKED" ||
        failure === "PERSON_ALREADY_LINKED"
      ) {
        setAcceptFailure(failure);
        return;
      }

      // Mã vừa hỏng ngay trước mắt (ai đó đã dùng, hoặc vừa hết hạn): đưa cả
      // màn về đúng nhánh ấy thay vì để tấm thẻ đứng nguyên với một dòng đỏ —
      // người dùng cần biết trạng thái mới, không chỉ biết "bấm không được".
      if (failure !== "UNAVAILABLE") {
        void preview.refetch();
        return;
      }
      setActionError(t("acceptFailed"));
    },
  });

  const decline = useMutation({
    mutationFn: () => invitationApi.decline(code),
    onSuccess: () => {
      setActionError(null);
      setDeclined(true);
    },
    onError: () => setActionError(t("declineFailed")),
  });

  if (preview.isPending) {
    return (
      <div data-invitation-state="LOADING" aria-busy="true">
        <p className="m-0 mb-3 text-than text-text-muted">{t("loading")}</p>
        <Skeleton active paragraph={{ rows: 6 }} />
      </div>
    );
  }

  if (preview.isError) {
    const failure: InvitationFailure = invitationFailureOf(preview.error);
    return (
      <InvitationProblem
        failure={failure}
        onRetry={() => void preview.refetch()}
        retrying={preview.isFetching}
      />
    );
  }

  if (acceptFailure) {
    return <InvitationProblem failure={acceptFailure} />;
  }

  if (accepted) {
    return (
      <section
        data-invitation-state="ACCEPTED"
        role="status"
        aria-labelledby="invitation-accepted-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.successBg, borderColor: colorVars.borderDark }}
      >
        <h1
          id="invitation-accepted-title"
          className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main"
        >
          <CheckCircleOutlined aria-hidden style={{ color: colorVars.successText }} />
          {t("acceptedTitle")}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("acceptedBody")}
        </p>
        {/* CHƯA ĐĂNG NHẬP thì hai liên kết dưới đây dẫn thẳng vào bức tường khách.
            `POST /invitations/accept` KHÔNG còn đòi token (hợp đồng đổi khi adapter
            Keycloak Admin ra đời), nên tới được đây mà chưa có phiên là chuyện BÌNH
            THƯỜNG: `setPasswordUrl` vắng nghĩa là tài khoản đã có mật khẩu từ trước —
            người này là thành viên cũ vừa nhận thêm một lời mời. Hồ sơ của họ là dữ
            liệu người còn sống, và phả đồ với khách chỉ có các cụ đã khuất; cho họ hai
            lối ấy là hứa một thứ rồi đưa tới một thứ khác. */}
        {!isAuthenticated ? (
          <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
            <button
              type="button"
              onClick={() => login()}
              className="inline-flex min-h-[44px] items-center rounded-lg border px-4 text-than font-semibold"
              style={{
                borderColor: colorVars.primary,
                color: colorVars.bgCard,
                background: colorVars.primary,
              }}
            >
              {t("acceptedSignIn")}
            </button>
            <p className="m-0 max-w-prose text-than text-text-muted">
              {t("acceptedSignInHint")}
            </p>
          </div>
        ) : (
        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          {/* Hồ sơ của chính mình đứng trước phả đồ, nhưng CẢ HAI đều có mặt:
              thiết kế 03 §6.4 cảnh báo rằng hạ cánh ở hồ sơ cá nhân (gần như
              trống) hay ở phả đồ (một bức tường người lạ) đều là lối vào tồi.
              Cho hai lối và để người dùng chọn là câu trả lời trung thực khi
              màn "họ nhà chồng" chưa tồn tại. */}
          <Link
            href={`/persons/${accepted.personId}`}
            className="inline-flex min-h-[44px] items-center rounded-lg border px-4 text-than font-semibold no-underline"
            style={{
              borderColor: colorVars.primary,
              color: colorVars.primary,
              background: colorVars.bgCard,
            }}
          >
            {t("acceptedOpenProfile")}
          </Link>
          <Link
            href="/tree"
            className="inline-flex min-h-[44px] items-center text-than underline"
            style={{ color: colorVars.primary }}
          >
            {t("acceptedOpenTree")}
          </Link>
        </div>
        )}
      </section>
    );
  }

  if (declined) {
    return (
      <div className="space-y-4">
        <section
          data-invitation-state="DECLINED"
          role="status"
          aria-labelledby="invitation-declined-title"
          className="rounded-lg border px-4 py-5 sm:px-6"
          style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
        >
          <h1
            id="invitation-declined-title"
            className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main"
          >
            <CheckCircleOutlined aria-hidden style={{ color: colorVars.accentText }} />
            {t("declinedTitle")}
          </h1>
          <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
            {t("declinedBody")}
          </p>
        </section>
        <ContactInviterNotice />
      </div>
    );
  }

  return (
    <InvitationCard
      preview={preview.data}
      onAccept={() => accept.mutate()}
      onDecline={() => decline.mutate()}
      accepting={accept.isPending}
      declining={decline.isPending}
      actionError={actionError}
    />
  );
}
