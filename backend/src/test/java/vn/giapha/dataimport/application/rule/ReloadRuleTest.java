package vn.giapha.dataimport.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.giapha.dataimport.application.rule.RuleFixtures.codes;
import static vn.giapha.dataimport.application.rule.RuleFixtures.ctx;
import static vn.giapha.dataimport.application.rule.RuleFixtures.of;
import static vn.giapha.dataimport.application.rule.RuleFixtures.row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/** Hai luật chỉ có nghĩa từ lần tải thứ hai trở đi. */
class ReloadRuleTest {

    private final ReloadRule rule = new ReloadRule();

    @Test
    @DisplayName("Lần nhập đầu tiên: mọi dòng đều CREATE, và đó là điều đúng — không chặn")
    void lanNhapDauTien() {
        List<PersonRow> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            rows.add(row(i + 1, "AT-01-" + i, "Người " + i).build());
        }
        ValidationContext ctx = ctx(rows);
        rule.apply(ctx);
        assertThat(codes(ctx)).isEmpty();
    }

    @Test
    @DisplayName("Tải lại nguyên tệp cũ: 100% UPDATE, không lỗi, không cảnh báo")
    void taiLaiNguyenTepCu() {
        List<PersonRow> rows = new ArrayList<>();
        Map<String, UUID> daCo = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            rows.add(row(i + 1, "AT-01-" + i, "Người " + i).build());
            daCo.put("AT-01-" + i, UUID.randomUUID());
        }
        ValidationContext ctx = RuleFixtures.ctxTaiLai(rows, daCo);
        rule.apply(ctx);

        assertThat(codes(ctx)).isEmpty();
        assertThat(ctx.personRows()).allSatisfy(r ->
                assertThat(r.plannedAction())
                        .isEqualTo(vn.giapha.dataimport.domain.PlannedAction.UPDATE));
    }

    @Test
    @DisplayName("Sổ giấy bị đánh số lại: chi đã có dữ liệu mà toàn dòng mới thì BỊ CHẶN")
    void soGiayBiDanhSoLai() {
        // Khong mot luat nao khac bat duoc ca nay, vi TUNG DONG MOT deu hoan toan hop le.
        List<PersonRow> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            rows.add(row(i + 1, "MOI-" + i, "Người " + i).build());
        }
        Map<String, UUID> daCo = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            daCo.put("AT-01-" + i, UUID.randomUUID());
        }
        ValidationContext ctx = RuleFixtures.ctxTaiLai(rows, daCo);
        rule.apply(ctx);

        List<ImportIssue> chan = of(ctx, IssueCode.IMP_MASS_CREATE_GUARD);
        assertThat(chan).hasSize(1);
        assertThat(chan.get(0).chan()).isTrue();
        assertThat(chan.get(0).context()).containsEntry("soTao", 20L).containsEntry("tiLe", 100L);
        assertThat(chan.get(0).message()).contains("bổ sung lớn");
    }

    @Test
    @DisplayName("Bổ sung vừa phải (20% dòng mới) thì đi qua được")
    void boSungVuaPhai() {
        List<PersonRow> rows = new ArrayList<>();
        Map<String, UUID> daCo = new LinkedHashMap<>();
        for (int i = 1; i <= 16; i++) {
            rows.add(row(i + 1, "AT-01-" + i, "Người " + i).build());
            daCo.put("AT-01-" + i, UUID.randomUUID());
        }
        for (int i = 17; i <= 20; i++) {
            rows.add(row(i + 1, "AT-01-" + i, "Người mới " + i).build());
        }
        ValidationContext ctx = RuleFixtures.ctxTaiLai(rows, daCo);
        rule.apply(ctx);
        assertThat(codes(ctx)).doesNotContain(IssueCode.IMP_MASS_CREATE_GUARD);
    }

    @Test
    @DisplayName("Dòng biến mất khỏi tệp: cảnh báo, và KHÔNG xoá ai cả")
    void dongBienMat() {
        List<PersonRow> rows = List.of(row(2, "AT-01-1", "Người 1").build());
        Map<String, UUID> daCo = new LinkedHashMap<>();
        daCo.put("AT-01-1", UUID.randomUUID());
        daCo.put("AT-01-2", UUID.randomUUID());
        daCo.put("AT-01-3", UUID.randomUUID());

        ValidationContext ctx = RuleFixtures.ctxTaiLai(rows, daCo);
        rule.apply(ctx);

        List<ImportIssue> issues = of(ctx, IssueCode.IMP_ROW_DISAPPEARED);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).chan()).isFalse();
        assertThat(issues.get(0).message()).contains("KHÔNG xoá ai cả");
        assertThat(issues.get(0).context()).containsEntry("soMaVang", 2);
    }
}
