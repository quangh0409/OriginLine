package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * <b>Canh cái bẫy đã bị gỡ, để nó không mọc lại.</b>
 *
 * <p>{@code VisibleTier} từng có {@code atLeast(VisibleTier)} trả {@code true} cho <b>mọi</b> so
 * sánh khi tier là {@code PUBLIC}. Người đã khuất luôn ở {@code PUBLIC}, nên
 * {@code tier.atLeast(T3)} — cách viết tự nhiên nhất của câu hỏi "được xem dữ liệu Tầng 3 không" —
 * mở khối liên hệ trong hồ sơ cụ tổ cho cả Khách vãng lai, mà số điện thoại ghi ở đó thực chất là
 * số của người thân đang sống.</p>
 *
 * <p>Cách sửa không phải là "nhớ so bằng chính xác" — nhớ được một lần chứ không nhớ được mười
 * lần. Phương thức đã bị <b>xoá</b>, và quyết định chuyển sang {@link PersonVisibility#allows}
 * theo từng nhóm trường. Test này khoá cả hai đầu: enum không được có lại phép so tầng, và
 * {@code allows} phải trả lời đúng cho đúng ca đã từng thủng.</p>
 */
class VisibleTierTest {

    @Test
    @DisplayName("VisibleTier KHÔNG được có lại bất kỳ phép so tầng nào")
    void khongDuocCoLaiPhepSoTang() {
        Method[] methods = VisibleTier.class.getDeclaredMethods();

        assertThat(Arrays.stream(methods).map(Method::getName))
                .as("them lai atLeast/isAtLeast/covers... la dung lai cai bay da go — "
                        + "cau hoi 'duoc xem nhom truong nao' thuoc ve PersonVisibility.allows()")
                .doesNotContain("atLeast", "isAtLeast", "covers", "includes", "gte");
    }

    @Test
    @DisplayName("Enum chỉ còn là nhãn tóm tắt: đúng bốn giá trị, không hành vi kèm theo")
    void chiConLaNhanTomTat() {
        assertThat(VisibleTier.values())
                .containsExactly(VisibleTier.PUBLIC, VisibleTier.T1, VisibleTier.T2, VisibleTier.T3);
    }

    @Test
    @DisplayName("Ca từng thủng: Khách xem hồ sơ người đã khuất KHÔNG được đọc khối liên hệ")
    void khachKhongDocDuocLienHeCuaNguoiDaKhuat() {
        // deceased = true, khong phai chinh chu, khong clan-wide, khong cung chi — dung Khach.
        PersonVisibility khach = new PersonVisibility(true, false, false, false, false, false,
                PrivacyConsent.allPrivate());

        assertThat(khach.tier()).isEqualTo(VisibleTier.PUBLIC);
        assertThat(khach.allows(PrivacyFieldGroup.CONTACT))
                .as("day chinh la o ma PUBLIC.atLeast(T3) tra true va lam thung")
                .isFalse();
        assertThat(khach.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO))
                .as("phan con lai cua ho so nguoi da khuat van cong khai")
                .isTrue();
    }

    @Test
    @DisplayName("Người đã khuất không bị mô hình đồng thuận áp lên, trừ khối liên hệ")
    void dongThuanKhongApLenNguoiDaKhuat() {
        PersonVisibility vis = new PersonVisibility(true, false, false, false, false, false,
                PrivacyConsent.allPrivate().with(PrivacyFieldGroup.CONTACT, ShareScope.CLAN));

        assertThat(vis.allows(PrivacyFieldGroup.OCCUPATION)).isTrue();
        assertThat(vis.allows(PrivacyFieldGroup.CONTACT))
                .as("mot lua chon tu luc con song khong mo lai duoc khoi lien he sau khi mat")
                .isFalse();
    }

    @Test
    @DisplayName("Lỗ hổng NT-MOI-07: tài khoản CHƯA VÀO PHẢ không được hưởng mức \"Cả họ xem\"")
    void chuaVaoPhaKhongDocDuocMucCaHo() {
        // Người còn sống, đặt cả năm nhóm ở CLAN — tức rộng nhất mà chủ thể có thể chọn.
        PrivacyConsent moCaHo = PrivacyConsent.allPrivate();
        for (PrivacyFieldGroup g : PrivacyFieldGroup.values()) {
            moCaHo = moCaHo.with(g, ShareScope.CLAN);
        }
        // Đã đăng nhập (qua existsFor), không phải chính chủ, không clan-wide, không cùng chi,
        // và linkedToTree = false: đúng hình dạng của một tài khoản vừa đăng ký bằng mã dòng họ.
        PersonVisibility choDuyet = new PersonVisibility(false, false, false, false, false, false, moCaHo);

        for (PrivacyFieldGroup g : PrivacyFieldGroup.values()) {
            assertThat(choDuyet.allows(g))
                    .as("mã mời dòng họ phát rộng; 'cầm được mã' chưa phải 'là người trong họ' — nhóm " + g)
                    .isFalse();
        }
        assertThat(choDuyet.tier())
                .as("màn chờ duyệt hứa: chỉ tên, đời, quan hệ lõi")
                .isEqualTo(VisibleTier.T1);

        // Đối chứng: cùng hồ sơ ấy, một thành viên đã vào phả (khác chi) đọc được đủ.
        PersonVisibility thanhVien = new PersonVisibility(false, false, false, false, true, false, moCaHo);
        assertThat(thanhVien.allows(PrivacyFieldGroup.CONTACT)).isTrue();
        assertThat(thanhVien.tier()).isEqualTo(VisibleTier.T3);
    }

    @Test
    @DisplayName("Mức BRANCH cũng đòi đã vào phả, không chỉ đòi cùng chi")
    void mucChiCungDoiDaVaoPha() {
        PrivacyConsent moChi = PrivacyConsent.allPrivate().with(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH);
        // inBranchScope = true nhưng linkedToTree = false: hình dạng không nên xảy ra ngoài đời
        // (chưa gắn nhân khẩu thì không có chi để "cùng"), nhưng nếu một lối vào nào đó dựng ra
        // được nó thì luật vẫn phải đóng.
        PersonVisibility laLung = new PersonVisibility(false, false, false, true, false, false, moChi);
        assertThat(laLung.allows(PrivacyFieldGroup.CONTACT)).isFalse();
    }
}
