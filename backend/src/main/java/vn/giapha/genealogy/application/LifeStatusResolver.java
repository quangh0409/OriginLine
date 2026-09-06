package vn.giapha.genealogy.application;

import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.shared.exception.DomainException;

/**
 * Quyết định <b>trạng thái sống/mất đích</b> mà một lệnh {@code PATCH} muốn đạt tới, trước khi
 * đụng vào aggregate.
 *
 * <h2>Phương án B — suy diễn (quyết định của Hội đồng Tộc biểu)</h2>
 * <b>Có ngày mất tức là đã mất.</b> Client gửi {@code {"death": …}} mà quên kèm
 * {@code "isAlive": false} thì backend tự đặt {@code isAlive = false} và ghi ngày mất, thay vì âm
 * thầm vứt ngày mất đi rồi trả {@code 200 OK} như bản cũ. Cú {@code PATCH} ấy là hành vi phổ biến
 * nhất của người nhập liệu, và mất dữ liệu im lặng là hỏng nặng hơn nhiều so với việc bắt họ tick
 * thêm một ô. Hộp thoại xác nhận ở giao diện ("bạn đang nhập thông tin người mất…") là lớp chặn
 * cho rủi ro gõ nhầm; backend không từ chối.
 *
 * <h2>Mâu thuẫn tường minh thì vẫn từ chối</h2>
 * {@code {"isAlive": true, "death": …}} là chuyện khác hẳn: client nói rõ <i>hai điều trái ngược
 * nhau</i> trong cùng một body, không phải quên tick. Không có cách suy diễn nào ở đây mà không
 * phải đoán bừa một nửa ý định, nên trả {@code VALIDATION_FAILED} để client tự làm rõ.
 *
 * <h2>Chiều ngược lại giữ nguyên</h2>
 * {@code {"isAlive": true}} trên người đã mất vẫn là <b>đính chính "thật ra còn sống"</b> và gỡ
 * ngày mất — đúng ràng buộc {@code ck_person_alive_vs_death} của V2.
 *
 * <h2>Đã mất mà không có ngày</h2>
 * {@code death} nằm trong {@code clearFields} (hoặc gửi {@code null}) trên người đã mất <b>không</b>
 * hồi sinh họ: {@code ck_person_alive_vs_death} chỉ cấm chiều "còn sống mà có ngày mất". Gia phả cổ
 * đầy trường hợp chỉ biết cụ đã mất chứ không còn ngày giỗ nào, nên đây là trạng thái hợp lệ và ý
 * định duy nhất đọc được là "xoá ngày mất ghi sai", không phải "cụ sống lại".
 */
final class LifeStatusResolver {

    private LifeStatusResolver() {
    }

    /**
     * Trạng thái sống/mất mà lệnh muốn đạt tới.
     *
     * @param alive    {@code isAlive} sau khi áp lệnh
     * @param death    ngày mất sau khi áp lệnh; luôn {@code null} khi {@code alive = true}
     * @param inferred {@code true} khi chuyển sang "đã mất" là do <b>backend suy ra từ ngày mất</b>
     *                 chứ không phải do client nói; ghi vào nhật ký để về sau đọc lại còn biết ai
     *                 quyết định điều đó
     */
    record Target(boolean alive, LifeDate death, boolean inferred) {
    }

    /**
     * @param aliveChange trường {@code isAlive} trong body
     * @param deathChange trường {@code death} trong body
     * @param currentAlive tình trạng hiện tại của nhân khẩu
     * @param currentDeath ngày mất hiện tại của nhân khẩu
     * @return đích cần đạt, hoặc {@code null} khi lệnh không nói gì về tình trạng sống/mất
     * @throws DomainException {@code VALIDATION_FAILED} khi body vừa khai còn sống vừa gửi ngày mất
     */
    static Target resolve(FieldChange<Boolean> aliveChange, FieldChange<LifeDate> deathChange,
                          boolean currentAlive, LifeDate currentDeath) {
        // `alive` gửi null được coi như không nói gì: "xoá trắng" một trường boolean không có nghĩa
        // nghiệp vụ nào - một người thì hoặc còn sống hoặc đã mất.
        boolean aliveGiven = aliveChange.present() && aliveChange.value() != null;
        boolean deathGiven = deathChange.present();
        if (!aliveGiven && !deathGiven) {
            return null;
        }

        LifeDate death = deathGiven ? deathChange.value() : currentDeath;

        if (aliveGiven && Boolean.TRUE.equals(aliveChange.value())) {
            if (deathGiven && deathChange.value() != null) {
                throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                        "Body vua khai isAlive=true vua gui ngay mat - hai dieu nay khong the cung "
                                + "dung. Bao mat thi bo isAlive (hoac gui isAlive=false); dinh chinh "
                                + "'that ra con song' thi dua 'death' vao clearFields.");
            }
            return new Target(true, null, false);
        }
        if (aliveGiven) {
            return new Target(false, death, false);
        }

        // `isAlive` vắng mặt - suy diễn từ ngày mất.
        if (death == null) {
            // Không có ngày mất nào để suy ra điều gì: giữ nguyên tình trạng, chỉ gỡ ngày mất cũ.
            return new Target(currentAlive, null, false);
        }
        return new Target(false, death, currentAlive);
    }
}
