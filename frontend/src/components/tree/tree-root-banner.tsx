"use client";

import { Button } from "antd";
import { useTranslations } from "next-intl";
import type { TreeNode } from "@/types/api";

export interface TreeRootBannerProps {
  /** Node gốc đang mở. `undefined` khi dữ liệu chưa về — lúc ấy dải này không vẽ gì. */
  rootNode?: TreeNode;
  onChangeRoot?: () => void;
}

/**
 * **"Đang mở Chi Ất, từ cụ …"** — một dòng nói rõ phả đồ đang mở từ đâu.
 *
 * <h2>Vì sao cần</h2>
 * Máy chủ chọn gốc theo **vai + phạm vi chi** của người gọi khi `rootId` vắng mặt. Đó là hành vi
 * đúng, nhưng nó im lặng: một Trưởng chi mở phả đồ ra, thấy một cụ đời 2, và **không có gì trên
 * màn hình giải thích vì sao lại là cụ ấy**. Cùng một chỗ trống đó cũng là lý do người vừa đăng
 * nhập cảm thấy "gia phả co lại" — khách thấy cây từ Thuỷ tổ, thành viên thấy cây từ gốc ngành
 * mình, và không ai nói cho họ biết.
 *
 * Một dòng chữ là đủ. Nó không sửa hành vi chọn gốc (hành vi ấy đúng), nó chỉ **nói ra** hành vi
 * ấy — và đặt ngay cạnh lối đổi gốc, để câu trả lời "tôi muốn xem chi khác" nằm cùng chỗ với câu
 * hỏi.
 *
 * <h2>Không bịa</h2>
 * Chỉ in những gì `TreeNode.person` thật sự mang: tên, đời, chi. Thiếu chi thì câu rút ngắn lại
 * chứ không có chỗ giữ chỗ nào — một "Chi —" đọc ra như dữ liệu hỏng.
 */
export function TreeRootBanner({ rootNode, onChangeRoot }: TreeRootBannerProps) {
  const t = useTranslations("tree.rootBanner");
  if (!rootNode) return null;

  const { displayName, generation, primaryBranch } = rootNode.person;
  const branchName = primaryBranch?.name ?? null;

  return (
    <div
      data-testid="tree-root-banner"
      data-branch={branchName ?? undefined}
      // MỘT DÒNG, và dòng ấy không được cao lên.
      //
      // Đây là dải nằm giữa thanh công cụ và canvas, tức nó ăn thẳng vào chiều cao phả đồ. Đo được
      // trên Pixel 5: bản đầu tiên để chữ tự xuống dòng và đặt nút trên một hàng riêng — dải cao
      // 109px, và canvas còn đúng 130px. Một phả đồ cao 130px thì không thao tác được, và phép
      // kiểm "thu nhỏ rồi chạm vào giữa thẻ" hỏng theo một kiểu chẳng liên quan gì tới nội dung nó
      // kiểm: tâm canvas rơi trúng thẻ chú giải.
      className="flex items-center justify-between gap-x-3 border-b border-border bg-bg-page px-3 py-1"
    >
      {/* `min-w-0` là thứ làm `truncate` có hiệu lực. Mặc định của một con flex là
          `min-width: auto`, tức nó KHÔNG co xuống dưới bề ngang nội dung — `overflow: hidden` khi
          ấy chẳng cắt gì cả, hộp chỉ việc rộng ra và đẩy cả trang tràn ngang. Đây đúng là bất biến
          mà `tests/unit/a11y/layout-containment.test.ts` ghim, và là lỗi mà
          `e2e/mobile.spec.ts` bắt được ở "/tree cuộn ngang". */}
      <p className="m-0 min-w-0 truncate text-than text-text-main">
        {branchName
          ? t("withBranch", { branch: branchName, name: displayName })
          : t("withoutBranch", { name: displayName })}
        {generation != null && (
          <span className="text-text-muted"> · {t("generation", { n: generation })}</span>
        )}
      </p>
      {/* Lối đổi chi ẩn dưới `sm`: trên điện thoại thanh công cụ ĐÃ có nút "Chọn người khác làm
          gốc" làm đúng việc này, nên giữ cả hai chỉ là mua một hàng 44px bằng một bản sao. */}
      {onChangeRoot && (
        <Button
          type="link"
          onClick={onChangeRoot}
          className="!hidden !min-h-11 !shrink-0 !px-0 !text-than sm:!inline-flex"
        >
          {t("changeRoot")}
        </Button>
      )}
    </div>
  );
}
