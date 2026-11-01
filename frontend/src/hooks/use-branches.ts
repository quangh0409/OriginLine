import { useQuery } from "@tanstack/react-query";
import { graphqlClient } from "@/lib/graphql/client";
import {
  BRANCHES_QUERY,
  type BranchOption,
  type BranchesQueryData,
  type BranchesVariables,
} from "@/lib/graphql/queries";
import { queryKeys } from "@/lib/query/keys";
import { useSignedIn } from "@/lib/auth/use-signed-in";

/**
 * The clan's chi/ngành list, used to populate the F6 branch filter (and any
 * later branch-scoped picker). Goes over GraphQL because there is no REST
 * `/branches` in the contract — see BRANCHES_QUERY for the rationale.
 *
 * Branches change about once a decade, so this is cached hard: refetching it
 * on every keystroke of a search box would be pure waste.
 *
 * <h2>Chỉ chạy khi ĐÃ đăng nhập (26/09/2026)</h2>
 * Resolver `branches` đòi token — đúng: cấu trúc chi/ngành không thuộc bề mặt
 * công khai. Nhưng bốn màn gọi hook này (`/search`, `/events`, `/vinh-danh`,
 * bộ chọn người) nay đều mở cho khách, và `/events`, `/vinh-danh` còn vừa mở
 * API cho khách hôm nay. Chặn ở từng nơi gọi là bốn chỗ để quên — `/search`
 * đã quên một lần: ô lọc chi được dựng trong nhịp `check-sso` chưa ngã ngũ
 * (lúc `restricted` còn `false`), nên khách vẫn nhận `401 POST /graphql`. Nên
 * chặn ở đây, một chỗ: chưa ngã ngũ hoặc khách ⇒ `enabled: false`, và mọi
 * nơi gọi đã sẵn chịu được `data === undefined` (ô lọc chi tự ẩn khi không có
 * chi nào).
 */
export function useBranches(rootPath?: string) {
  const signedIn = useSignedIn();
  return useQuery<BranchOption[]>({
    enabled: signedIn === true,
    queryKey: queryKeys.branches(rootPath),
    queryFn: async () => {
      const data = await graphqlClient.request<BranchesQueryData, BranchesVariables>(
        BRANCHES_QUERY,
        { rootPath }
      );
      // ltree path order == hierarchy order, so a plain string sort already
      // groups children under their parent without a tree walk.
      return [...data.branches].sort((a, b) => a.path.localeCompare(b.path));
    },
    staleTime: 10 * 60_000,
  });
}

/** Indentation depth of a branch in the chi/ngành hierarchy, from its ltree path. */
export function branchDepth(branch: BranchOption): number {
  return branch.path.split(".").length - 1;
}

/**
 * Tên **đầy đủ theo dòng** của một chi/ngành: `"Chi Bính › Ngành Út"`.
 *
 * <h2>Vì sao cần (đo trên hệ thật 24/09, NT-TV-18)</h2>
 * Dòng họ demo có bốn chi, mỗi chi ba ngành cùng tên — **bốn "Ngành Út"**. Ô chọn lọc theo `name`
 * trần thì (1) gõ "Bính" chỉ còn đúng "Chi Bính", ba ngành con biến mất; (2) chọn xong, ô hiện
 * "Ngành Út" mà không ai biết là Ngành Út của chi nào; (3) gõ "Ngành Út" ra bốn dòng y hệt nhau.
 * Thụt đầu dòng chỉ cứu được lúc danh sách đang mở và không lọc — đúng lúc người dùng ít cần nhất.
 *
 * Bỏ tầng gốc (cả họ): mọi chi đều thuộc nó, thêm vào chỉ làm dài nhãn. Tổ tiên nào không có mặt
 * trong danh sách (ví dụ bị `rootPath` cắt) thì bỏ qua thay vì in ra một đoạn đường dẫn ltree.
 */
export function branchQualifiedName(
  branch: BranchOption,
  all: readonly BranchOption[]
): string {
  const nameByPath = new Map(all.map((b) => [b.path, b.name]));
  const labels = branch.path.split(".");
  const parts: string[] = [];
  // i = 1: bỏ tầng gốc của dòng họ. Một chi (độ sâu 1) vẫn ra đúng tên của nó.
  for (let i = Math.min(1, labels.length - 1); i < labels.length; i++) {
    const name = nameByPath.get(labels.slice(0, i + 1).join("."));
    if (name) parts.push(name);
  }
  return parts.length > 0 ? parts.join(" › ") : branch.name;
}
