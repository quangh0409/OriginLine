package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Tiến độ ghi vào phả — <b>nguồn duy nhất</b> của trạng thái cuối sau khi bấm duyệt.
 *
 * <h2>{@code estimated} luôn đúng, và phải hiện ra thành chữ</h2>
 * Con số {@link #processedRows()} đếm ngoài giao dịch ghi, nên nó là <b>ước lượng theo bản chất</b>
 * chứ không phải vì hiện thực còn dở. Vẽ nó như một thanh tiến độ chính xác là hứa một điều hệ
 * thống không giữ được — và khi nó nhảy từ 60% lên xong, người dùng kết luận hệ thống nói dối.
 *
 * <h2>Vì sao {@code POST /commit} không trả trạng thái cuối</h2>
 * Ghi 400 người là vài giây tới vài chục giây; giữ một request HTTP mở suốt thời gian ấy là mời
 * gọi timeout của proxy và một lần bấm lại của người dùng. Vì vậy {@code commit} trả {@code 202} và
 * trạng thái cuối <b>chỉ</b> đến từ đây.
 *
 * @param phase {@code QUEUED} · {@code PERSONS} · {@code PARENT_EDGES} · {@code SPOUSE_EDGES} ·
 *        {@code CACHE} · {@code DONE} · {@code FAILED}
 * @param failureMessage câu lỗi khi {@code phase = FAILED}. Lô đã cuộn lại <b>toàn bộ</b> —
 *        tất-cả-hoặc-không; không có trạng thái "ghi được một nửa"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportCommitProgressDto(UUID batchId,
                                      String status,
                                      String phase,
                                      int processedRows,
                                      int totalRows,
                                      boolean estimated,
                                      String failureMessage) {
}
