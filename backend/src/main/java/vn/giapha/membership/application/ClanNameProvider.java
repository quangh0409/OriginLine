package vn.giapha.membership.application;

import java.util.Optional;

/**
 * Cổng SPI: <b>tên hiển thị của dòng họ</b> ứng với một nhãn gốc {@code ltree}.
 *
 * <h2>Vì sao cổng nằm ở đây và được hiện thực ở context {@code clan}</h2>
 * Thư mời ("Bạn được mời vào phả họ …") và màn tra mã mời dòng họ cần tên dòng họ. Trước V23 tên
 * ấy được suy từ tên chi gốc {@code DONG_HO} — tức là từ dữ liệu mẫu. Nguồn chân lý nay là hồ sơ
 * dòng họ ({@code clan_profile}), thuộc context {@code clan}. Nhưng {@code clan} <b>đã</b> phụ
 * thuộc {@code membership} (tạo tài khoản Hội đồng, đếm mã mời), nên {@code membership} gọi thẳng
 * sang {@code clan} là một chu trình và {@code ModularityTests} đỏ. Cùng lối thoát đã dùng cho
 * {@code ClaimScreeningPort}: <b>đảo phụ thuộc</b> — khai cổng ở gói named interface này, để
 * {@code clan.infrastructure.membership} hiện thực.
 *
 * <h2>Nhận nhãn gốc, không phải "tên của bản cài"</h2>
 * Bước 2 (nhiều dòng họ trên một bản cài) sẽ tra hồ sơ theo đúng nhãn gốc của nhân khẩu được mời;
 * hôm nay chỉ có một hồ sơ, và nó trả rỗng khi nhãn không khớp. Bên gọi luôn có đường lùi — tên chi
 * gốc — nên một bản cài chưa khởi tạo vẫn nói được một cái tên.
 *
 * <p>Bean có thể <b>vắng</b> (lát cắt test chỉ dựng {@code membership}): bên gọi tiêm qua
 * {@code ObjectProvider} và lùi về tên chi gốc.</p>
 */
public interface ClanNameProvider {

    /**
     * @param rootLabel nhãn {@code ltree} cấp 1, ví dụ {@code ho_nguyen_dinh}
     * @return tên dòng họ trong hồ sơ, hoặc rỗng khi chưa có hồ sơ cho nhãn ấy
     */
    Optional<String> clanNameOfRoot(String rootLabel);
}
