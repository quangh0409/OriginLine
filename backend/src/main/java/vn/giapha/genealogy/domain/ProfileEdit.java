package vn.giapha.genealogy.domain;

import java.util.Map;
import vn.giapha.shared.vo.Gender;

/**
 * Lô thay đổi hồ sơ đã được tầng application diễn giải xong, sẵn sàng để
 * {@link Person#applyProfileEdit(ProfileEdit)} áp vào aggregate.
 *
 * <p>Cố ý <b>không</b> chứa {@code isAlive}, {@code death}, {@code names}, {@code primaryBranchId},
 * {@code privacyLevel} hay {@code isDeleted}: mỗi thứ đó là một hành vi nghiệp vụ riêng có tên
 * ({@code markDeceased}, {@code replaceNames}, {@code moveToBranch}, {@code choosePrivacyLevel},
 * {@code softDelete}) và có quy tắc phân quyền riêng. Gộp chúng vào một lô là cách nhanh nhất để
 * mất luôn các quy tắc đó.</p>
 */
public record ProfileEdit(FieldChange<Gender> gender,
                          FieldChange<LifeDate> birth,
                          FieldChange<String> nativePlace,
                          FieldChange<String> currentPlaceProvince,
                          FieldChange<String> currentPlaceFull,
                          FieldChange<String> occupation,
                          FieldChange<String> biography,
                          FieldChange<String> avatarKey,
                          FieldChange<ContactInfo> contact,
                          FieldChange<Map<String, Object>> attributes) {

    public static Builder builder() {
        return new Builder();
    }

    /** Bộ dựng mặc định "giữ nguyên tất cả" — chỉ trường nào được nói tới mới đổi. */
    public static final class Builder {

        private FieldChange<Gender> gender = FieldChange.keep();
        private FieldChange<LifeDate> birth = FieldChange.keep();
        private FieldChange<String> nativePlace = FieldChange.keep();
        private FieldChange<String> currentPlaceProvince = FieldChange.keep();
        private FieldChange<String> currentPlaceFull = FieldChange.keep();
        private FieldChange<String> occupation = FieldChange.keep();
        private FieldChange<String> biography = FieldChange.keep();
        private FieldChange<String> avatarKey = FieldChange.keep();
        private FieldChange<ContactInfo> contact = FieldChange.keep();
        private FieldChange<Map<String, Object>> attributes = FieldChange.keep();

        public Builder gender(FieldChange<Gender> value) {
            this.gender = value;
            return this;
        }

        public Builder birth(FieldChange<LifeDate> value) {
            this.birth = value;
            return this;
        }

        public Builder nativePlace(FieldChange<String> value) {
            this.nativePlace = value;
            return this;
        }

        public Builder currentPlaceProvince(FieldChange<String> value) {
            this.currentPlaceProvince = value;
            return this;
        }

        public Builder currentPlaceFull(FieldChange<String> value) {
            this.currentPlaceFull = value;
            return this;
        }

        public Builder occupation(FieldChange<String> value) {
            this.occupation = value;
            return this;
        }

        public Builder biography(FieldChange<String> value) {
            this.biography = value;
            return this;
        }

        public Builder avatarKey(FieldChange<String> value) {
            this.avatarKey = value;
            return this;
        }

        public Builder contact(FieldChange<ContactInfo> value) {
            this.contact = value;
            return this;
        }

        public Builder attributes(FieldChange<Map<String, Object>> value) {
            this.attributes = value;
            return this;
        }

        public ProfileEdit build() {
            return new ProfileEdit(gender, birth, nativePlace, currentPlaceProvince, currentPlaceFull,
                    occupation, biography, avatarKey, contact, attributes);
        }
    }
}
