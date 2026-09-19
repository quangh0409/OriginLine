/**
 * Tầng <b>domain</b> của context {@code dataimport}: POJO thuần mô tả một lô nhập liệu, các dòng
 * chờ, hai nhóm lỗi, và hai thuật toán không cần cơ sở dữ liệu (dò vòng lặp, gợi ý mã gần giống).
 *
 * <p><b>Quy tắc bất di bất dịch:</b> không {@code @Entity}, không {@code @Component}, không import
 * {@code org.springframework.*} hay {@code jakarta.persistence.*}. Entity JPA và adapter nằm ở
 * {@code vn.giapha.dataimport.infrastructure}.</p>
 */
package vn.giapha.dataimport.domain;
