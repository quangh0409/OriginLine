package vn.giapha.genealogy.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code person_name} — tên đa lớp (húy / tự / hiệu / thụy / thường gọi / pháp danh).
 *
 * <p>{@code name_unaccented} và {@code name_tsv} là <b>generated column</b> của Postgres (V6):
 * ánh xạ {@code insertable = false, updatable = false} để Hibernate đọc mà không bao giờ ghi.
 * Ghi tay vào chúng là mở đường cho bản không dấu lệch khỏi tên gốc, và lệch kiểu đó thì tìm kiếm
 * sai âm thầm chứ không văng lỗi.</p>
 */
@Entity
@Table(name = "person_name")
public class PersonNameJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "name_type", nullable = false, length = 16)
    private String nameType;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "name_hannom", length = 255)
    private String nameHanNom;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "note")
    private String note;

    @Column(name = "name_unaccented", insertable = false, updatable = false, length = 255)
    private String nameUnaccented;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PersonNameJpaEntity() {
    }

    public PersonNameJpaEntity(UUID id, UUID personId) {
        this.id = id;
        this.personId = personId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public String getNameType() {
        return nameType;
    }

    public void setNameType(String nameType) {
        this.nameType = nameType;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNameHanNom() {
        return nameHanNom;
    }

    public void setNameHanNom(String nameHanNom) {
        this.nameHanNom = nameHanNom;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getNameUnaccented() {
        return nameUnaccented;
    }

    public long getVersion() {
        return version;
    }
}
