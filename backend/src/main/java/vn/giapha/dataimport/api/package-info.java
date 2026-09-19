/**
 * Tầng <b>api</b> của context {@code dataimport} — bề mặt REST của đường ống nhập liệu.
 *
 * <h2>Vì sao tầng này mỏng một cách cố ý</h2>
 * Không một quyết định nghiệp vụ nào được sinh ra ở đây. Bộ kiểm, phép đối soát
 * {@code person_external_ref} và máy trạng thái của lô đều nằm ở {@code application}/{@code domain};
 * controller chỉ dịch HTTP ↔ use case và <b>đếm hộ giao diện</b>.
 *
 * <h2>Ba bất biến mà tầng này phải giữ hộ giao diện</h2>
 * <ol>
 *   <li><b>Backend đếm, client không đếm lại.</b> {@code blockingCount} / {@code warningCount} /
 *       {@code undecidedDuplicateCount} đi kèm mỗi lô. Đếm ở hai nơi thì hai nơi sẽ lệch ngay khi
 *       danh sách lỗi được phân trang, và một con số lệch trên màn đối soát là con số phá vỡ lòng
 *       tin của Trưởng chi.</li>
 *   <li><b>Lỗi chặn và cảnh báo là hai nhóm, không phải hai mức.</b> Không có endpoint nào trả
 *       tổng gộp.</li>
 *   <li><b>Không một byte dữ liệu của người đã có trong phả được rò ra qua đây.</b> Xem
 *       {@code ImportDuplicatePairDto} — màn đối chiếu nghi trùng chỉ trưng dữ liệu do chính người
 *       nhập nộp lên.</li>
 * </ol>
 *
 * <h2>Khoản nợ kiến trúc đã biết — {@code api.support}</h2>
 * {@code ImportBranchDirectory} đọc bảng {@code branch} bằng SQL chỉ-đọc. Đúng ra nó thuộc
 * {@code infrastructure}; nó nằm ở đây vì đợt này chỉ gói {@code api} được mở. Chuyển đi là một
 * phép <i>di chuyển tệp</i>, không phải viết lại — không lớp nào ngoài
 * {@code api.support} biết tới nó.
 */
package vn.giapha.dataimport.api;
