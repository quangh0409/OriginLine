package vn.giapha.dataimport.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.dataimport.domain.RawRow;
import vn.giapha.dataimport.domain.TextNormalizer;

/**
 * Biến dòng thô thành dòng chờ đã hiểu được.
 *
 * <h2>Không phán xét ở đây</h2>
 * Lớp này <b>không sinh một lỗi nào</b>. Ô không đọc được thì trường tương ứng để {@code null} và
 * ô gốc vẫn nằm nguyên trong {@code raw}/{@code normalized} để bộ kiểm nói lại bằng một câu tiếng
 * Việt tử tế.
 *
 * <p>Lý do tách bạch: nếu việc ánh xạ cũng báo lỗi thì một tệp có 40 ô hỏng sẽ dừng ở ô thứ nhất
 * và người nhập phải tải lên bốn mươi lần. Đọc hết, rồi mới phán xét hết — đó là toàn bộ giá trị
 * của việc có một khu vực chờ.</p>
 */
@Component
public class RowMapper {

    /** @return dòng Nhân khẩu; rỗng khi dòng trống hoàn toàn (người nhập xoá nội dung, để lại khung) */
    public Optional<PersonRow> toPersonRow(RawRow raw) {
        if (raw.rong()) {
            return Optional.empty();
        }
        String ma = TextNormalizer.normalizeCode(raw.get(ImportColumn.MA));
        LunarDeathDate death = LunarDeathDate.doc(raw.get(ImportColumn.NGAY_MAT_AM))
                .filter(LunarDeathDate.KetQua::thanhCong)
                .map(LunarDeathDate.KetQua::value)
                .orElse(null);

        return Optional.of(new PersonRow(
                raw.rowNo(),
                ma,
                raw.raw(),
                raw.normalized(),
                raw.get(ImportColumn.HO_TEN),
                raw.get(ImportColumn.TEN_HUY),
                raw.get(ImportColumn.THUY_HIEU),
                raw.get(ImportColumn.TEN_HAN_NOM),
                CellCodec.gioi(raw.get(ImportColumn.GIOI)),
                CellCodec.so(raw.get(ImportColumn.DOI)),
                TextNormalizer.normalizeCode(raw.get(ImportColumn.MA_CHA)),
                TextNormalizer.normalizeCode(raw.get(ImportColumn.MA_ME)),
                CellCodec.quanHe(raw.get(ImportColumn.QUAN_HE)),
                CellCodec.conSong(raw.get(ImportColumn.CON_SONG)),
                CellCodec.so(raw.get(ImportColumn.NAM_SINH)),
                death,
                raw.get(ImportColumn.NGUYEN_QUAN),
                TextNormalizer.normalizeCode(raw.get(ImportColumn.MA_NGUYEN_QUAN)),
                TextNormalizer.normalizeCode(raw.get(ImportColumn.KE_TU_CHO_AI)),
                CellCodec.loaiKeTu(raw.get(ImportColumn.LOAI_KE_TU)),
                null,
                PlannedAction.CREATE));
    }

    public Optional<MarriageRow> toMarriageRow(RawRow raw) {
        if (raw.rong()) {
            return Optional.empty();
        }
        return Optional.of(new MarriageRow(
                raw.rowNo(),
                TextNormalizer.normalizeCode(raw.get(MarriageColumn.MA_CHONG)),
                TextNormalizer.normalizeCode(raw.get(MarriageColumn.MA_VO)),
                CellCodec.bac(raw.get(MarriageColumn.BAC)),
                CellCodec.so(raw.get(MarriageColumn.TU_NAM)),
                CellCodec.so(raw.get(MarriageColumn.DEN_NAM)),
                CellCodec.lyDoKetThuc(raw.get(MarriageColumn.LY_DO_KET_THUC)),
                raw.raw()));
    }

    public List<PersonRow> toPersonRows(List<RawRow> raws) {
        List<PersonRow> rows = new ArrayList<>(raws.size());
        for (RawRow raw : raws) {
            toPersonRow(raw).ifPresent(rows::add);
        }
        return rows;
    }

    public List<MarriageRow> toMarriageRows(List<RawRow> raws) {
        List<MarriageRow> rows = new ArrayList<>(raws.size());
        for (RawRow raw : raws) {
            toMarriageRow(raw).ifPresent(rows::add);
        }
        return rows;
    }
}
