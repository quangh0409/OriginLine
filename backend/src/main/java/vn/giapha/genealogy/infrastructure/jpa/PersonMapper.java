package vn.giapha.genealogy.infrastructure.jpa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.LineageStatus;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;
import vn.giapha.shared.vo.PersonId;

/**
 * Chuyển đổi {@link Person} (POJO thuần) ⇄ {@link PersonJpaEntity}.
 *
 * <h2>Vì sao có khoá {@code _profile} trong {@code attributes}</h2>
 * Bảng {@code person} của V2 <b>không có</b> cột cho nghề nghiệp, tiểu sử, ảnh, nơi ở cấp tỉnh hay
 * khối liên hệ, trong khi contract lại yêu cầu đủ những trường đó. Migration thuộc sở hữu của W1
 * nên W2 không tự thêm cột; lối đi hợp lệ còn lại là cột {@code attributes} (jsonb) — đúng vai trò
 * "thuộc tính linh hoạt theo từng nhân khẩu" mà kiến trúc đã dành cho nó.
 *
 * <p>Để không lẫn với khoá do dòng họ tự đặt, toàn bộ phần này nằm gọn dưới một khoá dành riêng
 * {@code _profile} và <b>bị lược khỏi</b> {@code attributes} khi trả ra API. Nếu W1 về sau bổ sung
 * cột thật thì chỉ mỗi mapper này phải sửa.</p>
 *
 * <h2>Ngày âm lịch</h2>
 * Cột {@code birth_lunar}/{@code death_lunar} là jsonb {@code {year, month, day, leap}}; ràng buộc
 * {@code ck_person_birth_lunar} bắt buộc có {@code day} và {@code month}. Mức chính xác
 * ({@code precision}) không có cột riêng nên cũng nằm trong {@code _profile}.
 */
@Component
public class PersonMapper {

    /** Khoá dành riêng trong {@code attributes}; không bao giờ lộ ra API. */
    public static final String PROFILE_KEY = "_profile";

    private static final String K_OCCUPATION = "occupation";
    private static final String K_BIOGRAPHY = "biography";
    private static final String K_AVATAR = "avatarKey";
    private static final String K_PROVINCE = "currentPlaceProvince";
    private static final String K_CONTACT = "contact";
    private static final String K_BIRTH_PRECISION = "birthPrecision";
    private static final String K_DEATH_PRECISION = "deathPrecision";

    public Person toDomain(PersonJpaEntity entity, List<PersonNameJpaEntity> nameRows) {
        Map<String, Object> attributes = new LinkedHashMap<>(
                entity.getAttributes() == null ? Map.of() : entity.getAttributes());
        Map<String, Object> profile = extractProfile(attributes);

        List<PersonName> names = new ArrayList<>();
        if (nameRows != null) {
            for (PersonNameJpaEntity row : nameRows) {
                names.add(new PersonName(row.getId(), NameType.valueOf(row.getNameType()),
                        row.getFullName(), row.getNameHanNom(), row.isPrimary(), row.getNote()));
            }
        }

        return Person.rehydrate(PersonId.of(entity.getId()))
                .gender(Gender.fromCode(entity.getGender()))
                .generation(entity.getGeneration())
                .birthOrder(entity.getBirthOrder())
                .birth(toLifeDate(entity.getBirthSolar(), entity.getBirthLunar(),
                        precisionOf(profile, K_BIRTH_PRECISION)))
                .death(toLifeDate(entity.getDeathSolar(), entity.getDeathLunar(),
                        precisionOf(profile, K_DEATH_PRECISION)))
                .alive(entity.isAlive())
                .nativePlace(entity.getNativePlace())
                .currentPlaceFull(entity.getCurrentPlace())
                .currentPlaceProvince(str(profile.get(K_PROVINCE)))
                .occupation(str(profile.get(K_OCCUPATION)))
                .biography(str(profile.get(K_BIOGRAPHY)))
                .avatarKey(str(profile.get(K_AVATAR)))
                .contact(toContact(profile.get(K_CONTACT)))
                .names(names)
                .primaryBranchId(entity.getPrimaryBranchId())
                .lineageStatus(LineageStatus.valueOf(entity.getLineageStatus()))
                .privacyLevel(PrivacyLevel.fromDbValue(entity.getPrivacyLevel()))
                .attributes(attributes)
                .deleted(entity.isDeleted(), toInstant(entity.getDeletedAt()))
                .anonymized(entity.isAnonymized(), toInstant(entity.getAnonymizedAt()))
                .updatedAt(toInstant(entity.getUpdatedAt()))
                .version(entity.getVersion())
                .build();
    }

    public void applyToEntity(Person person, PersonJpaEntity entity) {
        entity.setGender(normalizeGender(person.gender()));
        entity.setGeneration(person.generation());
        entity.setBirthOrder(person.birthOrder());

        entity.setBirthSolar(person.birth() == null ? null : person.birth().solar());
        entity.setBirthLunar(person.birth() == null ? null : toLunarJson(person.birth().lunar()));
        entity.setDeathSolar(person.death() == null ? null : person.death().solar());
        entity.setDeathLunar(person.death() == null ? null : toLunarJson(person.death().lunar()));

        entity.setAlive(person.isAlive());
        entity.setNativePlace(person.nativePlace());
        entity.setCurrentPlace(person.currentPlaceFull());
        entity.setPrimaryBranchId(person.primaryBranchId());
        entity.setLineageStatus(person.lineageStatus().name());
        entity.setPrivacyLevel(person.privacyLevel().dbValue());
        entity.setDeleted(person.isDeleted());
        entity.setDeletedAt(toOffset(person.deletedAt()));
        entity.setAnonymized(person.isAnonymized());
        entity.setAnonymizedAt(toOffset(person.anonymizedAt()));

        Map<String, Object> attributes = new LinkedHashMap<>(person.attributes());
        attributes.remove(PROFILE_KEY);
        Map<String, Object> profile = new LinkedHashMap<>();
        putIfNotNull(profile, K_OCCUPATION, person.occupation());
        putIfNotNull(profile, K_BIOGRAPHY, person.biography());
        putIfNotNull(profile, K_AVATAR, person.avatarKey());
        putIfNotNull(profile, K_PROVINCE, person.currentPlaceProvince());
        if (!person.contact().isEmpty()) {
            Map<String, Object> contact = new LinkedHashMap<>();
            putIfNotNull(contact, "phone", person.contact().phone());
            putIfNotNull(contact, "email", person.contact().email());
            putIfNotNull(contact, "zaloId", person.contact().zaloId());
            profile.put(K_CONTACT, contact);
        }
        if (person.birth() != null) {
            profile.put(K_BIRTH_PRECISION, person.birth().precision().name());
        }
        if (person.death() != null) {
            profile.put(K_DEATH_PRECISION, person.death().precision().name());
        }
        if (!profile.isEmpty()) {
            attributes.put(PROFILE_KEY, profile);
        }
        entity.setAttributes(attributes);
    }

    /** Thuộc tính mở rộng <b>đã lược</b> khoá dành riêng — đây là thứ được phép trả ra API. */
    public static Map<String, Object> publicAttributes(Person person) {
        Map<String, Object> copy = new LinkedHashMap<>(person.attributes());
        copy.remove(PROFILE_KEY);
        return copy;
    }

    /**
     * Ràng buộc {@code ck_person_gender} của V2 chỉ nhận {@code MALE|FEMALE|UNKNOWN}, trong khi
     * {@code Gender} của shared kernel còn có {@code OTHER}. Ánh xạ {@code OTHER → UNKNOWN} thay vì
     * để câu insert vỡ ở tầng driver; đây là điểm cần W1 hoặc shared chốt lại (xem báo cáo W2).
     */
    private String normalizeGender(Gender gender) {
        if (gender == null || gender == Gender.OTHER) {
            return Gender.UNKNOWN.name();
        }
        return gender.name();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractProfile(Map<String, Object> attributes) {
        Object raw = attributes.remove(PROFILE_KEY);
        return raw instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private DatePrecision precisionOf(Map<String, Object> profile, String key) {
        Object raw = profile.get(key);
        if (raw == null) {
            return DatePrecision.DAY;
        }
        try {
            return DatePrecision.valueOf(raw.toString());
        } catch (IllegalArgumentException ex) {
            return DatePrecision.DAY;
        }
    }

    private LifeDate toLifeDate(LocalDate solar, Map<String, Object> lunarJson, DatePrecision precision) {
        LunarDate lunar = toLunar(lunarJson);
        if (solar == null && lunar == null) {
            return null;
        }
        return LifeDate.of(solar, lunar, precision);
    }

    private LunarDate toLunar(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Integer day = intOf(json.get("day"));
        Integer month = intOf(json.get("month"));
        if (day == null || month == null) {
            return null;
        }
        Integer year = intOf(json.get("year"));
        boolean leap = Boolean.TRUE.equals(json.get("leap"));
        return new LunarDate(year == null ? 0 : year, month, day, leap);
    }

    private Map<String, Object> toLunarJson(LunarDate lunar) {
        if (lunar == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("year", lunar.year());
        json.put("month", lunar.month());
        json.put("day", lunar.day());
        json.put("leap", lunar.leapMonth());
        return json;
    }

    @SuppressWarnings("unchecked")
    private ContactInfo toContact(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return ContactInfo.EMPTY;
        }
        Map<String, Object> contact = (Map<String, Object>) map;
        return new ContactInfo(str(contact.get("phone")), str(contact.get("email")),
                str(contact.get("zaloId")));
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffset(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
