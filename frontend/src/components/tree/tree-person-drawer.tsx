"use client";

import { Drawer, Grid } from "antd";
import { useTranslations } from "next-intl";
import { PersonProfileDrawer } from "@/components/person/person-profile-drawer";
import type { TreeAudience } from "@/lib/api/tree";
import { TreePublicPersonProfile } from "./tree-public-person-profile";

export interface TreePersonDrawerProps {
  personId: string | null;
  /**
   * Cùng `audience` mà canvas dùng để vẽ cây. `null` = phiên chưa ngã ngũ; lúc
   * ấy chưa có node nào để bấm nên `personId` cũng đang là `null`.
   */
  audience: TreeAudience | null;
  onClose: () => void;
}

/**
 * Hồ sơ mở ra từ một nút trên phả đồ — **rẽ theo người xem**, đúng như canvas.
 *
 * <h2>Khoảng hở nó vá</h2>
 * Khách xem được phả đồ công khai, nhưng bấm vào một cụ thì trước đây giao diện
 * gọi `GET /persons/{id}` — endpoint nằm sau `authenticated()` — nên nhận `401`
 * và dịch nó thành một dải đỏ "không tải được hồ sơ". Nửa "cổng thông tin dòng
 * họ" của sản phẩm vì thế dừng lại ở đúng cú bấm đầu tiên.
 *
 * Quyết định "ai đang xem" chỉ được đưa ra **một lần**, ở `useTreeAudience`, và
 * cả cây lẫn hồ sơ đều đọc từ đó. Cách còn lại — cứ gọi bản thành viên rồi bắt
 * `401` mà lùi — tốn một vòng mạng cho mọi khách và làm bẩn nhật ký máy chủ
 * bằng những `401` không phải sự cố.
 *
 * <h2>Vì sao hai component chứ không một</h2>
 * `/persons/{id}` và `/public/persons/{id}` là **hai bề mặt** với hai bộ DTO
 * khác nhau, không phải một endpoint với hai mức quyền (xem
 * `src/lib/api/public-portal.ts`). Bản công khai không có `contact`,
 * `occupation`, `privacy`, `version`, và không có endpoint quan hệ/huy hiệu đi
 * kèm. Giữ chúng tách nhau là cách chắc chắn nhất để một ngày nào đó một trường
 * Tầng 3 không đi nhầm đường ra tới người lạ.
 */
export function TreePersonDrawer({ personId, audience, onClose }: TreePersonDrawerProps) {
  const t = useTranslations("person");
  const screens = Grid.useBreakpoint();
  const isDesktop = Boolean(screens.sm);

  if (audience !== "public") {
    return <PersonProfileDrawer personId={personId} onClose={onClose} />;
  }

  return (
    <Drawer
      open={personId !== null}
      onClose={onClose}
      title={t("profileTitle")}
      placement={isDesktop ? "right" : "bottom"}
      width={isDesktop ? 460 : undefined}
      height={isDesktop ? undefined : "82vh"}
      destroyOnClose
      styles={{ body: { background: "var(--color-bg-page)", padding: 12 } }}
    >
      {personId && <TreePublicPersonProfile personId={personId} />}
    </Drawer>
  );
}
