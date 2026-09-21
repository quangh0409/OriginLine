"use client";

import { Button } from "antd";
import { CheckCircleOutlined, KeyOutlined, LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import type { ClanRegistrationDto } from "@/lib/api/clan-invite";
import { colorVars } from "@/styles/tokens";

export interface RegisterDoneProps {
  readonly registration: ClanRegistrationDto;
  /**
   * Tài khoản vừa lập bằng một **số điện thoại**, không phải thư điện tử.
   *
   * Dùng cho đúng một câu, và câu ấy nói về **khôi phục** chứ không về đăng ký:
   * khi hệ thống có SMTP, một tài khoản không có địa chỉ thư thì không nhận
   * được liên kết đặt lại mật khẩu. Nói điều đó ở ô nhập là chặn nhầm một người
   * mà máy chủ sẵn sàng nhận — nên nó nằm ở đây, cạnh nút đặt mật khẩu.
   */
  readonly loginIdIsPhone?: boolean;
  /**
   * Rời ứng dụng để đi tới liên kết đặt mật khẩu **máy chủ trả về**.
   *
   * Tiêm được vào là cố ý, cùng lý do với {@code InvitationScreen}: mặc định là
   * {@code window.location.assign}, thứ jsdom không thực hiện mà chỉ in một
   * cảnh báo "Not implemented: navigation" — ca kiểm sẽ <b>xanh mà không kiểm
   * được gì</b>. Tham số này cho phép khẳng định đúng điều quan trọng nhất: đi
   * tới URL máy chủ trả về, không phải một URL trình duyệt tự ghép.
   */
  readonly onLeaveForPasswordSetup?: (url: string) => void;
}

/**
 * **Bước 3 — tài khoản đã lập. Và câu hỏi thật sự: đi đâu bây giờ?**
 *
 * <h2>Trạng thái "có tài khoản, chưa gắn nhân khẩu" là ĐÚNG, không phải dở dang</h2>
 * {@code ClanRegistrationDto} không có {@code personId} và {@code status} luôn
 * là {@code PENDING}. Mã dòng họ không trỏ vào ai nên không có gì để ghép, và
 * <b>không được suy đoán</b>: ghép theo trùng tên là cách nhanh nhất để trao
 * cho một người quyền đọc dữ liệu Tầng 3 của người khác. Màn này phải nói ra
 * điều đó bằng tiếng người, nếu không người dùng sẽ đọc "PENDING" thành "đơn
 * của tôi bị treo".
 *
 * <h2>KHÔNG tự chuyển hướng đi đâu cả</h2>
 * Hai đích có thể có — liên kết đặt mật khẩu, và màn "tôi là ai trong phả" ở
 * {@code /nhan-dien} — đều là <b>liên kết người dùng bấm</b>, không phải một cú
 * {@code router.push} tự động. Lý do khác nhau ở hai đích:
 * <ul>
 *   <li><b>đặt mật khẩu</b>: một cú rời SPA tự động ngay sau khi lập tài khoản
 *       lấy mất của người dùng khoảnh khắc đọc được rằng việc đã xong, và lập
 *       tài khoản là việc <i>không làm lại được</i> (mã đã tiêu một lượt);</li>
 *   <li><b>{@code /nhan-dien}</b>: tuyến ấy do một mạch việc khác dựng và
 *       <b>có thể chưa tồn tại</b>. Một liên kết chưa có đích thì tệ nhất là
 *       một trang 404 người dùng bấm nút lùi để thoát ra, và câu chữ quanh nó
 *       vẫn nói đủ việc cần làm. Một cú chuyển hướng tự động tới cùng chỗ ấy
 *       thì ném họ vào 404 <i>ngay sau</i> thao tác không làm lại được, không
 *       kịp đọc một chữ nào.</li>
 * </ul>
 *
 * <h2>Vắng `setPasswordUrl` KHÔNG phải một lỗi — và nay nó chỉ còn nghĩa MỘT ca</h2>
 * Nếu máy chủ phát liên kết cho một tài khoản đã có chủ thì bất kỳ ai cầm mã
 * dòng họ — tức <b>cả họ</b> — cộng với việc đoán đúng thư điện tử của một
 * thành viên cũ sẽ đổi được mật khẩu của người ta. Vì vậy nó vắng, và vắng là
 * đúng.
 *
 * <p><b>Nhưng ca "vắng" đã đổi nghĩa sau bản vá lỗ hổng chiếm tài khoản.</b>
 * Trước kia, một người <i>chưa đăng nhập</i> khai một định danh đã có chủ thì
 * nhận {@code 200} không kèm liên kết — đó là ca mà câu "tài khoản này đã có
 * mật khẩu từ trước" được viết cho. Nay nhánh không-token <b>dừng lại sớm hơn
 * hẳn</b>: nó trả {@code 422 IDENTITY_ALREADY_REGISTERED} trước cả bước đúc
 * liên kết. Nên với máy chủ hôm nay, đường <b>duy nhất</b> tới đây là nhánh
 * <b>đã có token</b> — người vừa đăng nhập Google/Zalo rồi mới nhập mã. Với họ
 * thì câu cũ nói sai: họ không "đã có mật khẩu từ trước", họ <i>đang đăng nhập
 * sẵn</i>.</p>
 *
 * <p>{@code isAuthenticated} chính là thứ phân biệt hai ca, nên nó chọn luôn
 * câu chữ. Nhánh "chưa đăng nhập" ở lại làm <b>lưới an toàn cho một bản máy chủ
 * cũ hơn</b> — cùng khuôn với {@code InvitationFailure.NEEDS_ACCOUNT} — và câu
 * của nó vẫn đúng cho đúng ca ấy. Nó cũng là nhánh duy nhất còn nút "Đăng
 * nhập": bảo một người đang đăng nhập đi đăng nhập là một vòng tròn.</p>
 */
export function RegisterDone({
  registration,
  loginIdIsPhone,
  onLeaveForPasswordSetup,
}: RegisterDoneProps) {
  const t = useTranslations("auth.register");
  const { isAuthenticated, login } = useAuth();

  const setPasswordUrl = registration.setPasswordUrl;

  return (
    <div className="space-y-4">
      <section
        data-register-state="DONE"
        role="status"
        aria-labelledby="register-done-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.successBg, borderColor: colorVars.borderDark }}
      >
        <h1
          id="register-done-title"
          className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main"
        >
          <CheckCircleOutlined aria-hidden style={{ color: colorVars.successText }} />
          {t("doneTitle")}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("doneBody")}
        </p>

        {setPasswordUrl ? (
          <div className="mt-4">
            <h2 className="m-0 text-than font-semibold text-text-main">
              {t("donePasswordTitle")}
            </h2>
            <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-main">
              {t("donePasswordBody")}
            </p>
            {loginIdIsPhone && (
              <p
                data-register-recovery="PHONE"
                className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-main"
              >
                {t("donePasswordPhoneRecovery")}
              </p>
            )}
            <Button
              type="primary"
              icon={<KeyOutlined />}
              onClick={() => {
                const roiDi =
                  onLeaveForPasswordSetup ?? ((url: string) => window.location.assign(url));
                // Đi tới URL MÁY CHỦ TRẢ VỀ. Không ghép URL ở client: chỗ ấy mang
                // một token ký HMAC, và một URL tự ghép thì hoặc sai chữ ký,
                // hoặc thiếu token, hoặc cả hai.
                roiDi(setPasswordUrl);
              }}
              className="mt-3 min-h-[44px] text-than font-semibold"
            >
              {t("donePasswordCta")}
            </Button>
          </div>
        ) : (
          // Vắng `setPasswordUrl`. Hai ca, và `isAuthenticated` CHÍNH LÀ thứ
          // phân biệt chúng — xem javadoc "Vắng `setPasswordUrl`".
          <div
            className="mt-4"
            data-register-nolink={isAuthenticated ? "SIGNED_IN" : "HAS_PASSWORD"}
          >
            <h2 className="m-0 text-than font-semibold text-text-main">
              {t(isAuthenticated ? "doneAlreadySignedInTitle" : "doneHasPasswordTitle")}
            </h2>
            <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-main">
              {t(isAuthenticated ? "doneAlreadySignedInBody" : "doneHasPasswordBody")}
            </p>
            {!isAuthenticated && (
              <Button
                type="primary"
                icon={<LoginOutlined />}
                onClick={() => login()}
                className="mt-3 min-h-[44px] text-than font-semibold"
              >
                {t("doneSignInCta")}
              </Button>
            )}
          </div>
        )}
      </section>

      <section
        data-register-next="NHAN_DIEN"
        aria-labelledby="register-next-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.bgCard, borderColor: colorVars.border }}
      >
        <h2
          id="register-next-title"
          className="m-0 font-serif text-de font-semibold text-text-main"
        >
          {t("doneNextTitle")}
        </h2>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("doneNextBody")}
        </p>
        <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {t("doneNextNote")}
        </p>
        {/* Liên kết, không phải chuyển hướng — xem javadoc. `/nhan-dien` giữ
            nguyên tiếng Việt ở cả hai ngôn ngữ, cùng quy ước với `/moi` và
            `/dat-mat-khau`. */}
        <Link
          href="/nhan-dien"
          className="mt-4 inline-flex min-h-[44px] items-center rounded-lg border px-4 text-than font-semibold no-underline"
          style={{
            borderColor: colorVars.primary,
            color: colorVars.primary,
            background: colorVars.bgCard,
          }}
        >
          {t("doneNextCta")}
        </Link>
      </section>
    </div>
  );
}
