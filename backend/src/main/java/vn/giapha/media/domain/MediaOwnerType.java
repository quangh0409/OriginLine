package vn.giapha.media.domain;

/**
 * Loại chủ sở hữu của một tệp. Khoá đa hình — {@code media_link} cố ý <b>không</b> có khoá ngoại
 * sang {@code post}/{@code person}, xem khối ghi chú 19.2 của {@code V19}.
 *
 * <p>Mỗi giá trị ở đây ứng với đúng một hiện thực của {@code MediaOwnerAccessPort}: chính module
 * sở hữu bản ghi là module trả lời câu "người gọi này có được xem nó không". {@code media} không
 * tự trả lời — nó không biết {@code ltree}, không biết nhóm trường riêng tư, và nếu nó biết thì
 * luật ấy đã có bản sao thứ hai.</p>
 */
public enum MediaOwnerType {

    /** Tệp đính kèm một bài viết. Quyền xem = quyền xem BÀI (quyết định đã chốt số 1). */
    POST,

    /** Ảnh chân dung một nhân khẩu. Quyền xem = nhóm trường {@code birthDetailAndPhoto} (số 3). */
    PERSON_AVATAR
}
