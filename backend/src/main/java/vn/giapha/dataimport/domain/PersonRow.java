package vn.giapha.dataimport.domain;

import java.util.Map;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Một dòng trang <b>Nhân khẩu</b> đang nằm ở khu vực chờ — đã hiểu thành các trường, chưa được ghi
 * vào phả.
 *
 * <h2>Đây không phải {@code Person}</h2>
 * Cố ý không dùng lại aggregate của {@code genealogy}. Một dòng chờ là <b>thứ người nhập gõ</b>,
 * kể cả khi nó mâu thuẫn, thiếu, hoặc sai — đó là toàn bộ lý do khu vực chờ tồn tại. Ép nó vào một
 * aggregate có bất biến thì hoặc ta phải nới bất biến của aggregate (hỏng cho cả hệ thống), hoặc
 * phải từ chối dòng ngay lúc đọc (mất khả năng báo cho người nhập biết cả tệp sai những gì).
 *
 * @param externalCode cột {@code Mã}, đã nâng hoa và bỏ khoảng trắng — <b>khoá bất biến</b> của
 *        toàn bộ đường ống. Tải lại tệp đã sửa không sinh người trùng là nhờ cột này.
 * @param death ngày giỗ như người nhập gõ; {@code null} khi trống, và với người đã mất thì đó là
 *        {@link IssueCode#IMP_MISSING_GIO}
 * @param resolvedPersonId nhân khẩu đã có mang mã này, tra từ {@code person_external_ref};
 *        {@code null} nghĩa là sẽ tạo mới
 */
public record PersonRow(int rowNo,
                        String externalCode,
                        Map<String, String> raw,
                        Map<String, String> normalized,
                        String fullName,
                        String tabooName,
                        String posthumousName,
                        String hanNomName,
                        Gender gender,
                        Integer generation,
                        String fatherCode,
                        String motherCode,
                        CellCodec.ParentRel parentRel,
                        Boolean alive,
                        Integer birthYear,
                        LunarDeathDate death,
                        String nativePlace,
                        String nativePlaceCode,
                        String heirOfCode,
                        CellCodec.HeirType heirType,
                        UUID resolvedPersonId,
                        PlannedAction plannedAction) {

    public PersonRow {
        raw = raw == null ? Map.of() : Map.copyOf(raw);
        normalized = normalized == null ? Map.of() : Map.copyOf(normalized);
        parentRel = parentRel == null ? CellCodec.ParentRel.BIO : parentRel;
        plannedAction = plannedAction == null ? PlannedAction.CREATE : plannedAction;
    }

    /** Gắn kết quả đối soát với {@code person_external_ref} — bước quyết định CREATE hay UPDATE. */
    public PersonRow withResolution(UUID personId, PlannedAction action) {
        return new PersonRow(rowNo, externalCode, raw, normalized, fullName, tabooName,
                posthumousName, hanNomName, gender, generation, fatherCode, motherCode, parentRel,
                alive, birthYear, death, nativePlace, nativePlaceCode, heirOfCode, heirType,
                personId, action);
    }

    /**
     * Trỏ lại các mã tham chiếu sau khi một dòng khác bị bỏ đi vì được gộp.
     *
     * <p><b>Đây là chỗ dễ sinh người mồ côi nhất trong cả đường ống.</b> Bỏ một dòng mà quên trỏ
     * lại mã cha/mã mẹ/mã kế tự của những dòng đang trỏ tới nó thì con cháu của người ấy mất cha
     * trong phả đồ — và mất một cách im lặng, vì không ràng buộc nào của CSDL biết rằng đáng lẽ
     * phải có một cạnh ở đó.</p>
     */
    public PersonRow withThamChieu(String cha, String me, String keTuCho) {
        return new PersonRow(rowNo, externalCode, raw, normalized, fullName, tabooName,
                posthumousName, hanNomName, gender, generation, cha, me, parentRel,
                alive, birthYear, death, nativePlace, nativePlaceCode, keTuCho, heirType,
                resolvedPersonId, plannedAction);
    }

    /**
     * Hút những ô <b>còn trống</b> của dòng này từ một dòng bị bỏ vì được gộp vào đây.
     *
     * <h2>Chỉ lấp chỗ trống, không bao giờ ghi đè</h2>
     * Dòng ở lại là dòng người đối chiếu chọn giữ; ghi đè ô của nó bằng ô của dòng bị bỏ là lặng lẽ
     * đảo ngược quyết định ấy. Nhưng <b>bỏ</b> những ô mà chỉ dòng kia có thì cũng sai không kém:
     * hai dòng cùng tả một người thường bổ khuyết cho nhau (trang đời cha ghi tên huý, trang đời
     * con ghi năm sinh), và gộp mà mất một nửa dữ kiện thì người ta sẽ thôi dùng nút gộp.
     *
     * <p>Riêng {@code fatherCode} / {@code motherCode} / {@code heirOfCode} quan trọng hơn hẳn các ô
     * khác: nếu dòng bị bỏ là dòng mang mã cha còn dòng ở lại để trống, mà ta không hút sang, thì
     * chính người vừa được gộp trở thành <b>người mồ côi</b> trong phả đồ. Đó là ca tệ nhất của cả
     * thao tác gộp, nên nó được nêu tên ở đây thay vì lẫn vào danh sách chung.</p>
     *
     * <p>{@code parentRel} và {@code heirType} đi <b>kèm</b> mã tương ứng: nhận một mã cha mà bỏ
     * lại nhãn "con nuôi" của nó là biến một người con nuôi thành con ruột.</p>
     */
    public PersonRow donNhan(PersonRow bo) {
        if (bo == null) {
            return this;
        }
        boolean nhanCha = trong(fatherCode) && !trong(bo.fatherCode());
        boolean nhanMe = trong(motherCode) && !trong(bo.motherCode());
        boolean nhanKeTu = trong(heirOfCode) && !trong(bo.heirOfCode());
        return new PersonRow(rowNo, externalCode, raw, normalized,
                trong(fullName) ? bo.fullName() : fullName,
                trong(tabooName) ? bo.tabooName() : tabooName,
                trong(posthumousName) ? bo.posthumousName() : posthumousName,
                trong(hanNomName) ? bo.hanNomName() : hanNomName,
                gender == null || gender == Gender.UNKNOWN ? bo.gender() : gender,
                generation == null ? bo.generation() : generation,
                nhanCha ? bo.fatherCode() : fatherCode,
                nhanMe ? bo.motherCode() : motherCode,
                nhanCha || nhanMe ? bo.parentRel() : parentRel,
                alive == null ? bo.alive() : alive,
                birthYear == null ? bo.birthYear() : birthYear,
                death == null ? bo.death() : death,
                trong(nativePlace) ? bo.nativePlace() : nativePlace,
                trong(nativePlaceCode) ? bo.nativePlaceCode() : nativePlaceCode,
                nhanKeTu ? bo.heirOfCode() : heirOfCode,
                nhanKeTu ? bo.heirType() : heirType,
                resolvedPersonId == null ? bo.resolvedPersonId() : resolvedPersonId,
                plannedAction);
    }

    private static boolean trong(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Người này đã mất hay chưa.
     *
     * <p>Ô "Còn sống" để trống <b>không</b> có nghĩa là còn sống: với một cuốn gia phả, mặc định
     * như vậy sinh ra một phả đồ toàn người sống từ đời thứ ba. Khi ô trống thì suy từ sự có mặt
     * của ngày giỗ, và nếu cũng không có thì trả {@code null} — chưa biết, và bộ kiểm nói ra điều
     * đó thay vì tự quyết.</p>
     */
    public Boolean daMat() {
        if (alive != null) {
            return !alive;
        }
        return death != null ? Boolean.TRUE : null;
    }

    public boolean coCha() {
        return fatherCode != null && !fatherCode.isBlank();
    }

    public boolean coMe() {
        return motherCode != null && !motherCode.isBlank();
    }

    /** Tên hiển thị dùng trong thông báo lỗi — đủ để người nhập nhận ra dòng nào. */
    public String nhan() {
        return fullName == null ? externalCode : externalCode + " (" + fullName + ")";
    }
}
