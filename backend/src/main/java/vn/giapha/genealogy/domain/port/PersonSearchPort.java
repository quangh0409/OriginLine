package vn.giapha.genealogy.domain.port;

import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.domain.NameType;

/**
 * Tìm kiếm nhân khẩu theo tên, <b>có dấu hoặc không dấu</b> (FR-4.4).
 *
 * <p>Công cụ là full-text search của PostgreSQL cộng {@code unaccent} và {@code pg_trgm} —
 * <b>không</b> phải Elasticsearch, và <b>không</b> phải Redis. Tìm trên mọi lớp tên (húy / tự /
 * hiệu / thụy / thường gọi / pháp danh), không chỉ tên chính.</p>
 *
 * <p>Cổng này trả <b>id thô, chưa lọc riêng tư</b>; việc lọc do tầng application làm, vì chỉ ở đó
 * mới biết người gọi là ai.</p>
 */
public interface PersonSearchPort {

    /**
     * @param query chuỗi tìm kiếm do người dùng gõ
     * @param filter bộ lọc phụ (đời, chi, nguyên quán, sống/mất, kèm bản ghi đã xoá)
     * @param limit số dòng tối đa cần lấy từ CSDL — gọi rộng hơn trang cần hiển thị vì bộ lọc
     *        riêng tư ở tầng trên sẽ bỏ bớt kết quả
     */
    List<Hit> search(String query, SearchFilter filter, int limit);

    /** Một dòng kết quả thô: ai khớp, khớp ở lớp tên nào, và điểm tương đồng. */
    record Hit(UUID personId, NameType matchedNameType, double score) {
    }

    /**
     * @param generation lọc theo đời thứ
     * @param branchPathPrefix lọc trong một chi và toàn bộ nhánh con ({@code ltree <@})
     * @param nativePlace lọc theo nguyên quán, so không dấu
     * @param alive lọc theo tình trạng sống/mất
     * @param includeDeleted kèm bản ghi đã xoá mềm (chỉ Admin/Hội đồng)
     */
    record SearchFilter(Integer generation, String branchPathPrefix, String nativePlace,
                        Boolean alive, boolean includeDeleted) {

        public static SearchFilter none() {
            return new SearchFilter(null, null, null, null, false);
        }
    }
}
