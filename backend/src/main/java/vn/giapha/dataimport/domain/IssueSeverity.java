package vn.giapha.dataimport.domain;

/**
 * Hai nhóm vấn đề, <b>tách bạch hoàn toàn</b>, khác nhau ở đúng một điểm: lỗi chặn thì không cho
 * bấm duyệt, cảnh báo thì cho — sau khi người nhập tick "tôi đã xem".
 *
 * <h2>Vì sao không được gộp chung</h2>
 * Đây là quyết định về <b>con người</b>, không phải về kỹ thuật. Một danh sách "15 vấn đề" nghe
 * như hỏng cả tệp và người nhập sẽ bấm bừa cho xong; "4 lỗi phải sửa, 11 điều nên xem" nghe là
 * việc làm được. Cùng một dữ liệu, hai kết cục khác hẳn nhau. Vì vậy đừng bao giờ thêm nhóm thứ
 * ba, và đừng bao giờ hiển thị tổng số gộp.
 *
 * <h2>Và máy không tự sửa con số của người</h2>
 * Không một cảnh báo nào ở đây được phép âm thầm sửa dữ liệu người nhập đã gõ.
 */
public enum IssueSeverity {

    /** Chặn bấm duyệt. Phải sửa trong tệp rồi tải lại. */
    BLOCKING,

    /** Không chặn. Người nhập xem, quyết định, rồi đi tiếp. */
    WARNING
}
