"use client";

import { UserOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { claimRoutes } from "@/components/claim";
import { Link } from "@/i18n/navigation";
import { useMe } from "@/hooks/use-me";
import { usePerson } from "@/hooks/use-person";
import { colorVars } from "@/styles/tokens";

/**
 * Người đang xem **đã đăng nhập nhưng chưa gắn vào phả** hay chưa — đúng điều kiện
 * `<TreeClaimActions>` dùng trên canvas, tách ra để ngăn hồ sơ hỏi cùng một câu.
 */
export function useIsUnlinkedViewer(): boolean {
  const { data: me } = useMe();
  return Boolean(me?.appUserId) && !me?.personId;
}

/**
 * **Nút "Đây là tôi" đặt ở chân ngăn hồ sơ mở từ phả đồ.**
 *
 * <h2>Vì sao phải có nó ở đây — lỗi đã đo bằng trình duyệt thật (24/09)</h2>
 * Nút cùng tên trên dải dưới ô tìm (`<TreeClaimActions>`) chỉ hiện khi một node **đang được
 * chọn** — mà "chọn một node" trên canvas chính là **mở ngăn hồ sơ này**. Ngăn là một `Drawer`
 * có lớp phủ, nên đúng lúc nút ấy xuất hiện thì nó đã nằm dưới lớp phủ:
 *  - máy tính: bấm vào chỗ nút là bấm vào lớp phủ → ngăn đóng → hết chọn → nút biến mất;
 *  - điện thoại: ngăn trượt lên cao 82vh và che hẳn dải nút, không nhìn thấy mà bấm.
 * Tức bước "tự nhận mình" — việc chặn của cả luồng đăng ký — không đi tiếp được bằng chuột hay
 * bằng tay. Bộ kiểm component không bắt được, vì nó dựng `<TreeClaimActions>` một mình.
 *
 * Đặt nút ở **chân ngăn** thì nó nằm đúng chỗ mắt người dùng vừa đọc tên, ngày sinh, cha mẹ để tự
 * xác nhận "đúng là tôi", và luôn trong tầm ngón cái trên điện thoại. Dải trên canvas vẫn giữ
 * nguyên (nút "Tôi chưa có trong phả" ở đó phải luôn hiện), nên đây là lối **bổ sung** chứ không
 * thay thế.
 *
 * Người đã khuất: không có nút, chỉ một câu — cùng quyết định "chặn ngay lúc chọn" của checklist
 * §1.4, và cùng câu chữ với dải trên canvas (`tree.claim.deceasedCannotBeClaimed`).
 */
export function PersonClaimAction({ personId }: { personId: string }) {
  const t = useTranslations("tree.claim");
  const { data } = usePerson(personId);
  const person = data?.person;
  if (!person) return null;

  if (!person.isAlive) {
    return (
      <p className="m-0 text-than text-text-muted" data-testid="person-claim-deceased-note">
        {t("deceasedCannotBeClaimed")}
      </p>
    );
  }

  return (
    <Link
      href={claimRoutes.forPerson(person.id)}
      data-testid="person-claim-this-is-me"
      className="inline-flex min-h-[44px] w-full items-center justify-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
      style={{ background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }}
    >
      <UserOutlined aria-hidden className="shrink-0" />
      <span>{t("thisIsMe", { name: person.displayName })}</span>
    </Link>
  );
}
