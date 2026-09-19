package vn.giapha.dataimport.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.DuplicateScorer;

/**
 * Chốt chống <b>trôi ngưỡng nghi trùng</b>.
 *
 * <h2>Vì sao cái chốt này nằm ở test chứ không ở mã chính</h2>
 * Ngưỡng thật sống ở {@code DuplicateScorer.NGUONG_NGHI_TRUNG} bên {@code genealogy}. Mã chính của
 * {@code dataimport} <b>không tham chiếu thẳng</b> được vào đó: {@code genealogy.application} chưa
 * được công bố làm {@code @NamedInterface}, nên một lời gọi như vậy làm {@code ModularityTests} đỏ.
 *
 * <p>Bộ kiểm ranh giới module <b>không</b> quét cây test ({@code ApplicationModules} dùng
 * {@code DO_NOT_INCLUDE_TESTS}), nên đây là chỗ duy nhất trong dự án nơi hai con số ấy đứng cạnh
 * nhau được. Sửa một trong hai mà quên chỗ kia là một bài test <b>đỏ</b>, không phải một lỗi im
 * lặng — và lỗi im lặng ở đây có triệu chứng rất khó truy: giao diện tick sẵn "hợp nhất" cho một
 * cặp mà máy chủ coi là chưa đáng nghi.</p>
 *
 * <p>Cách sửa tận gốc: {@code genealogy} công bố ngưỡng qua mặt tiền {@code do-trung} (một
 * phương thức trên {@code PersonScreeningService}), rồi {@code ImportDuplicatePolicy} đọc thẳng từ
 * đó và lớp test này xoá đi.</p>
 */
@DisplayName("Ngưỡng nghi trùng chỉ được có một bản")
class ImportDuplicatePolicyTest {

    @Test
    @DisplayName("Ngưỡng máy chủ công bố bằng đúng ngưỡng bộ dò đang chạy — không phải 60 của kế hoạch")
    void nguongCongBoBangNguongThat() {
        assertThat(ImportDuplicatePolicy.SUSPECT_THRESHOLD)
                .as("plan/02 §6.4 ghi 60; bo do da duoc hieu chinh lai va con so THAT la 70")
                .isEqualTo(DuplicateScorer.NGUONG_NGHI_TRUNG)
                .isEqualTo(70);
    }

    @Test
    @DisplayName("Ngưỡng tick sẵn hợp nhất phải chặt hơn ngưỡng kêu, và không bao giờ tự gộp")
    void nguongTickSanChatHonNguongKeu() {
        assertThat(ImportDuplicatePolicy.PRESELECT_MERGE_THRESHOLD)
                .as("tick san o diem THAP hon nguong keu la tick san cho mot cap chua he duoc keu")
                .isGreaterThan(ImportDuplicatePolicy.SUSPECT_THRESHOLD);
        assertThat(ImportDuplicatePolicy.AUTO_MERGE)
                .as("gop nham hai nguoi la hop nhat hai nhanh con chau vao mot node sai")
                .isFalse();
    }
}
