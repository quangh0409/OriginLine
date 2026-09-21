"use client";

import { useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Skeleton } from "antd";
import { claimFailureOf, claimsOf, quotaOf, type ClaimFailure } from "@/lib/api/claim";
import { ClaimBlockNotice } from "./claim-block-notice";
import { ClaimExistingForm } from "./claim-existing-form";
import { ClaimQuotaLine } from "./claim-quota-line";
import { ClaimStartPanel } from "./claim-start-panel";
import { ClaimTargetCard } from "./claim-target-card";
import { THAM_SO_NGUOI } from "./routes";
import { useClaimTarget, useMyClaims } from "./use-claims";

/**
 * Màn **"tôi là ai trong phả"** — `/nhan-dien` và `/nhan-dien?nguoi=<mã>`.
 *
 * <h2>Một đường dẫn, hai trạng thái, và đó là cố ý</h2>
 * Không có `?nguoi` thì đây là màn mở đầu: giải thích và mở hai lối đi. Có
 * `?nguoi` thì đây là màn quyết định cho <em>một</em> ô. Tách thành hai đường
 * dẫn sẽ đẹp hơn về mặt bộ định tuyến và tệ hơn về mặt người dùng: bấm Quay lại
 * từ biểu mẫu phải về <b>phả đồ</b> — nơi họ vừa chọn — chứ không về một trang
 * giới thiệu họ đã đọc xong.
 *
 * <h2>Bốn ca của checklist §1.4, và chúng chặn ở HAI thời điểm khác nhau</h2>
 * Ranh giới giữa hai thời điểm không phải chuyện kỹ thuật — nó <b>là</b> thiết
 * kế riêng tư của màn này:
 * <ol>
 *   <li><b>Ô của người đã khuất</b> → chặn <b>trước khi dựng biểu mẫu</b>, từ
 *       {@code isAlive} của {@code GET /persons/&#123;id&#125;}. Hồ sơ người đã
 *       khuất vốn công khai với cả khách nên đọc trường ấy không lộ thêm gì —
 *       đúng lập luận checklist §1.4 dùng để đòi "chặn cứng ngay lúc chọn".</li>
 *   <li><b>Đã gửi đơn rồi</b> · <b>hết lượt</b> → chặn trước, từ
 *       {@code /person-claims/mine}. Cả hai là chuyện của <em>chính tài
 *       khoản đang gọi</em>, nên nói rõ được mà không kể gì về ai khác.</li>
 *   <li><b>Ô đã có người nhận</b> → <b>chỉ lộ ra khi GỬI</b>, và câu trả lời cố
 *       ý chung chung. Không có lối hỏi trước, và <b>không được dựng một lối
 *       hỏi trước</b>: một lối như thế chính là công cụ dò xem ai đã vào hệ
 *       thống. Cái giá là người dùng ngay tình gõ xong rồi mới bị từ chối; máy
 *       chủ đã cân nhắc và chấp nhận cái giá ấy.</li>
 * </ol>
 * Không ca nào trong bốn ca ấy là sự cố, và không ca nào được vẽ bằng sắc đỏ.
 *
 * <h2>Hai truy vấn, không ba</h2>
 * {@code /person-claims/mine} và {@code /persons/&#123;id&#125;}. Cố ý <b>không</b> đọc
 * {@code /me} để đoán trước ca "tài khoản đã ghép": nó là một lượt gọi nữa cho
 * một ca mà bước gửi đã trả lời dứt khoát bằng {@code ACCOUNT_ALREADY_LINKED},
 * và nhân vật của màn này theo định nghĩa là người <em>chưa</em> ghép.
 */
export function ClaimScreen() {
  const t = useTranslations("claim");
  const searchParams = useSearchParams();
  const personId = searchParams.get(THAM_SO_NGUOI);

  const mine = useMyClaims();
  const target = useClaimTarget(personId);

  /**
   * Lời từ chối của máy chủ ở bước **gửi** — ca "ô ấy đã có tài khoản" và mọi
   * ca đổi trạng thái giữa chừng.
   *
   * Giữ ở đây, không giữ trong biểu mẫu: quyết định "ca nào trong bốn ca" phải
   * nằm ở một chỗ. Để biểu mẫu tự vẽ lời từ chối thì tấm thẻ "ô đã chọn" ở trên
   * vẫn mang nút "Chọn ô khác trên phả đồ" của nó, và khối từ chối ở dưới mọc
   * thêm một nút y hệt — hai nút giống nhau cạnh nhau, và người đọc phải đoán
   * chúng có khác nhau không.
   */
  const [blocked, setBlocked] = useState<ClaimFailure | null>(null);

  /**
   * Khung chung của nhánh "đã chọn một ô" — và nó tồn tại vì một lý do a11y
   * chứ không vì thẩm mỹ.
   *
   * Nhánh này có bốn trạng thái (đang tải · bị chặn · đã khuất · biểu mẫu), và
   * trước khi có khung này thì <b>ba trong bốn không có `h1` nào</b>: tấm thẻ ô
   * đã chọn là `h2`, khối từ chối là `h2`, khối biểu mẫu là `h2`. Một trang
   * không có `h1` làm hỏng chính thao tác mà người dùng trình đọc màn hình dùng
   * để định vị đầu tiên. Đặt tiêu đề trang ở một chỗ, dùng cho cả bốn.
   */
  const khung = (noiDung: ReactNode) => (
    <div className="space-y-4">
      <h1 className="m-0 font-serif text-de font-bold text-text-main">{t("pageTitle")}</h1>
      {noiDung}
    </div>
  );

  // ── Chưa chọn ai ────────────────────────────────────────────────────────
  // Nhánh này KHÔNG dùng `khung`: `ClaimStartPanel` mang `h1` riêng của nó, và
  // hai `h1` trên một trang thì tệ hơn không có cái nào.
  if (personId === null || personId.length === 0) {
    return <ClaimStartPanel claims={claimsOf(mine.data)} loading={mine.isPending} />;
  }

  if (mine.isPending || target.isPending) {
    return khung(
      <div data-claim-state="LOADING" aria-busy="true">
        <p className="m-0 mb-3 text-than text-text-muted">{t("loading")}</p>
        <Skeleton active paragraph={{ rows: 5 }} />
      </div>
    );
  }

  // Không đọc được danh sách đơn thì KHÔNG dựng biểu mẫu: trạng thái "đang có
  // đơn mở" nằm trong đúng phản hồi ấy, và dựng biểu mẫu mà không biết nó là
  // mời người dùng gõ xong một lá đơn chắc chắn bị từ chối.
  if (mine.isError) {
    return khung(
      <ClaimBlockNotice failure={claimFailureOf(mine.error)} onRetry={() => void mine.refetch()} />
    );
  }

  const claims = claimsOf(mine.data);
  const quota = quotaOf(mine.data);

  const chanTheoTaiKhoan: ClaimFailure | null = claims.some((c) => c.status === "PENDING")
    ? "CLAIM_ALREADY_PENDING"
    : // `quota` bắt buộc ở contract, nhưng `quotaOf` vẫn trả `null` khi TRUY VẤN
      // CHƯA XONG — và `null` phải đọc là "chưa biết", không phải "còn 0 lần".
      // Ngưỡng sống trong cấu hình máy chủ nên client tuyệt đối không đoán; đoán
      // sai ở đây là chặn nhầm một người vẫn còn lượt.
      quota !== null && quota.remaining <= 0
      ? "CLAIM_LIMIT_REACHED"
      : null;

  if (chanTheoTaiKhoan) {
    return khung(<ClaimBlockNotice failure={chanTheoTaiKhoan} />);
  }

  // Không tra ra ô (404 vì đã xoá mềm, hoặc vì người gọi không được xem), hoặc
  // hồ sơ về mà không có tên: cùng một câu trả lời chung chung. Phân biệt được
  // thì người dò đọc được mã nào từng tồn tại.
  if (target.isError || target.data === null || target.data.displayName.length === 0) {
    return khung(
      <ClaimBlockNotice
        failure={target.isError ? claimFailureOf(target.error) : "PERSON_UNAVAILABLE"}
        onRetry={() => void target.refetch()}
      />
    );
  }

  // "Đã khuất" và "máy chủ vừa từ chối lúc gửi" vẽ GIỐNG HỆT nhau: tấm thẻ ô đã
  // chọn (không kèm lối ra riêng) rồi tới lời từ chối mang lối ra của nó. Hai ca
  // phát hiện ở hai thời điểm khác nhau, nhưng với người đọc thì cùng một câu
  // chuyện — "ô này không phải chỗ của ông/bà, đây là lối đi tiếp".
  const chan: ClaimFailure | null = !target.data.isAlive ? "PERSON_DECEASED" : blocked;

  if (chan) {
    return khung(
      <>
        <ClaimTargetCard target={target.data} hideBackLink />
        <ClaimBlockNotice failure={chan} targetName={target.data.displayName} />
      </>
    );
  }

  return khung(
    <>
      <ClaimTargetCard target={target.data} />
      <ClaimQuotaLine quota={quota} showWhy />
      <ClaimExistingForm target={target.data} onBlocked={setBlocked} />
    </>
  );
}
