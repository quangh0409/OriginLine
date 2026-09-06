package vn.giapha.genealogy.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.application.command.UpdatePersonCommand;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.shared.vo.Gender;

/**
 * Bộ dựng {@link UpdatePersonCommand} cho test: mặc định <b>mọi trường đều "giữ nguyên"</b>, đúng
 * ngữ nghĩa của một lệnh {@code PATCH} rỗng. Chỉ trường nào test nói tới mới có mặt.
 */
public final class UpdatePersonCommands {

    private final UUID personId;
    private Long expectedVersion;
    private FieldChange<List<PersonName>> names = FieldChange.keep();
    private FieldChange<Gender> gender = FieldChange.keep();
    private FieldChange<Boolean> alive = FieldChange.keep();
    private FieldChange<Boolean> deleted = FieldChange.keep();
    private FieldChange<LifeDate> birth = FieldChange.keep();
    private FieldChange<LifeDate> death = FieldChange.keep();
    private FieldChange<String> nativePlace = FieldChange.keep();
    private FieldChange<String> currentPlaceProvince = FieldChange.keep();
    private FieldChange<String> currentPlaceFull = FieldChange.keep();
    private FieldChange<String> occupation = FieldChange.keep();
    private FieldChange<String> biography = FieldChange.keep();
    private FieldChange<String> avatarKey = FieldChange.keep();
    private FieldChange<UUID> primaryBranchId = FieldChange.keep();
    private FieldChange<ContactInfo> contact = FieldChange.keep();
    private FieldChange<Map<String, Object>> attributes = FieldChange.keep();
    private FieldChange<PrivacyLevel> privacyLevel = FieldChange.keep();
    private boolean confirmTabooOverride;
    private String note;

    private UpdatePersonCommands(UUID personId) {
        this.personId = personId;
    }

    public static UpdatePersonCommands cho(UUID personId) {
        return new UpdatePersonCommands(personId);
    }

    public UpdatePersonCommands phienBan(Long value) {
        this.expectedVersion = value;
        return this;
    }

    public UpdatePersonCommands ten(List<PersonName> value) {
        this.names = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands gioiTinh(Gender value) {
        this.gender = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands conSong(Boolean value) {
        this.alive = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands daXoa(Boolean value) {
        this.deleted = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands ngaySinh(LifeDate value) {
        this.birth = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands ngayMat(LifeDate value) {
        this.death = FieldChange.set(value);
        return this;
    }

    /** {@code clearFields: ["death"]} — gỡ ngày mất ghi sai, KHÔNG có nghĩa là "cụ sống lại". */
    public UpdatePersonCommands xoaTrangNgayMat() {
        this.death = FieldChange.clear();
        return this;
    }

    public UpdatePersonCommands nguyenQuan(String value) {
        this.nativePlace = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands ngheNghiep(String value) {
        this.occupation = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands xoaTrangNgheNghiep() {
        this.occupation = FieldChange.clear();
        return this;
    }

    public UpdatePersonCommands lienHe(ContactInfo value) {
        this.contact = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands chi(UUID value) {
        this.primaryBranchId = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands mucRiengTu(PrivacyLevel value) {
        this.privacyLevel = FieldChange.set(value);
        return this;
    }

    public UpdatePersonCommands xacNhanKyHuy() {
        this.confirmTabooOverride = true;
        return this;
    }

    public UpdatePersonCommands ghiChu(String value) {
        this.note = value;
        return this;
    }

    public UpdatePersonCommand build() {
        return new UpdatePersonCommand(personId, expectedVersion, names, gender, alive, deleted,
                birth, death, nativePlace, currentPlaceProvince, currentPlaceFull, occupation,
                biography, avatarKey, primaryBranchId, contact, attributes, privacyLevel,
                confirmTabooOverride, note);
    }
}
