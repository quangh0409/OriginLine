/**
 * Domain event của context {@code genealogy}.
 *
 * <p>Đây là <b>một trong hai</b> cách duy nhất context khác được biết chuyện gì xảy ra trong phả hệ
 * (cách còn lại là gọi application service public). Không context nào được chạm vào repository
 * của {@code genealogy}.</p>
 *
 * <p>Sự kiện cố ý chỉ mang <b>định danh</b>, không mang hồ sơ: người nhận tự truy vấn lại qua API
 * công khai và do đó tự động chịu bộ lọc phân tầng riêng tư. Nhét dữ liệu người sống vào payload
 * sự kiện là cách rò rỉ vòng qua mọi lớp kiểm soát.</p>
 */
@org.springframework.modulith.NamedInterface("events")
package vn.giapha.genealogy.domain.event;
