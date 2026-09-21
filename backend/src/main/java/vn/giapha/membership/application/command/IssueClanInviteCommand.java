package vn.giapha.membership.application.command;

/**
 * Phát một mã mời dòng họ.
 *
 * @param label   nhãn để Hội đồng nhận ra mình phát mã nào cho kênh nào ("Nhóm Zalo họ Nguyễn
 *                2026"). Không bắt buộc, nhưng nó là thứ biến bộ đếm thành thông tin dùng được:
 *                "mã dán nhóm Zalo đã dùng 400 lần" nói được điều gì đó, "mã thứ ba đã dùng 400
 *                lần" thì không
 * @param ttlDays hạn hiệu lực tính bằng ngày; {@code null} = mặc định cấu hình. <b>Không có giá
 *                trị nào nghĩa là vô hạn</b> — chốt 1 của design 07 §1.2
 * @param maxUses trần lượt dùng; {@code null} = không đặt trần (vẫn có hạn thời gian, vẫn thu hồi
 *                được, vẫn có bộ đếm)
 * @param note    ghi chú nội bộ
 */
public record IssueClanInviteCommand(String label, Integer ttlDays, Integer maxUses, String note) {

    public IssueClanInviteCommand(String label) {
        this(label, null, null, null);
    }
}
