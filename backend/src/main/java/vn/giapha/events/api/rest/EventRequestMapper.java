package vn.giapha.events.api.rest;

import java.util.Set;
import vn.giapha.events.api.rest.dto.CreateEventRequest;
import vn.giapha.events.api.rest.dto.UpdateEventRequest;
import vn.giapha.events.application.command.CreateEventCommand;
import vn.giapha.events.application.command.EventScope;
import vn.giapha.events.application.command.UpdateEventCommand;

/**
 * Thân yêu cầu HTTP → lệnh của tầng application.
 *
 * <p>Ở đây, và chỉ ở đây, mã loại sự kiện của <b>hợp đồng</b> được đổi sang mã của <b>cơ sở dữ
 * liệu</b> ({@link EventTypeApiMapper}). Tầng application nói ngôn ngữ của domain; để mã hợp đồng
 * lọt xuống đó là mở đường cho một bản quy đổi thứ hai, và hai bản quy đổi cho cùng một loại sự
 * kiện là đúng khoản nợ mà V10 vừa trả xong.</p>
 */
final class EventRequestMapper {

    private EventRequestMapper() {
    }

    static CreateEventCommand toCommand(CreateEventRequest request) {
        EventTypeApiMapper.WriteType type = EventTypeApiMapper.toWriteType(request.eventType());
        boolean recurring = request.recurring();
        return new CreateEventCommand(
                type.type(),
                request.title(),
                request.description(),
                request.lunarDate() == null ? null : request.lunarDate().toDomain(),
                request.solarDate(),
                recurring,
                scopeOf(type, request.clanWideFlag(), request.scopeBranchId(), request.eventType()),
                request.personId(),
                request.location());
    }

    /**
     * @param presentFields tên các khoá thực sự có mặt ở cấp cao nhất của JSON — căn cứ duy nhất
     *                      cho "vắng mặt = giữ nguyên"
     */
    static UpdateEventCommand toCommand(UpdateEventRequest request, Set<String> presentFields,
                                        long expectedVersion) {
        EventTypeApiMapper.WriteType type = request.eventType() == null
                ? null : EventTypeApiMapper.toWriteType(request.eventType());
        Boolean clanWide = request.clanWide();
        Set<String> fields = presentFields;
        if (type != null && type.clanLevel() != null) {
            if (request.clanWide() != null && !type.clanLevel().equals(request.clanWide())) {
                throw new IllegalArgumentException(
                        mauThuanPhamVi(request.eventType(), type.clanLevel()));
            }
            // GIO_HO / GIO_CHI tu no da noi pham vi. Coi nhu nguoi gui da gui clanWide, de luat
            // "vang mat = giu nguyen" khong giu lai mot pham vi mau thuan voi loai vua doi.
            clanWide = type.clanLevel();
            fields = new java.util.LinkedHashSet<>(presentFields);
            fields.add(UpdateEventCommand.F_CLAN_WIDE);
        }
        return new UpdateEventCommand(
                type == null ? null : type.type(),
                request.title(),
                request.description(),
                request.lunarDate() == null ? null : request.lunarDate().toDomain(),
                request.solarDate(),
                request.recurringAnnually(),
                clanWide,
                request.scopeBranchId(),
                request.personId(),
                request.location(),
                fields,
                expectedVersion);
    }

    /**
     * Phạm vi của sự kiện.
     *
     * <p>{@code GIO_HO} và {@code GIO_CHI} là hai mã hợp đồng cùng trỏ về một giá trị cơ sở dữ liệu
     * ({@code TE_LE}) và phân biệt nhau <b>bằng chính cờ cấp dòng họ</b>. Vì vậy mã đã nói phạm vi
     * rồi; gửi kèm một cờ mâu thuẫn là một yêu cầu không có nghĩa, và im lặng chọn một bên là cách
     * chắc chắn để người gửi tin vào bên còn lại.</p>
     */
    private static EventScope scopeOf(EventTypeApiMapper.WriteType type, boolean clanWide,
                                      java.util.UUID branchId, String rawType) {
        if (type.clanLevel() != null) {
            if (clanWide != type.clanLevel()) {
                throw new IllegalArgumentException(mauThuanPhamVi(rawType, type.clanLevel()));
            }
        }
        return new EventScope(clanWide, branchId);
    }

    private static String mauThuanPhamVi(String rawType, boolean expected) {
        return "Loai su kien " + rawType + " da an dinh pham vi clanWide=" + expected
                + "; khong gui kem mot co mau thuan. Dung GIO_HO cho viec ca ho va GIO_CHI cho"
                + " viec mot chi.";
    }
}
