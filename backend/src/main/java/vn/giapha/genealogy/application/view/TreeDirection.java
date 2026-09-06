package vn.giapha.genealogy.application.view;

/**
 * Chiều duyệt phả đồ.
 *
 * <p>Nhắc lại chiều cạnh cho người viết truy vấn mới: {@code (cha)-[:PARENT]->(con)}. Đi xuống
 * con cháu là xuôi mũi tên, đi lên tổ tiên là <b>ngược</b> mũi tên. Nhầm chiều thì truy vấn trả
 * 0 dòng mà không hề văng lỗi.</p>
 */
public enum TreeDirection {

    /** Con cháu — mặc định. */
    DESCENDANTS,

    /** Tổ tiên; các node nhận {@code depth} <b>âm</b>. */
    ANCESTORS,

    /** Cả hai chiều, mỗi chiều {@code depth} đời. */
    BOTH
}
