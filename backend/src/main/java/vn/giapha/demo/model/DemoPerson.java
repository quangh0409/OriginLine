package vn.giapha.demo.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.shared.vo.LunarDate;

/**
 * Nhân khẩu trong bộ dữ liệu giả — POJO khả biến, chỉ sống trong bộ nhớ lúc sinh dữ liệu.
 *
 * <p>Các trường có tiền tố "quan hệ" ({@link #father()}, {@link #mother()}, {@link #wives()}) chỉ
 * phục vụ thuật toán sinh; khi ghi xuống CSDL chúng biến thành dòng trong bảng
 * {@code relationship} + cạnh AGE.</p>
 */
public final class DemoPerson {

    private final UUID id;
    private final String key;
    private final int generation;
    private final boolean bloodline;

    private String gender = "MALE";
    private Integer birthOrder;
    private LocalDate birthSolar;
    private LunarDate birthLunar;
    private LocalDate deathSolar;
    private LunarDate deathLunar;
    private boolean alive = true;
    private String nativePlace;
    private String currentPlace;
    private UUID branchId;
    private String lineageStatus = "NORMAL";
    private String privacyLevel = "DEFAULT";
    private boolean deleted;
    private boolean anonymized;

    private String surname;
    private String middleName;
    private String givenName;

    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<DemoName> names = new ArrayList<>();
    private final List<DemoPerson> children = new ArrayList<>();
    private final List<DemoPerson> wives = new ArrayList<>();

    private DemoPerson father;
    private DemoPerson mother;
    private DemoPerson husband;

    public DemoPerson(UUID id, String key, int generation, boolean bloodline) {
        this.id = id;
        this.key = key;
        this.generation = generation;
        this.bloodline = bloodline;
    }

    public UUID id() { return id; }
    public String key() { return key; }
    public int generation() { return generation; }
    public boolean bloodline() { return bloodline; }

    public String gender() { return gender; }
    public void gender(String value) { this.gender = value; }
    public boolean male() { return "MALE".equals(gender); }

    public Integer birthOrder() { return birthOrder; }
    public void birthOrder(Integer value) { this.birthOrder = value; }

    public LocalDate birthSolar() { return birthSolar; }
    public void birthSolar(LocalDate value) { this.birthSolar = value; }
    public LunarDate birthLunar() { return birthLunar; }
    public void birthLunar(LunarDate value) { this.birthLunar = value; }

    public LocalDate deathSolar() { return deathSolar; }
    public void deathSolar(LocalDate value) { this.deathSolar = value; }
    public LunarDate deathLunar() { return deathLunar; }
    public void deathLunar(LunarDate value) { this.deathLunar = value; }

    public boolean alive() { return alive; }
    public void alive(boolean value) { this.alive = value; }

    public String nativePlace() { return nativePlace; }
    public void nativePlace(String value) { this.nativePlace = value; }
    public String currentPlace() { return currentPlace; }
    public void currentPlace(String value) { this.currentPlace = value; }

    public UUID branchId() { return branchId; }
    public void branchId(UUID value) { this.branchId = value; }

    public String lineageStatus() { return lineageStatus; }
    public void lineageStatus(String value) { this.lineageStatus = value; }

    public String privacyLevel() { return privacyLevel; }
    public void privacyLevel(String value) { this.privacyLevel = value; }

    public boolean deleted() { return deleted; }
    public void deleted(boolean value) { this.deleted = value; }
    public boolean anonymized() { return anonymized; }
    public void anonymized(boolean value) { this.anonymized = value; }

    public String surname() { return surname; }
    public void surname(String value) { this.surname = value; }
    public String middleName() { return middleName; }
    public void middleName(String value) { this.middleName = value; }
    public String givenName() { return givenName; }
    public void givenName(String value) { this.givenName = value; }

    /** Tên đầy đủ dựng từ họ + chữ đệm theo đời + tên. */
    public String fullName() {
        StringBuilder sb = new StringBuilder(surname);
        if (middleName != null && !middleName.isBlank()) {
            sb.append(' ').append(middleName);
        }
        return sb.append(' ').append(givenName).toString();
    }

    public Map<String, Object> attributes() { return attributes; }
    public List<DemoName> names() { return names; }
    public List<DemoPerson> children() { return children; }
    public List<DemoPerson> wives() { return wives; }

    public DemoPerson father() { return father; }
    public void father(DemoPerson value) { this.father = value; }
    public DemoPerson mother() { return mother; }
    public void mother(DemoPerson value) { this.mother = value; }
    public DemoPerson husband() { return husband; }
    public void husband(DemoPerson value) { this.husband = value; }

    /** Tên huý (HUY) — đầu vào của cảnh báo kỵ húy FR-1.6. */
    public String huyName() {
        return names.stream().filter(n -> "HUY".equals(n.type())).map(DemoName::fullName).findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return "%s (%s, đời %d)".formatted(key, gender, generation);
    }
}
