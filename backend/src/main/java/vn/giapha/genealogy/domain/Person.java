package vn.giapha.genealogy.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import vn.giapha.genealogy.domain.event.PersonAddedEvent;
import vn.giapha.genealogy.domain.event.PersonAnonymizedEvent;
import vn.giapha.genealogy.domain.event.PersonMovedBranchEvent;
import vn.giapha.genealogy.domain.event.PersonRestoredEvent;
import vn.giapha.genealogy.domain.event.PersonSoftDeletedEvent;
import vn.giapha.genealogy.domain.event.PersonUpdatedEvent;
import vn.giapha.shared.domain.AggregateRoot;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Nhân khẩu — aggregate root của context {@code genealogy}.</b> POJO thuần: không một annotation
 * Spring hay JPA nào được xuất hiện trong file này (bản chiếu JPA nằm ở
 * {@code genealogy.infrastructure.jpa.PersonJpaEntity}).
 *
 * <h2>Bất biến nghiệp vụ mà lớp này tự bảo vệ</h2>
 * <ol>
 *   <li><b>Xoá mềm, không bao giờ xoá cứng.</b> {@link #softDelete(String)} chỉ bật cờ; node trong
 *       đồ thị AGE và mọi cạnh quan hệ được giữ nguyên để cây không đứt.</li>
 *   <li><b>Đúng một tên chính.</b> Mọi lối vào danh sách tên đều đi qua {@code normalizeNames()},
 *       nên không tồn tại trạng thái hai tên chính hoặc không có tên nào.</li>
 *   <li><b>Còn sống thì không có ngày mất.</b> Trùng với ràng buộc {@code ck_person_alive_vs_death}
 *       ở V2 — chặn ở domain để lỗi hiện ra dưới dạng thông điệp nghiệp vụ, không phải
 *       {@code SQLException} lúc flush.</li>
 *   <li><b>Xoá dữ liệu cá nhân hợp pháp = ẩn danh hoá</b> ({@link #anonymize()}), không phải xoá.
 *       Vai vế phả hệ ở lại, dữ liệu Tầng 3 biến mất.</li>
 * </ol>
 *
 * <p><b>Không có setter trần.</b> Mỗi thay đổi là một hành vi nghiệp vụ có tên
 * ({@code markDeceased}, {@code moveToBranch}, {@code addName}...), và mỗi hành vi tự ghi nhận
 * domain event tương ứng để tầng application publish sau khi commit.</p>
 */
public class Person extends AggregateRoot<PersonId> {

    private final PersonId id;

    private Gender gender;
    private Integer generation;
    private Integer birthOrder;
    private LifeDate birth;
    private LifeDate death;
    private boolean alive;

    private String nativePlace;
    private String currentPlaceProvince;
    private String currentPlaceFull;
    private String occupation;
    private String biography;
    private String avatarKey;
    private ContactInfo contact;

    private final List<PersonName> names = new ArrayList<>();
    private UUID primaryBranchId;
    private LineageStatus lineageStatus;

    /**
     * <b>Ý chí riêng tư của chủ thể</b> — mỗi nhóm trường một mức độc lập. Đây là thứ duy nhất
     * {@code PrivacyTierService} hỏi tới khi lọc hồ sơ người còn sống.
     */
    private PrivacyConsent privacyConsent;

    /**
     * <b>DI SẢN, không quyết định gì.</b> Giá trị nguyên vẹn của cột {@code person.privacy_level}
     * để việc ghi lại không xoá mất dấu vết dữ liệu trước {@code V8}. Đừng đọc nó để quyết định
     * hiển thị — mọi lối như thế phải đi qua {@link #privacyConsent}.
     */
    @SuppressWarnings("deprecation")
    private PrivacyLevel legacyPrivacyLevel;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    private boolean deleted;
    private Instant deletedAt;
    private boolean anonymized;
    private Instant anonymizedAt;

    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Person(PersonId id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "PersonId khong duoc null");
        this.createdAt = createdAt;
    }

    // ---------------------------------------------------------------------------------------
    // Khởi tạo
    // ---------------------------------------------------------------------------------------

    /**
     * Tạo một nhân khẩu mới.
     *
     * <p>{@code generation} cố ý <b>không</b> nhận từ client: nó được suy ra từ quan hệ cha–con
     * ({@link #placeInGeneration(Integer)}) nên không bao giờ lệch với đồ thị. Nhân khẩu chưa nối
     * vào cây có {@code generation = null} và sẽ không xuất hiện trên phả đồ.</p>
     */
    public static Person create(PersonId id, Gender gender, boolean alive, List<PersonName> names) {
        Person person = new Person(id, Instant.now());
        person.gender = gender == null ? Gender.UNKNOWN : gender;
        person.alive = alive;
        person.lineageStatus = LineageStatus.NORMAL;
        // Mac dinh la KIN: nhan khau moi bat dau voi ca nam nhom truong o muc Rieng tu. Nguoi dung
        // chu dong mo; he thong khong tu mo ho.
        person.privacyConsent = PrivacyConsent.allPrivate();
        person.legacyPrivacyLevel = PrivacyLevel.DEFAULT;
        person.contact = ContactInfo.EMPTY;
        person.deleted = false;
        person.anonymized = false;
        person.updatedAt = person.createdAt;
        person.version = 0L;
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("Nhan khau phai co it nhat mot lop ten");
        }
        person.names.addAll(names);
        person.normalizeNames();
        person.registerEvent(new PersonAddedEvent(id.value()));
        return person;
    }

    /**
     * Dựng lại aggregate từ kho lưu trữ. Chỉ mapper của tầng infrastructure được gọi — dựng lại
     * <b>không</b> phát domain event, vì không có gì mới xảy ra trong nghiệp vụ.
     */
    public static Builder rehydrate(PersonId id) {
        return new Builder(id);
    }

    // ---------------------------------------------------------------------------------------
    // Hành vi nghiệp vụ
    // ---------------------------------------------------------------------------------------

    /**
     * <b>Xoá mềm (FR-1.5).</b> Không có và sẽ không bao giờ có đường xoá cứng: node phả hệ phải ở
     * lại đồ thị, nếu không thì mọi hậu duệ của người này mất đường nối lên tổ tiên.
     *
     * @throws IllegalStateException khi bản ghi đã ở trạng thái xoá mềm (API dịch thành 409)
     */
    public void softDelete(String reason) {
        if (deleted) {
            throw new IllegalStateException("Nhan khau da o trang thai xoa mem");
        }
        this.deleted = true;
        this.deletedAt = Instant.now();
        touch();
        registerEvent(new PersonSoftDeletedEvent(id.value(), reason));
    }

    /** Khôi phục bản ghi đã xoá mềm — chỉ Admin/Hội đồng được gọi (kiểm quyền ở application). */
    public void restore() {
        if (!deleted) {
            return;
        }
        this.deleted = false;
        this.deletedAt = null;
        touch();
        registerEvent(new PersonRestoredEvent(id.value()));
    }

    /** Thêm một lớp tên. Nếu tên mới là tên chính thì các tên còn lại tự bị hạ xuống. */
    public void addName(PersonName name) {
        Objects.requireNonNull(name, "PersonName khong duoc null");
        names.add(name);
        normalizeNames();
        touch();
        registerEvent(new PersonUpdatedEvent(id.value(), List.of("names")));
    }

    /**
     * Thay thế <b>toàn bộ</b> danh sách tên — đúng ngữ nghĩa {@code PATCH names} của contract:
     * gửi là thay hết, không phải cộng thêm.
     */
    public void replaceNames(List<PersonName> newNames) {
        if (newNames == null || newNames.isEmpty()) {
            throw new IllegalArgumentException("Nhan khau phai co it nhat mot lop ten");
        }
        names.clear();
        names.addAll(newNames);
        normalizeNames();
        touch();
        registerEvent(new PersonUpdatedEvent(id.value(), List.of("names")));
    }

    /**
     * <b>Báo mất.</b> Đặt {@code isAlive = false} kèm ngày mất song lịch.
     *
     * <p>Phần âm lịch của ngày mất là nguồn chân lý để context {@code events} sinh nhắc giỗ; cho
     * phép {@code null} vì gia phả cổ có người chỉ biết đã mất mà không còn ngày nào.</p>
     *
     * <p><b>Chỉ báo cáo thứ thực sự đổi.</b> Gọi lại trên người vốn đã mất với đúng ngày mất cũ là
     * thao tác rỗng: không {@code touch()}, không phát sự kiện, trả về danh sách rỗng. Trước đây
     * phương thức này luôn khai {@code [isAlive, death]}, khiến {@code audit_log.changed_fields}
     * nói dối về những thay đổi chưa từng xảy ra — xem {@link #markAlive()}.</p>
     *
     * @return tên các trường thực sự đổi, đi thẳng vào {@code audit_log.changed_fields}
     */
    public List<String> markDeceased(LifeDate deathDate) {
        List<String> changed = new ArrayList<>();
        if (alive) {
            this.alive = false;
            changed.add("isAlive");
        }
        if (!Objects.equals(this.death, deathDate)) {
            this.death = deathDate;
            changed.add("death");
        }
        return recordLifeStatusChange(changed);
    }

    /**
     * Đính chính: người này thật ra còn sống. Ngày mất bị gỡ để không vi phạm ràng buộc của V2.
     *
     * @return tên các trường thực sự đổi; rỗng khi người này vốn đã sống và không có ngày mất
     */
    public List<String> markAlive() {
        List<String> changed = new ArrayList<>();
        if (!alive) {
            this.alive = true;
            changed.add("isAlive");
        }
        if (this.death != null) {
            this.death = null;
            changed.add("death");
        }
        return recordLifeStatusChange(changed);
    }

    private List<String> recordLifeStatusChange(List<String> changed) {
        if (changed.isEmpty()) {
            return List.of();
        }
        touch();
        registerEvent(new PersonUpdatedEvent(id.value(), List.copyOf(changed)));
        return List.copyOf(changed);
    }

    /** Chuyển chi/ngành. Người gọi phải có quyền ở <b>cả chi cũ lẫn chi mới</b> (kiểm ở application). */
    public void moveToBranch(UUID targetBranchId) {
        UUID previous = this.primaryBranchId;
        if (Objects.equals(previous, targetBranchId)) {
            return;
        }
        this.primaryBranchId = targetBranchId;
        touch();
        registerEvent(new PersonMovedBranchEvent(id.value(), previous, targetBranchId));
    }

    /**
     * Ghi nhận đời thứ, suy ra từ quan hệ cha–con chứ không nhận từ client.
     * Truyền {@code null} khi nhân khẩu bị gỡ khỏi cây.
     */
    public void placeInGeneration(Integer value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException("Doi thu phai >= 1 (Thuy to = 1), nhan duoc: " + value);
        }
        this.generation = value;
        touch();
    }

    /** Thứ tự sinh trong các con của cùng một người cha (con cả = 1) — đầu vào của danh xưng. */
    public void placeInBirthOrder(Integer value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException("Thu tu sinh phai >= 1, nhan duoc: " + value);
        }
        this.birthOrder = value;
        touch();
    }

    public void changeLineageStatus(LineageStatus status) {
        this.lineageStatus = status == null ? LineageStatus.NORMAL : status;
        touch();
    }

    /**
     * <b>Đặt lại bản đồng thuận riêng tư</b> — ý chí của chính chủ thể, từng nhóm trường một.
     *
     * <p>Admin không siết hộ và cũng không nới hộ. {@code null} được hiểu là "về mặc định", mà mặc
     * định của mô hình này là <b>kín hoàn toàn</b> chứ không phải một mức trung dung nào đó.</p>
     */
    public void choosePrivacyConsent(PrivacyConsent consent) {
        PrivacyConsent resolved = consent == null ? PrivacyConsent.allPrivate() : consent;
        if (resolved.equals(this.privacyConsent)) {
            return;
        }
        this.privacyConsent = resolved;
        touch();
        registerEvent(new PersonUpdatedEvent(id.value(), List.of("privacyConsent")));
    }

    /** Đổi mức chia sẻ của <b>một</b> nhóm trường, giữ nguyên bốn nhóm còn lại. */
    public void choosePrivacyScope(PrivacyFieldGroup group, ShareScope scope) {
        choosePrivacyConsent(privacyConsent().with(group, scope));
    }

    /**
     * Áp một lô thay đổi hồ sơ đã được tầng application diễn giải xong ngữ nghĩa
     * "vắng mặt = giữ nguyên / có mặt = ghi đè / {@code clearFields} = xoá trắng".
     *
     * <p>Domain nhận vào thứ đã rõ ràng ({@link ProfileEdit} với mỗi trường là một
     * {@link FieldChange}) để không phải tự đoán ý nghĩa của {@code null} — đúng lý do contract
     * chọn {@code clearFields} thay vì {@code null}.</p>
     *
     * @return danh sách tên trường thực sự đổi — đi thẳng vào {@code audit_log.changed_fields}
     */
    public List<String> applyProfileEdit(ProfileEdit edit) {
        Objects.requireNonNull(edit, "ProfileEdit khong duoc null");
        List<String> changed = new ArrayList<>();

        if (edit.gender().present()) {
            Gender resolved = edit.gender().value() == null ? Gender.UNKNOWN : edit.gender().value();
            if (resolved != this.gender) {
                this.gender = resolved;
                changed.add("gender");
            }
        }
        changed.addAll(applyText("nativePlace", edit.nativePlace(),
                v -> this.nativePlace = v, () -> this.nativePlace));
        changed.addAll(applyText("currentPlaceProvince", edit.currentPlaceProvince(),
                v -> this.currentPlaceProvince = v, () -> this.currentPlaceProvince));
        changed.addAll(applyText("currentPlaceFull", edit.currentPlaceFull(),
                v -> this.currentPlaceFull = v, () -> this.currentPlaceFull));
        changed.addAll(applyText("occupation", edit.occupation(),
                v -> this.occupation = v, () -> this.occupation));
        changed.addAll(applyText("biography", edit.biography(),
                v -> this.biography = v, () -> this.biography));
        changed.addAll(applyText("avatarKey", edit.avatarKey(),
                v -> this.avatarKey = v, () -> this.avatarKey));

        if (edit.birth().present()) {
            this.birth = edit.birth().value();
            changed.add("birth");
        }
        if (edit.contact().present()) {
            this.contact = edit.contact().value() == null ? ContactInfo.EMPTY : edit.contact().value();
            changed.add("contact");
        }
        if (edit.attributes().present()) {
            this.attributes.clear();
            if (edit.attributes().value() != null) {
                this.attributes.putAll(edit.attributes().value());
            }
            changed.add("attributes");
        }
        if (!changed.isEmpty()) {
            touch();
            registerEvent(new PersonUpdatedEvent(id.value(), List.copyOf(changed)));
        }
        return changed;
    }

    /**
     * <b>Ẩn danh hoá (Nghị định 13/2023 — quyền xoá dữ liệu cá nhân).</b> Xoá sạch dữ liệu Tầng 3
     * nhưng <b>giữ nguyên node phả hệ</b>: tên chính, đời thứ, giới tính và mọi cạnh quan hệ ở lại
     * để cây không gãy.
     *
     * <p>Nghĩa vụ pháp lý này chưa có endpoint trong contract Sprint 1 (xem
     * {@code contracts/README.md} mục "Việc còn treo"); hành vi được hiện thực sẵn ở domain để khi
     * endpoint được chốt thì chỉ còn việc gọi.</p>
     */
    public void anonymize() {
        this.contact = ContactInfo.EMPTY;
        this.currentPlaceFull = null;
        this.currentPlaceProvince = null;
        this.occupation = null;
        this.biography = null;
        this.avatarKey = null;
        this.attributes.clear();
        this.birth = this.birth == null ? null : this.birth.coarsenToYear();
        this.names.removeIf(name -> !name.primary());
        this.anonymized = true;
        this.anonymizedAt = Instant.now();
        // Xoa du lieu ma khong dong lai muc chia se thi lan nhap lieu sau se lap tuc mo lai dung
        // nhung truong vua duoc xoa theo yeu cau hop phap.
        this.privacyConsent = PrivacyConsent.allPrivate();
        touch();
        registerEvent(new PersonAnonymizedEvent(id.value()));
    }

    // ---------------------------------------------------------------------------------------
    // Truy vấn
    // ---------------------------------------------------------------------------------------

    @Override
    public PersonId id() {
        return id;
    }

    public UUID rawId() {
        return id.value();
    }

    public Gender gender() {
        return gender;
    }

    public Integer generation() {
        return generation;
    }

    public Integer birthOrder() {
        return birthOrder;
    }

    public LifeDate birth() {
        return birth;
    }

    public LifeDate death() {
        return death;
    }

    public boolean isAlive() {
        return alive;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public boolean isAnonymized() {
        return anonymized;
    }

    public Instant anonymizedAt() {
        return anonymizedAt;
    }

    public String nativePlace() {
        return nativePlace;
    }

    public String currentPlaceProvince() {
        return currentPlaceProvince;
    }

    public String currentPlaceFull() {
        return currentPlaceFull;
    }

    public String occupation() {
        return occupation;
    }

    public String biography() {
        return biography;
    }

    public String avatarKey() {
        return avatarKey;
    }

    public ContactInfo contact() {
        return contact == null ? ContactInfo.EMPTY : contact;
    }

    public List<PersonName> names() {
        return Collections.unmodifiableList(names);
    }

    public UUID primaryBranchId() {
        return primaryBranchId;
    }

    public LineageStatus lineageStatus() {
        return lineageStatus;
    }

    /** Bản đồng thuận riêng tư; không bao giờ {@code null}. */
    public PrivacyConsent privacyConsent() {
        return privacyConsent == null ? PrivacyConsent.allPrivate() : privacyConsent;
    }

    /**
     * Giá trị di sản của cột {@code person.privacy_level}. <b>Chỉ để adapter ghi lại nguyên vẹn.</b>
     *
     * @deprecated không dùng để quyết định hiển thị — xem {@link #privacyConsent()}
     */
    @Deprecated(since = "V8", forRemoval = true)
    public PrivacyLevel legacyPrivacyLevel() {
        return legacyPrivacyLevel == null ? PrivacyLevel.DEFAULT : legacyPrivacyLevel;
    }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    /** Tên hiển thị: tên có cờ chính; luôn tồn tại nhờ bất biến "đúng một tên chính". */
    public String displayName() {
        return primaryName().map(PersonName::fullName).orElse("");
    }

    public Optional<PersonName> primaryName() {
        return names.stream().filter(PersonName::primary).findFirst();
    }

    /** Các tên húy của người này — dữ liệu đối chiếu cho cảnh báo kỵ húy của người khác. */
    public List<PersonName> tabooNames() {
        return names.stream().filter(PersonName::isTabooName).toList();
    }

    /**
     * Ảnh chụp phục vụ {@code audit_log}. <b>Cố ý bỏ toàn bộ dữ liệu Tầng 3</b> (liên hệ, địa chỉ
     * đầy đủ, tiểu sử, ảnh, thuộc tính mở rộng, ngày sinh đầy đủ): nhật ký thay đổi không được trở
     * thành một bản sao không kiểm soát của dữ liệu nhạy cảm.
     */
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", id.value().toString());
        snapshot.put("displayName", displayName());
        snapshot.put("gender", gender == null ? null : gender.name());
        snapshot.put("generation", generation);
        snapshot.put("birthOrder", birthOrder);
        snapshot.put("isAlive", alive);
        snapshot.put("isDeleted", deleted);
        snapshot.put("birthYear", birth == null ? null : birth.year().orElse(null));
        snapshot.put("deathYear", death == null ? null : death.year().orElse(null));
        snapshot.put("nativePlace", nativePlace);
        snapshot.put("primaryBranchId", primaryBranchId == null ? null : primaryBranchId.toString());
        snapshot.put("lineageStatus", lineageStatus == null ? null : lineageStatus.name());
        // Chi ghi MUC chia se, khong ghi gia tri truong nao — nhat ky khong duoc thanh ban sao
        // khong kiem soat cua du lieu Tang 3.
        snapshot.put("privacyConsent", privacyConsent().toJson());
        snapshot.put("names", names.stream()
                .map(n -> Map.<String, Object>of(
                        "type", n.type().name(),
                        "fullName", n.fullName(),
                        "isPrimary", n.primary()))
                .toList());
        return snapshot;
    }

    // ---------------------------------------------------------------------------------------
    // Nội bộ
    // ---------------------------------------------------------------------------------------

    /**
     * Ép bất biến "đúng một tên chính": không tên nào được đánh dấu thì lấy tên đầu tiên; nhiều
     * tên được đánh dấu thì giữ tên đầu tiên và hạ phần còn lại.
     *
     * <p>Chỉ mục {@code ux_person_name_primary} của V2 cũng bắt điều này, nhưng bắt ở domain thì
     * kết quả là dữ liệu đã đúng, không phải một {@code SQLException} lúc flush.</p>
     */
    private void normalizeNames() {
        boolean seenPrimary = false;
        for (int i = 0; i < names.size(); i++) {
            PersonName name = names.get(i);
            if (name.primary() && !seenPrimary) {
                seenPrimary = true;
            } else if (name.primary()) {
                names.set(i, name.asPrimary(false));
            }
        }
        if (!seenPrimary && !names.isEmpty()) {
            names.set(0, names.get(0).asPrimary(true));
        }
    }

    private List<String> applyText(String field, FieldChange<String> change,
                                   Consumer<String> setter, Supplier<String> getter) {
        if (!change.present()) {
            return List.of();
        }
        String value = change.value();
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (Objects.equals(normalized, getter.get())) {
            return List.of();
        }
        setter.accept(normalized);
        return List.of(field);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Bộ dựng dùng <b>duy nhất</b> cho việc nạp lại aggregate từ CSDL. Cố ý tách khỏi
     * {@link #create} để không ai dùng nhầm nó như một tập setter công khai.
     */
    public static final class Builder {

        private final Person person;

        private Builder(PersonId id) {
            this.person = new Person(id, Instant.now());
            this.person.gender = Gender.UNKNOWN;
            this.person.alive = true;
            this.person.lineageStatus = LineageStatus.NORMAL;
            this.person.privacyConsent = PrivacyConsent.allPrivate();
            this.person.legacyPrivacyLevel = PrivacyLevel.DEFAULT;
            this.person.contact = ContactInfo.EMPTY;
        }

        public Builder gender(Gender value) {
            person.gender = value == null ? Gender.UNKNOWN : value;
            return this;
        }

        public Builder generation(Integer value) {
            person.generation = value;
            return this;
        }

        public Builder birthOrder(Integer value) {
            person.birthOrder = value;
            return this;
        }

        public Builder birth(LifeDate value) {
            person.birth = value;
            return this;
        }

        public Builder death(LifeDate value) {
            person.death = value;
            return this;
        }

        public Builder alive(boolean value) {
            person.alive = value;
            return this;
        }

        public Builder nativePlace(String value) {
            person.nativePlace = value;
            return this;
        }

        public Builder currentPlaceProvince(String value) {
            person.currentPlaceProvince = value;
            return this;
        }

        public Builder currentPlaceFull(String value) {
            person.currentPlaceFull = value;
            return this;
        }

        public Builder occupation(String value) {
            person.occupation = value;
            return this;
        }

        public Builder biography(String value) {
            person.biography = value;
            return this;
        }

        public Builder avatarKey(String value) {
            person.avatarKey = value;
            return this;
        }

        public Builder contact(ContactInfo value) {
            person.contact = value == null ? ContactInfo.EMPTY : value;
            return this;
        }

        public Builder names(List<PersonName> value) {
            person.names.clear();
            if (value != null) {
                person.names.addAll(value);
            }
            return this;
        }

        public Builder primaryBranchId(UUID value) {
            person.primaryBranchId = value;
            return this;
        }

        public Builder lineageStatus(LineageStatus value) {
            person.lineageStatus = value == null ? LineageStatus.NORMAL : value;
            return this;
        }

        /**
         * Bản đồng thuận đọc từ cột {@code privacy_consent}. {@code null} ⇒ kín hoàn toàn: một hàng
         * chưa có dữ liệu đồng thuận không được suy diễn thành mức mở nào.
         */
        public Builder privacyConsent(PrivacyConsent value) {
            person.privacyConsent = value == null ? PrivacyConsent.allPrivate() : value;
            return this;
        }

        /** Giá trị di sản của cột {@code privacy_level}, giữ nguyên để ghi lại. */
        @Deprecated(since = "V8", forRemoval = true)
        public Builder legacyPrivacyLevel(PrivacyLevel value) {
            person.legacyPrivacyLevel = value == null ? PrivacyLevel.DEFAULT : value;
            return this;
        }

        public Builder attributes(Map<String, Object> value) {
            person.attributes.clear();
            if (value != null) {
                person.attributes.putAll(value);
            }
            return this;
        }

        public Builder deleted(boolean value, Instant at) {
            person.deleted = value;
            person.deletedAt = at;
            return this;
        }

        public Builder anonymized(boolean value, Instant at) {
            person.anonymized = value;
            person.anonymizedAt = at;
            return this;
        }

        public Builder updatedAt(Instant value) {
            person.updatedAt = value;
            return this;
        }

        public Builder version(long value) {
            person.version = value;
            return this;
        }

        public Person build() {
            person.normalizeNames();
            person.clearDomainEvents();
            return person;
        }
    }
}
