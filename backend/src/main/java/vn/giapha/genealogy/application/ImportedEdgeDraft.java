package vn.giapha.genealogy.application;

import java.util.UUID;

/**
 * Một <b>cạnh quan hệ</b> sắp được ghi vào phả từ đường nhập liệu hàng loạt.
 *
 * <p>Hướng là {@code tu} → {@code den} và nghĩa phụ thuộc loại: với cạnh cha/mẹ thì {@code tu} là
 * cha/mẹ; với {@code HEIR} thì {@code tu} là người <b>để lại</b> hương hoả và {@code den} là người
 * <b>nối dõi</b>. Vẽ ngược một cạnh cha–con là lật ngược cả một nhánh, nên chiều được nói ra ở
 * đây bằng chữ chứ không để bên gọi tự nhớ.</p>
 *
 * @param loai một trong {@code PARENT_BIO}, {@code PARENT_ADOPT}, {@code SPOUSE}, {@code HEIR} —
 *        nhận bằng chuỗi để không phải đẩy enum {@code RelType} của domain qua ranh giới context
 * @param bacHonPhoi vợ cả là 1, vợ hai là 2. <b>Chỉ</b> có nghĩa với {@code SPOUSE}; thiếu nó thì
 *        {@code ux_relationship_spouse_order} không có dữ liệu và danh xưng con của các bà tính sai
 * @param loaiKeTu một trong {@code DICH_TON}, {@code THUA_TU}, {@code KE_TU} — bắt buộc với
 *        {@code HEIR}
 */
@org.springframework.modulith.NamedInterface("ghi-pha")
public record ImportedEdgeDraft(UUID tu,
                                UUID den,
                                String loai,
                                Integer bacHonPhoi,
                                String loaiKeTu,
                                String ghiChu) {

    public static ImportedEdgeDraft cha(UUID chaId, UUID conId, boolean nuoi, String ghiChu) {
        return new ImportedEdgeDraft(chaId, conId, nuoi ? "PARENT_ADOPT" : "PARENT_BIO", null, null,
                ghiChu);
    }

    public static ImportedEdgeDraft honPhoi(UUID chongId, UUID voId, Integer bac, String ghiChu) {
        return new ImportedEdgeDraft(chongId, voId, "SPOUSE", bac, null, ghiChu);
    }

    /** Kế tự: {@code deLaiId} là cụ tuyệt tự, {@code noiDoiId} là người được lập để nối dõi. */
    public static ImportedEdgeDraft keTu(UUID deLaiId, UUID noiDoiId, String loaiKeTu, String ghiChu) {
        return new ImportedEdgeDraft(deLaiId, noiDoiId, "HEIR", null, loaiKeTu, ghiChu);
    }
}
