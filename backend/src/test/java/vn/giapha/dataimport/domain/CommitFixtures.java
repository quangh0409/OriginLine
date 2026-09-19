package vn.giapha.dataimport.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import vn.giapha.shared.vo.Gender;

/** Dựng {@link PersonRow} / {@link MarriageRow} cho test của bước ghi — gọn hơn hàm dựng 22 tham số. */
public final class CommitFixtures {

    private CommitFixtures() {
    }

    public static Builder row(int rowNo, String ma) {
        return new Builder(rowNo, ma);
    }

    public static MarriageRow honPhoi(int rowNo, String chong, String vo, Integer bac) {
        return new MarriageRow(rowNo, chong, vo, bac, null, null, null, Map.of());
    }

    public static final class Builder {

        private final int rowNo;
        private final String ma;
        private String hoTen;
        private Integer doi;
        private String cha;
        private String me;
        private CellCodec.ParentRel quanHe = CellCodec.ParentRel.BIO;
        private Boolean conSong = Boolean.FALSE;
        private LunarDeathDate gio = new LunarDeathDate(1950, 8, 15, false);
        private String keTuCho;
        private CellCodec.HeirType loaiKeTu;

        private Builder(int rowNo, String ma) {
            this.rowNo = rowNo;
            this.ma = ma;
            this.hoTen = "Nguyễn Văn " + ma;
        }

        public Builder ten(String v) {
            this.hoTen = v;
            return this;
        }

        public Builder doi(Integer v) {
            this.doi = v;
            return this;
        }

        public Builder cha(String v) {
            this.cha = v;
            return this;
        }

        public Builder me(String v) {
            this.me = v;
            return this;
        }

        public Builder conNuoi() {
            this.quanHe = CellCodec.ParentRel.ADOPT;
            return this;
        }

        /** Ô "Còn sống" và ô "Ngày mất âm" đều trống — ca không rõ sống hay đã mất. */
        public Builder khongRoSongChet() {
            this.conSong = null;
            this.gio = null;
            return this;
        }

        public Builder conSong() {
            this.conSong = Boolean.TRUE;
            this.gio = null;
            return this;
        }

        public Builder keTu(String choAi, CellCodec.HeirType loai) {
            this.keTuCho = choAi;
            this.loaiKeTu = loai;
            return this;
        }

        public PersonRow build() {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("Mã", ma);
            o.put("Họ tên", hoTen);
            return new PersonRow(rowNo, ma, o, o, hoTen, null, null, null, Gender.MALE, doi,
                    cha, me, quanHe, conSong, null, gio, null, null, keTuCho, loaiKeTu, null,
                    PlannedAction.CREATE);
        }
    }
}
