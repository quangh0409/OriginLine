import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { InvitationScreen } from "@/components/auth/invitation-screen";

/**
 * Nhận lời mời — `/moi/<mã>` (và `/en/moi/<mã>`).
 *
 * <h2>Đường dẫn giữ nguyên tiếng Việt ở cả hai ngôn ngữ</h2>
 * Cùng lý do với `/danh-ba`: mã mời được dán vào tin nhắn Zalo hoặc in lên
 * phiếu giấy, và nó phải mở ra đúng một chỗ bất kể người gửi lẫn người nhận
 * đang để giao diện ở ngôn ngữ nào. `/moi/K7M2QD` là thứ in trên giấy — nó
 * không được đổi theo ngôn ngữ.
 *
 * <h2>Ba chỗ mã mời có thể rò ra, và cách chặn từng chỗ</h2>
 * Mã trong URL là bí mật duy nhất bảo vệ tên một người <b>đang sống</b>
 * (thiết kế 06 §5.3). Trang này không xoá được mã khỏi thanh địa chỉ, nhưng nó
 * chặn được ba đường mã đi tiếp:
 * <ol>
 *   <li><b>Tiêu đề trang.</b> {@code generateMetadata} <b>không</b> nhận
 *       {@code params.ma} vào tiêu đề. Tiêu đề đi vào lịch sử duyệt web, vào
 *       danh sách tab, vào ảnh chụp màn hình và vào thẻ xem trước — bốn nơi
 *       người dùng không nghĩ là mình đang chia sẻ bí mật.</li>
 *   <li><b>Referer.</b> {@code referrer: "no-referrer"} để mọi lượt rời trang
 *       — kể cả cú chuyển sang Keycloak để đặt mật khẩu — không mang URL chứa
 *       mã sang máy chủ bên kia và vào nhật ký của nó.</li>
 *   <li><b>Bọ tìm kiếm và thẻ xem trước.</b> {@code robots} chặn cả lập chỉ
 *       mục lẫn lưu bản sao; trang này không bao giờ là thứ để tìm thấy.</li>
 * </ol>
 * Còn một chỗ thứ tư nằm ngoài tệp này: sản phẩm hiện <b>không</b> nhúng công
 * cụ phân tích nào, nên không có đường nào để mã đi vào nhật ký bên thứ ba.
 * Nếu sau này thêm, đường dẫn `/moi/**` phải nằm trong danh sách loại trừ.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "auth.invitation" });

  return {
    // KHÔNG chèn mã mời vào đây. Xem javadoc ở trên.
    title: t("pageTitle"),
    description: t("pageDescription"),
    referrer: "no-referrer",
    robots: { index: false, follow: false, nocache: true },
  };
}

export default async function InvitationPage({
  params,
}: {
  params: Promise<{ locale: string; ma: string }>;
}) {
  const { locale, ma } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      {/* `hep`: màn này là một quyết định một cột. Bề rộng đọc hẹp hơn giữ mắt
          không phải quét ngang giữa tên người và hai nút — và nhóm người dùng
          chính của nó là người lớn tuổi mở lần đầu trên điện thoại. */}
      <KhungTrang beRong="hep">
        {/* Mã đi thẳng từ đoạn đường dẫn vào lớp API. Không chuẩn hoá ở đây:
            phiếu giấy in `K7M-2QD` còn tin nhắn mang `K7M2QD`, và luật chuẩn
            hoá phải nằm ở máy chủ để mọi client cùng theo một luật. */}
        <InvitationScreen code={ma} />
      </KhungTrang>
    </AppShell>
  );
}
