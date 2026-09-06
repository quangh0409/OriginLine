package vn.giapha.genealogy.api.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.giapha.genealogy.api.rest.dto.ContactInfoDto;
import vn.giapha.genealogy.api.rest.dto.CreatePersonRequest;
import vn.giapha.genealogy.api.rest.dto.DateDualDto;
import vn.giapha.genealogy.api.rest.dto.PersonNameInput;
import vn.giapha.genealogy.api.rest.dto.RelationshipLinkInput;
import vn.giapha.genealogy.api.rest.dto.UpdatePersonRequest;
import vn.giapha.genealogy.application.GenealogyProblemCodes;
import vn.giapha.genealogy.application.command.AddPersonCommand;
import vn.giapha.genealogy.application.command.RelationshipLinkCommand;
import vn.giapha.genealogy.application.command.UpdatePersonCommand;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.vo.LunarDate;

/**
 * Dịch thân yêu cầu HTTP thành command của tầng application.
 *
 * <h2>Ba trạng thái, không phải hai</h2>
 * Với {@code PATCH}, một trường có ba khả năng: <b>vắng mặt</b> (giữ nguyên), <b>có giá trị</b>
 * (ghi đè), <b>có tên trong {@code clearFields}</b> (xoá trắng). JSON không tự phân biệt được khả
 * năng thứ nhất với việc gửi {@code null}, nên controller đọc danh sách khoá thực sự có mặt từ cây
 * JSON và truyền vào đây; chỗ này chỉ việc dịch sang {@link FieldChange}.
 *
 * <p>Một trường vừa có giá trị vừa nằm trong {@code clearFields} là mâu thuẫn ý định, trả
 * {@code 422} chứ không tự chọn giúp một bên.</p>
 */
public final class PersonRequestMapper {

    private PersonRequestMapper() {
    }

    public static AddPersonCommand toCommand(CreatePersonRequest request) {
        return new AddPersonCommand(
                toNames(request.names()),
                request.gender(),
                Boolean.TRUE.equals(request.isAlive()),
                toLifeDate(request.birth()),
                toLifeDate(request.death()),
                request.nativePlace(),
                request.currentPlaceProvince(),
                request.currentPlaceFull(),
                request.occupation(),
                request.biography(),
                request.primaryBranchId(),
                toContact(request.contact()),
                request.attributes(),
                request.privacyLevel(),
                toLinks(request.initialRelationships()),
                Boolean.TRUE.equals(request.confirmTabooOverride()),
                Boolean.TRUE.equals(request.confirmDuplicateOverride()),
                request.note());
    }

    /**
     * @param present tên các khoá thực sự xuất hiện trong body JSON
     * @param expectedVersion phiên bản lấy từ header {@code If-Match}
     */
    public static UpdatePersonCommand toCommand(UUID personId, UpdatePersonRequest request,
                                                Set<String> present, Long expectedVersion) {
        Set<String> cleared = Set.copyOf(request.clearFields());
        rejectContradictions(present, cleared);

        return new UpdatePersonCommand(
                personId,
                expectedVersion,
                change(present, cleared, "names", toNames(request.names())),
                change(present, cleared, "gender", request.gender()),
                change(present, cleared, "isAlive", request.isAlive()),
                change(present, cleared, "isDeleted", request.isDeleted()),
                change(present, cleared, "birth", toLifeDate(request.birth())),
                change(present, cleared, "death", toLifeDate(request.death())),
                change(present, cleared, "nativePlace", request.nativePlace()),
                change(present, cleared, "currentPlaceProvince", request.currentPlaceProvince()),
                change(present, cleared, "currentPlaceFull", request.currentPlaceFull()),
                change(present, cleared, "occupation", request.occupation()),
                change(present, cleared, "biography", request.biography()),
                change(present, cleared, "avatarUrl", request.avatarUrl()),
                change(present, cleared, "primaryBranchId", request.primaryBranchId()),
                contactChange(present, cleared, request.contact()),
                PersonRequestMapper.<Map<String, Object>>change(present, cleared, "attributes",
                        request.attributes()),
                change(present, cleared, "privacyLevel", request.privacyLevel()),
                Boolean.TRUE.equals(request.confirmTabooOverride()),
                request.note());
    }

    /**
     * Khối liên hệ hỗ trợ xoá trắng tới <b>từng trường con</b> ({@code contact.phone}), vì người
     * dùng thường chỉ muốn gỡ số điện thoại chứ không gỡ cả khối.
     */
    private static FieldChange<ContactInfo> contactChange(Set<String> present, Set<String> cleared,
                                                          ContactInfoDto dto) {
        if (cleared.contains("contact")) {
            return FieldChange.clear();
        }
        boolean subFieldCleared = cleared.stream().anyMatch(field -> field.startsWith("contact."));
        if (!present.contains("contact") && !subFieldCleared) {
            return FieldChange.keep();
        }
        String phone = cleared.contains("contact.phone") || dto == null ? null : dto.phone();
        String email = cleared.contains("contact.email") || dto == null ? null : dto.email();
        String zaloId = cleared.contains("contact.zaloId") || dto == null ? null : dto.zaloId();
        return FieldChange.set(new ContactInfo(phone, email, zaloId));
    }

    private static <T> FieldChange<T> change(Set<String> present, Set<String> cleared, String field,
                                             T value) {
        if (cleared.contains(field)) {
            return FieldChange.clear();
        }
        return present.contains(field) ? FieldChange.set(value) : FieldChange.keep();
    }

    private static void rejectContradictions(Set<String> present, Set<String> cleared) {
        for (String field : cleared) {
            if (present.contains(field)) {
                throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                        "Truong '" + field + "' vua co gia tri trong body vua nam trong clearFields");
            }
        }
    }

    private static List<PersonName> toNames(List<PersonNameInput> inputs) {
        if (inputs == null) {
            return null;
        }
        List<PersonName> names = new ArrayList<>();
        for (PersonNameInput input : inputs) {
            names.add(new PersonName(null, input.nameType(), input.fullName(), input.nameHanNom(),
                    Boolean.TRUE.equals(input.isPrimary()), input.note()));
        }
        return names;
    }

    private static List<RelationshipLinkCommand> toLinks(List<RelationshipLinkInput> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream()
                .map(input -> new RelationshipLinkCommand(input.relType(), input.otherPersonId(),
                        input.otherIsSource(), input.heirKind(), input.spouseOrder(),
                        input.validFrom(), input.validTo(), input.note()))
                .toList();
    }

    private static ContactInfo toContact(ContactInfoDto dto) {
        return dto == null ? null : new ContactInfo(dto.phone(), dto.email(), dto.zaloId());
    }

    /**
     * Mốc song lịch. Gửi cả hai vế mà chỉ có một vế hợp lệ thì vẫn nhận; gửi rỗng cả hai thì coi
     * như không có mốc nào - đúng thực tế gia phả cổ, nơi nhiều người chỉ còn ngày âm hoặc chỉ còn
     * năm.
     */
    private static LifeDate toLifeDate(DateDualDto dto) {
        if (dto == null) {
            return null;
        }
        LunarDate lunar = toLunar(dto.lunar());
        if (dto.solar() == null && lunar == null) {
            return null;
        }
        DatePrecision precision = dto.precision() == null ? DatePrecision.DAY : dto.precision();
        return LifeDate.of(dto.solar(), lunar, precision);
    }

    private static LunarDate toLunar(DateDualDto.LunarDateDto dto) {
        if (dto == null || dto.year() == null || dto.month() == null || dto.day() == null) {
            return null;
        }
        return new LunarDate(dto.year(), dto.month(), dto.day(), Boolean.TRUE.equals(dto.leap()));
    }
}
