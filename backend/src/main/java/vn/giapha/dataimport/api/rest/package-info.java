/**
 * Controller REST của đường ống nhập liệu, cộng hai lớp phụ trợ không rời khỏi gói này:
 * {@code ImportDtoMapper} (dịch domain → DTO) và {@code ImportIssueRedactor} (cắt dữ liệu người
 * đã có trong phả khỏi thông báo lỗi).
 *
 * <p>Không lớp nào ở đây chứa quy tắc nghiệp vụ. Bộ kiểm, phép đối soát {@code person_external_ref}
 * và máy trạng thái của lô đều ở {@code application}/{@code domain}.</p>
 */
package vn.giapha.dataimport.api.rest;
