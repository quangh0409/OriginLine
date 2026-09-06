package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Vai trò <b>kỹ thuật</b> — nửa thứ nhất của phân quyền.
 *
 * <p>Nửa thứ hai (phạm vi chi/ngành theo {@code ltree}) được kiểm ở {@link MemberScopeTest}. Tách
 * đôi có chủ ý: mọi lỗi phân quyền của dự án này đều bắt nguồn từ việc ai đó tưởng nửa thứ nhất là
 * toàn bộ bài toán.</p>
 */
class RoleCodeTest {

    @Nested
    @DisplayName("Thứ bậc vai trò")
    class ThuBac {

        @Test
        @DisplayName("Xếp đúng: Quản trị > Hội đồng Tộc biểu > Trưởng chi > Thành viên > Khách")
        void thuBacGiamDan() {
            assertThat(RoleCode.ADMIN.rank()).isGreaterThan(RoleCode.COUNCIL.rank());
            assertThat(RoleCode.COUNCIL.rank()).isGreaterThan(RoleCode.BRANCH_HEAD.rank());
            assertThat(RoleCode.BRANCH_HEAD.rank()).isGreaterThan(RoleCode.MEMBER.rank());
            assertThat(RoleCode.MEMBER.rank()).isGreaterThan(RoleCode.GUEST.rank());
        }

        @Test
        @DisplayName("Chỉ Quản trị và Hội đồng Tộc biểu có phạm vi toàn dòng họ")
        void chiHaiVaiToanDongHo() {
            assertThat(RoleCode.ADMIN.isClanWide()).isTrue();
            assertThat(RoleCode.COUNCIL.isClanWide()).isTrue();
            // Truong Chi/Nganh KHONG toan dong ho, du xep tren Thanh vien.
            assertThat(RoleCode.BRANCH_HEAD.isClanWide()).isFalse();
            assertThat(RoleCode.MEMBER.isClanWide()).isFalse();
            assertThat(RoleCode.GUEST.isClanWide()).isFalse();
        }

        @Test
        @DisplayName("Thành viên và Khách không duyệt được yêu cầu đính chính")
        void quyenDuyet() {
            assertThat(RoleCode.ADMIN.canReview()).isTrue();
            assertThat(RoleCode.COUNCIL.canReview()).isTrue();
            assertThat(RoleCode.BRANCH_HEAD.canReview()).isTrue();
            assertThat(RoleCode.MEMBER.canReview()).isFalse();
            assertThat(RoleCode.GUEST.canReview()).isFalse();
        }
    }

    @Nested
    @DisplayName("Đọc vai từ token")
    class DocTuToken {

        @Test
        @DisplayName("Tập vai rỗng cho ra Khách, KHÔNG phải Thành viên")
        void tapRongLaKhach() {
            // Mac dinh phai la vai HEP nhat. Doi thanh MEMBER o day la mo cho moi token khong
            // mang vai nao deu thanh thanh vien da dang nhap.
            assertThat(RoleCode.broadest(List.of())).isEqualTo(RoleCode.GUEST);
            assertThat(RoleCode.broadest(null)).isEqualTo(RoleCode.GUEST);
        }

        @Test
        @DisplayName("Nhiều vai thì lấy vai rộng nhất")
        void layVaiRongNhat() {
            assertThat(RoleCode.broadest(Set.of("MEMBER", "BRANCH_HEAD")))
                    .isEqualTo(RoleCode.BRANCH_HEAD);
            assertThat(RoleCode.broadest(Set.of("MEMBER", "COUNCIL", "BRANCH_HEAD")))
                    .isEqualTo(RoleCode.COUNCIL);
        }

        @Test
        @DisplayName("Vai lạ trong token bị bỏ qua, không làm hỏng request")
        void vaiLaBiBoQua() {
            // Mot realm Keycloak dung chung co the mang theo vai cua ung dung khac.
            assertThat(RoleCode.broadest(Set.of("offline_access", "uma_authorization", "MEMBER")))
                    .isEqualTo(RoleCode.MEMBER);
            assertThat(RoleCode.broadest(Set.of("offline_access"))).isEqualTo(RoleCode.GUEST);
            assertThat(RoleCode.parse("khong_ton_tai")).isNull();
            assertThat(RoleCode.parse(null)).isNull();
            assertThat(RoleCode.parse("   ")).isNull();
        }

        @Test
        @DisplayName("Chuẩn hoá hoa/thường và gạch ngang")
        void chuanHoaChuoi() {
            assertThat(RoleCode.parse("branch-head")).isEqualTo(RoleCode.BRANCH_HEAD);
            assertThat(RoleCode.parse(" Council ")).isEqualTo(RoleCode.COUNCIL);
        }
    }

    @Test
    @DisplayName("Chức danh dòng tộc KHÔNG nằm trong danh sách vai kỹ thuật")
    void chucDanhDongTocTachBiet() {
        // Toc truong, Truong chi (theo huyet thong/dich ton) la du kien cua bang `branch`
        // (`head_person_id`), khong phai vai he thong. Neu mot ngay nao do co ai them
        // RoleCode.TOC_TRUONG thi test nay do — va do la dieu dung.
        assertThat(RoleCode.values()).containsExactlyInAnyOrder(
                RoleCode.ADMIN, RoleCode.COUNCIL, RoleCode.BRANCH_HEAD,
                RoleCode.MEMBER, RoleCode.GUEST);
    }
}
