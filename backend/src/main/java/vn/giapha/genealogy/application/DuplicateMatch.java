package vn.giapha.genealogy.application;

import java.util.List;
import java.util.UUID;

/**
 * Một nhân khẩu <b>đã có trong gia phả</b> bị nghi là cùng một người với hồ sơ đang định nhập.
 *
 * <p><b>Máy nghi ngờ, người quyết định.</b> Bản ghi này không bao giờ dẫn tới việc tự gộp hai hồ
 * sơ, cũng không bao giờ khoá vĩnh viễn việc thêm người: nó chỉ là dữ liệu để dựng một hộp thoại
 * đủ chi tiết cho người nhập liệu tự đối chiếu. Gộp nhầm hai người trong gia phả là loại lỗi rất
 * khó gỡ — cả hai nhánh con cháu đều đã treo vào node sai.</p>
 *
 * <h2>Bản ghi này KHÔNG được tuần tự hoá nguyên vẹn ra khỏi tiến trình</h2>
 * Bốn thành phần {@link #displayName()}, {@link #generation()}, {@link #branchId()} và
 * {@link #matchedName()} là <b>giá trị đọc từ phả</b>. Bộ dò quét toàn dòng họ và không biết người
 * gọi là ai, nên bên bị nghi hoàn toàn có thể là một người <b>còn sống ở một chi khác</b> mà người
 * đang thêm nhân khẩu không được xem tên huý, năm sinh hay nguyên quán. Ranh giới đã chốt cho mọi
 * kênh đi ra ngoài (thân lỗi 409, {@code import_issue.message}, log):
 * <b>được phép nói trường nào của chính người gọi vừa nhập đã khớp, không bao giờ nói một giá trị
 * đọc từ phả</b> — nên các kênh ấy chỉ lấy {@link #personId()} / {@link #ref()} /
 * {@link #score()} / {@link #signals()} / {@link #hint()}, rồi để giao diện cầm khoá gọi
 * {@code GET /api/v1/persons/&#123;id&#125;}, nơi bộ lọc phân tầng riêng tư thật sự chạy.
 *
 * <p>Bốn thành phần kia vẫn tồn tại vì chúng <b>hợp lệ trong tiến trình</b>: đường nhập liệu hàng
 * loạt dùng chúng cho bên bị nghi là <i>một dòng khác trong chính tệp của người nhập</i>
 * ({@code personId == null}) — dữ liệu người ấy vừa gõ, giấu đi chỉ làm họ không đối chiếu
 * được.</p>
 *
 * @param personId nhân khẩu đã có; {@code null} khi bên bị nghi là một dòng khác trong cùng lô
 *        nhập liệu chưa được ghi (lúc đó xem {@link #ref()})
 * @param ref mã tham chiếu của dòng trong lô nhập liệu, để bên gọi chỉ ra đúng dòng nào
 * @param displayName tên hiển thị của bên bị nghi — <b>đọc từ phả</b> khi {@code personId != null}
 * @param generation đời thứ của bên bị nghi — <b>đọc từ phả</b> khi {@code personId != null}
 * @param branchId chi/ngành của bên bị nghi — <b>đọc từ phả</b> khi {@code personId != null}
 * @param score điểm nghi ngờ 0–100+, đã cộng trừ mọi tín hiệu
 * @param signals các tín hiệu đã đóng góp, giữ nguyên thứ tự để hiển thị
 * @param matchedName tên của bên bị nghi đã khớp — <b>đọc từ phả</b>, và khi khớp ở mức bỏ dấu thì
 *        đây là bản <b>có dấu</b> mà người gọi chưa từng biết; phép khớp chạy chéo mọi lớp tên nên
 *        nó có thể là tên huý
 * @param hint câu giải thích ngắn cho người dùng — <b>chỉ để hiển thị</b>, đừng phân tích chuỗi này
 */
@org.springframework.modulith.NamedInterface("do-trung")
public record DuplicateMatch(UUID personId,
                             String ref,
                             String displayName,
                             Integer generation,
                             UUID branchId,
                             int score,
                             List<DuplicateSignal> signals,
                             String matchedName,
                             String hint) {

    public DuplicateMatch {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    /** Gắn mã tham chiếu của dòng trong lô nhập liệu — dùng khi bên bị nghi chưa được ghi. */
    public DuplicateMatch withRef(String value) {
        return new DuplicateMatch(personId, value, displayName, generation, branchId, score, signals,
                matchedName, hint);
    }

    /** {@code true} khi bên bị nghi cũng là một dòng chưa ghi trong cùng lô nhập liệu. */
    public boolean trongCungLo() {
        return personId == null;
    }
}
