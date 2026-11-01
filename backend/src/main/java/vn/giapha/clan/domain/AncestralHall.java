package vn.giapha.clan.domain;

/**
 * Từ đường của dòng họ.
 *
 * <p>Địa chỉ đi kèm tên, không đứng một mình: một địa chỉ không có tên từ đường là dữ liệu mồ côi
 * mà giao diện không có chỗ hiện (ràng buộc {@code ck_clan_profile_hall_shape} ở V23 giữ lần hai).
 * Đây là dữ liệu <b>công khai</b> — từ đường là nơi thờ tự chung, không phải địa chỉ nhà riêng của
 * một người đang sống.</p>
 *
 * @param name    tên từ đường, bắt buộc, tối đa 200 ký tự
 * @param address địa chỉ, tuỳ chọn, tối đa 500 ký tự
 */
public record AncestralHall(String name, String address) {

    public AncestralHall {
        name = Texts.required(name, 200, "ancestralHall.name");
        address = Texts.optional(address, 500, "ancestralHall.address");
    }
}
