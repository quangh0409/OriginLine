/**
 * Bộ luật của bộ kiểm — mỗi lớp trả lời một nhóm câu hỏi, gom lỗi vào {@code ValidationContext}.
 *
 * <p>Hai nhóm mức nghiêm trọng <b>tách bạch hoàn toàn</b>: lỗi chặn thì không cho bấm duyệt, cảnh
 * báo thì cho. Không luật nào được tự sửa dữ liệu, và không luật nào được ném ngoại lệ khi dữ liệu
 * sai — dữ liệu sai là đầu ra bình thường của bộ kiểm.</p>
 */
package vn.giapha.dataimport.application.rule;
