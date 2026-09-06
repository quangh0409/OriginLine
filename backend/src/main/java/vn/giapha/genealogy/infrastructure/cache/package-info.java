/**
 * Adapter cache Redis cho projection cây phả đồ.
 *
 * <p>Chỉ cache <b>khung xương</b> (id đỉnh + độ sâu). Hồ sơ trả về khác nhau theo người gọi vì phân
 * tầng riêng tư, nên cache thứ đã lọc là con đường ngắn nhất tới việc một vai nhận được bản cache
 * của vai khác — một sự cố rò rỉ dữ liệu không để lại dấu vết nào trong log.</p>
 */
package vn.giapha.genealogy.infrastructure.cache;
