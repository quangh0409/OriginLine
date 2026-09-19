/**
 * DTO của tầng REST cho đường ống nhập liệu.
 *
 * <p>Tách khỏi record của {@code domain} một cách có chủ ý: {@code ImportBatch} và
 * {@code PersonRow} là mô hình <b>trong</b> hệ thống, còn các record ở đây là <b>hợp đồng</b> với
 * giao diện — đổi một cái không được kéo theo cái kia. Mọi trường đều đặt tên theo lối
 * {@code camelCase} tiếng Anh vì đó là quy ước của {@code contracts/openapi.yaml}; riêng nội dung
 * ({@code message}, {@code context}) giữ nguyên tiếng Việt và <b>giữ nguyên tên khoá tiếng Việt</b>
 * của {@code import_issue.context} — xem {@code ImportIssueDto}.</p>
 */
package vn.giapha.dataimport.api.rest.dto;
