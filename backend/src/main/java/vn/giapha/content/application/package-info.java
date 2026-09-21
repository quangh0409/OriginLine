/**
 * Tầng <b>application</b> của context {@code content}: use case service điều phối domain + port,
 * ranh giới {@code @Transactional}, view ra và command vào.
 *
 * <p>Chỉ được phụ thuộc xuống {@code domain} (trong context này) và sang <b>mặt tiền công khai</b>
 * của ba context khác: {@code membership.application} (vai trò × phạm vi {@code ltree}),
 * {@code genealogy} qua named interface {@code "loc-rieng-tu"} (tên người đã lọc + kết luận về
 * nhóm trường vinh danh), và {@code audit.application} (ghi vết). Không biết gì về HTTP, JPA hay
 * Cypher.</p>
 *
 * <p><b>Gói này KHÔNG mang {@code @NamedInterface}.</b> Chưa context nào cần gọi vào
 * {@code content}, và mở sẵn một mặt tiền "cho sau này" là cách bề mặt tiếp xúc lớn dần mà không
 * ai quyết định gì. Khi có nhu cầu thật thì gắn nhãn cho <b>từng kiểu</b> theo đúng khuôn
 * {@code genealogy} đã đặt, không gắn cho cả gói.</p>
 */
package vn.giapha.content.application;
