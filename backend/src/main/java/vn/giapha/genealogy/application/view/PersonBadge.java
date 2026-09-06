package vn.giapha.genealogy.application.view;

/**
 * Nhãn nghiệp vụ cho canvas phả đồ, <b>tính sẵn ở backend</b>.
 *
 * <p>Tính sẵn vì mấy nhãn này đòi hỏi hiểu quan hệ: "dâu" là vợ của một người con trai trong
 * dòng họ, không phải một loại cạnh. Bắt giao diện tự suy ra là bảo nó cài lại một phần rule
 * engine danh xưng bằng JavaScript — sớm muộn cũng lệch với backend.</p>
 */
public enum PersonBadge {

    /** Đích tôn — con trai trưởng của con trai trưởng, chịu trách nhiệm thờ tự chính. */
    DICH_TON,

    /** Thừa tự — người được chỉ định tiếp nối việc thờ cúng. */
    THUA_TU,

    /** Kế tự — người được lập để nối dõi một chi tuyệt tự. */
    KE_TU,

    /** Con nuôi — đến từ cạnh {@code PARENT_ADOPT}, không phải một cờ trên cạnh ruột. */
    CON_NUOI,

    /** Con dâu — suy từ {@code SPOUSE} cộng huyết thống. */
    DAU,

    /** Con rể — suy từ {@code SPOUSE} cộng huyết thống. */
    RE,

    /** Tuyệt tự — chi không có người nối dõi. */
    TUYET_TU,

    /** Đang giữ chức <b>Trưởng chi</b> — chức danh dòng tộc, không phải vai kỹ thuật. */
    TRUONG_CHI,

    /** Đã khuất — canvas phân biệt thị giác với người còn sống. */
    DECEASED
}
