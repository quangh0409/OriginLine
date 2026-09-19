package vn.giapha.genealogy.application;

import java.util.UUID;

/**
 * Một va chạm <b>kỵ húy</b> đã tìm thấy, ở dạng dùng được <b>ngoài</b> context {@code genealogy}.
 *
 * <p>Bản đối chiếu ở tầng domain là {@code TabooConflict}. Kiểu ấy mang thêm {@code NameType} và
 * {@code TabooMatchKind} — hai enum của domain — nên nó không đi qua ranh giới context được. Kiểu
 * này chỉ giữ những gì bên ngoài thật sự dùng để dựng cảnh báo cho người đọc: <b>tên huý nào,
 * trùng với cụ nào, đời thứ mấy</b>.</p>
 *
 * <p><b>Cố ý không mang {@code matchKind}.</b> Mức độ trùng (nguyên văn / trùng tên chính / chỉ
 * trùng khi bỏ dấu) là chuyện nội bộ của phép dò, và chưa bên gọi nào cần tới. Thêm một trường
 * không ai đọc chính là kiểu nhân đôi khái niệm mà kiểu này sinh ra để tránh; khi nào có bên gọi
 * thật sự cần thì thêm — kèm một enum riêng của tầng application, không phải bằng cách lôi enum
 * domain ra ngoài.</p>
 *
 * @param ref                 mã tham chiếu của hồ sơ đầu vào, chép lại từ {@link ScreeningSubject#ref()}
 * @param tabooName           tên huý bị trùng
 * @param ancestorPersonId    nhân khẩu bậc trên mang tên huý ấy
 * @param ancestorDisplayName tên hiển thị của bậc trên
 * @param ancestorGeneration  đời thứ của bậc trên (Thuỷ tổ = 1); có thể {@code null}
 */
@org.springframework.modulith.NamedInterface("do-trung")
public record TabooHit(String ref,
                       String tabooName,
                       UUID ancestorPersonId,
                       String ancestorDisplayName,
                       Integer ancestorGeneration) {
}
