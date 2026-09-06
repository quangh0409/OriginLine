package vn.giapha.events.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.giapha.events.domain.EventSubject;

/**
 * Nhân khẩu chủ thể của sự kiện — tập con của schema {@code PersonSummaryDto}.
 *
 * <p><b>Chỉ trường Tầng 1.</b> Không có năm sinh, không quê quán, không ảnh: danh sách giỗ là màn
 * hình công khai với thành viên, và một sự kiện không phải chỗ để lộ thêm thông tin về người còn
 * sống. Cần hồ sơ đầy đủ thì giao diện gọi {@code GET /persons/{id}}, nơi bộ lọc phân tầng chạy lại
 * từ đầu.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EventPerson", description = "Nhan khau chu the su kien (Tang 1)")
public record EventPersonDto(UUID id,
                             String displayName,
                             String nameHanNom,
                             Integer generation,
                             boolean isAlive,
                             BranchRefDto primaryBranch) {

    public static EventPersonDto from(EventSubject subject) {
        if (subject == null) {
            return null;
        }
        return new EventPersonDto(
                subject.personId(),
                subject.displayName(),
                subject.nameHanNom(),
                subject.generation(),
                subject.alive(),
                BranchRefDto.from(subject.primaryBranch()));
    }
}
