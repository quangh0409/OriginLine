"use client";

import { UserAddOutlined, UserOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { claimRoutes } from "@/components/claim";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import type { TreeNode } from "@/types/api";

export interface TreeClaimActionsProps {
  /**
   * Người này **đã đăng nhập nhưng chưa gắn vào phả** hay chưa.
   *
   * Chỉ họ mới thấy hai nút này. Khách thì chưa có tài khoản để mà nhận ai; thành viên đã gắn thì
   * hai nút là nhiễu — và nhiễu trên màn hình chính là thứ người dùng học cách bỏ qua, kể cả khi
   * đến lượt nó có nghĩa.
   */
  readonly unlinked: boolean;
  /** Node đang được chọn trên canvas, nếu có. `undefined` = chưa chọn ai. */
  readonly selectedNode?: TreeNode;
}

/**
 * Cùng một sàn chạm với luồng "tôi là ai trong phả" (`components/claim/claim-chrome.tsx`):
 * 44px chiều cao, `px-4` cho bề ngang, chữ 16px. Chép hình dạng chứ **không** import —
 * `components/claim` chỉ mở ra đúng một bề mặt công khai là {@link claimRoutes}, và nới bề mặt ấy
 * ra để lấy một cái nút là đánh đổi sai chiều.
 */
const SAN_CHAM =
  "inline-flex min-h-[44px] items-center justify-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline";

/**
 * **Hai lối vào luồng "tôi là ai trong phả", đặt ngay cạnh ô tìm.**
 *
 * <h2>Vì sao cả hai phải ở đây, không phải ở một màn riêng</h2>
 * Bước "tự nhận mình" của luồng đăng ký chạy **trên chính màn phả đồ**: người mới mở phả đồ ra,
 * gõ tên mình vào ô tìm, rồi chỉ vào ô của mình. Nút *"Đây là tôi"* vì thế phải nằm đúng chỗ họ
 * vừa nhìn.
 *
 * Nút *"Tôi chưa có trong phả"* thì ngược lại: nó dành cho người **không tìm thấy mình** — con dâu
 * mới về, cháu mới sinh, nhánh ở xa đã lâu không ghi tiếp. Họ sẽ không nghĩ ra việc bấm vào một
 * node nào đó để tìm lối đi, nên nút ấy **luôn hiện** cạnh ô tìm, không chờ ai chọn gì. Không có
 * nó thì họ chọn bừa một người gần đúng — thường là bố mình — và Trưởng chi nhận một đơn vô nghĩa
 * (checklist §1.5).
 *
 * <h2>Người đã khuất: chặn ngay ở đây</h2>
 * Checklist §1.4 chốt "chặn cứng ngay lúc chọn". Chặn ở bước này không lộ thêm gì — người đã khuất
 * vốn là dữ liệu công khai và trạng thái ấy đã hiện sẵn trên chính tấm thẻ. Nên thay vì một nút
 * bấm vào rồi mới bị từ chối, ở đây không có nút, kèm đúng một câu nói vì sao.
 *
 * <h2>Địa chỉ đi qua `claimRoutes`, không ghép chuỗi</h2>
 * Đây là **ranh giới duy nhất** giữa vùng tệp này và `components/claim/**`. Chép tay
 * `"/nhan-dien?nguoi=" + id` ở cả hai bên là cách chắc chắn nhất để một bên đổi và bên kia im lặng
 * dẫn tới trang trắng. Bên kia cũng cố ý dùng **tham số truy vấn** chứ không phải đoạn đường dẫn:
 * `/nhan-dien/<id>` nằm cạnh hai tuyến tĩnh `chua-co` và `cho-duyet`, Next ưu tiên tuyến tĩnh nên
 * nó chạy đúng hôm nay và vỡ lặng lẽ vào ngày một mã nhân khẩu trùng một trong hai từ ấy.
 */
export function TreeClaimActions({ unlinked, selectedNode }: TreeClaimActionsProps) {
  const t = useTranslations("tree.claim");
  if (!unlinked) return null;

  const person = selectedNode?.person;
  const canClaimSelected = Boolean(person && person.isAlive);

  return (
    <div
      data-testid="tree-claim-actions"
      className="flex flex-wrap items-center gap-2 border-b border-border bg-bg-page px-3 py-2"
    >
      {canClaimSelected && person && (
        <Link
          href={claimRoutes.forPerson(person.id)}
          data-testid="tree-claim-this-is-me"
          className={SAN_CHAM}
          style={{
            background: colorVars.primary,
            color: colorVars.bgCard,
            borderColor: colorVars.primary,
          }}
        >
          <UserOutlined aria-hidden className="shrink-0" />
          {/* Nhãn mang TÊN người đang chọn: "Đây là tôi" một mình không nói rõ *ô nào*, và chọn
              nhầm ô là đúng cái lỗi mà cả luồng duyệt sinh ra để bắt. */}
          <span>{t("thisIsMe", { name: person.displayName })}</span>
        </Link>
      )}

      {selectedNode && !canClaimSelected && (
        <p className="m-0 text-than text-text-muted" data-testid="tree-claim-deceased-note">
          {t("deceasedCannotBeClaimed")}
        </p>
      )}

      <Link
        href={claimRoutes.newPerson}
        data-testid="tree-claim-not-in-tree"
        className={SAN_CHAM}
        style={{
          background: colorVars.bgCard,
          color: colorVars.primary,
          borderColor: colorVars.primary,
        }}
      >
        <UserAddOutlined aria-hidden className="shrink-0" />
        <span>{t("notInTree")}</span>
      </Link>
    </div>
  );
}
