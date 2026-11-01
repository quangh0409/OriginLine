package vn.giapha.clan.application;

/**
 * Lệnh ghi hồ sơ dòng họ — thân của {@code PUT /admin/clan-profile}, chưa kiểm.
 *
 * <p>Để kiểu thô (chuỗi, số nguyên có thể {@code null}) chứ không nhận thẳng value object của
 * domain: việc dựng value object là nơi luật được kiểm, và nó phải xảy ra trong service để mọi vi
 * phạm ra cùng một mã {@code VALIDATION_FAILED}.</p>
 *
 * @param ancestralHall   {@code null} = không có / xoá từ đường
 * @param gioTo           {@code null} = không có / xoá ngày giỗ tổ
 * @param expectedVersion phiên bản đọc từ {@code ETag}; bắt buộc khi hồ sơ đã có
 */
public record WriteClanProfileCommand(String name, String shortName, String rootBranchPath,
                                      String region, Hall ancestralHall, String ancestorName,
                                      String foundedNote, LunarDayMonth gioTo,
                                      Long expectedVersion) {

    /** Từ đường, thô. */
    public record Hall(String name, String address) {
    }

    /** Ngày/tháng âm lịch, thô — {@code null} là thiếu, và thiếu một trong hai là lỗi. */
    public record LunarDayMonth(Integer lunarDay, Integer lunarMonth) {
    }
}
