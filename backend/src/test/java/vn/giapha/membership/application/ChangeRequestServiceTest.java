package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.application.command.ReviewChangeRequestCommand;
import vn.giapha.membership.application.command.SubmitChangeRequestCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.ChangeRequestStatus;
import vn.giapha.membership.domain.ChangeRequestType;
import vn.giapha.membership.domain.event.ChangeRequestApprovedEvent;
import vn.giapha.membership.domain.event.ChangeRequestRejectedEvent;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Luồng đề nghị – duyệt đính chính: gửi → duyệt/từ chối → phát sự kiện áp dụng.
 *
 * <p>Trọng tâm là <b>cửa kiểm quyền duyệt</b>: nó phải soi {@code ltree}, không chỉ soi vai. Ca
 * "Trưởng Chi Ất duyệt yêu cầu nhắm vào Chi Giáp" là ca phải đỏ nếu ai đó lỡ nới luật.</p>
 */
class ChangeRequestServiceTest {

    private final ClanFixture clan = new ClanFixture();
    private final ChangeRequestService service = new ChangeRequestService(
            clan.requests, clan.scopes, clan.guard, clan.branches, clan.auditTrail, clan.events);

    private UUID nguoiChiGiap;
    private UUID nguoiChiAt;

    @AfterEach
    void donSecurityContext() {
        TestSecurity.logout();
    }

    /** Một thành viên thường của Chi Giáp, đã đăng nhập. */
    private AppUser thanhVienChiGiap(String sub) {
        AppUser user = clan.account(sub, clan.chiGiapId);
        TestSecurity.loginAs(sub, "MEMBER");
        return user;
    }

    private ChangeRequestView guiYeuCauSuaNguoiChiGiap(String sub) {
        thanhVienChiGiap(sub);
        nguoiChiGiap = clan.personIn(clan.chiGiapId);
        return service.submit(new SubmitChangeRequestCommand(ChangeRequestType.UPDATE_PERSON,
                nguoiChiGiap, null, Map.of("deathLunar", "15/07"), "Ngày giỗ ghi sai"));
    }

    @Nested
    @DisplayName("Gửi yêu cầu")
    class Gui {

        @Test
        @DisplayName("Mọi thành viên có tài khoản đều gửi được, KHÔNG kiểm phạm vi ở bước này")
        void thanhVienNgoaiChiVanGuiDuoc() {
            // Mot nguoi con gai da lay chong xa van phai bao duoc rang ngay mat cua cu ghi sai, du
            // chi cua cu khong phai chi co ay dang sinh hoat. Cua kiem la o buoc duyet.
            clan.account("sub-con-gai-lay-chong-xa", clan.chiAtId);
            TestSecurity.loginAs("sub-con-gai-lay-chong-xa", "MEMBER");
            nguoiChiGiap = clan.personIn(clan.chiGiapId);

            ChangeRequestView view = service.submit(new SubmitChangeRequestCommand(
                    ChangeRequestType.UPDATE_PERSON, nguoiChiGiap, null,
                    Map.of("deathLunar", "15/07"), "Ngày giỗ cụ ghi sai một tháng"));

            assertThat(view.status()).isEqualTo("PENDING");
            assertThat(view.targetBranchId()).isEqualTo(clan.chiGiapId);
        }

        @Test
        @DisplayName("Chi đích được chốt ngay lúc gửi, suy từ chi chính của nhân khẩu")
        void chotChiDichLucGui() {
            ChangeRequestView view = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            assertThat(view.targetBranchId()).isEqualTo(clan.chiGiapId);
            assertThat(view.personId()).isEqualTo(nguoiChiGiap);
        }

        @Test
        @DisplayName("Khách không gửi được — ACCOUNT_NOT_PROVISIONED")
        void khachKhongGui() {
            TestSecurity.logout();

            assertThatThrownBy(() -> service.submit(new SubmitChangeRequestCommand(
                    ChangeRequestType.UPDATE_PERSON, UUID.randomUUID(), null, Map.of(), null)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED);
        }

        @Test
        @DisplayName("Đề nghị XOÁ một trường (payload mang null) đi trọn luồng gửi rồi duyệt")
        void deNghiXoaMotTruong() {
            // "Bo ngay mat ghi nham" la mot de nghi dinh chinh hop le, va no gui len JSON null.
            // Neu bat ky chang nao tren duong di dung Map.copyOf thi ca luong vo bang NPE — mot loi
            // 500 cho mot thao tac hoan toan binh thuong.
            thanhVienChiGiap("sub-nguoi-gui");
            nguoiChiGiap = clan.personIn(clan.chiGiapId);
            Map<String, Object> xoaTruong = new java.util.LinkedHashMap<>();
            xoaTruong.put("deathSolar", null);

            ChangeRequestView yeuCau = service.submit(new SubmitChangeRequestCommand(
                    ChangeRequestType.UPDATE_PERSON, nguoiChiGiap, null, xoaTruong,
                    "Ghi nhầm ngày mất của cụ, xin bỏ"));

            assertThat(yeuCau.payload()).containsEntry("deathSolar", null);
            assertThat(yeuCau.payloadFields()).containsExactly("deathSolar");

            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThat(service.review(new ReviewChangeRequestCommand(yeuCau.id(), true, null))
                    .status()).isEqualTo("APPROVED");
            ChangeRequestApprovedEvent event =
                    (ChangeRequestApprovedEvent) clan.publishedEvents.get(0);
            assertThat(event.payload()).containsEntry("deathSolar", null);
        }

        @Test
        @DisplayName("Việc gửi để lại vết CREATE trong audit_log")
        void ghiVetKhiGui() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            assertThat(clan.auditLog.last().entry().entityType()).isEqualTo("ChangeRequest");
            assertThat(clan.auditLog.last().entry().action().name()).isEqualTo("CREATE");
        }
    }

    @Nested
    @DisplayName("Duyệt: soi ltree chứ không chỉ soi vai")
    class DuyetSoiPhamVi {

        @Test
        @DisplayName("Trưởng Chi Ất duyệt yêu cầu nhắm vào Chi Giáp -> BRANCH_SCOPE_VIOLATION")
        void truongChiKhacNhanhBiChan() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser truongChiAt = clan.account("sub-truong-chi-at", clan.chiAtId);
            clan.branchHeadOf(truongChiAt, clan.chiAtId);
            TestSecurity.loginAs("sub-truong-chi-at", "BRANCH_HEAD");

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, "duyệt hộ")))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION);

            assertThat(clan.requests.byId(yeuCau.id()).orElseThrow().status())
                    .isEqualTo(ChangeRequestStatus.PENDING);
            assertThat(clan.publishedEvents).isEmpty();
        }

        @Test
        @DisplayName("Trưởng Chi Giáp duyệt được, và phát sự kiện để genealogy áp dụng")
        void truongChiDungNhanhDuyetDuoc() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser truongChiGiap = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChiGiap, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            ChangeRequestView sau = service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, "Đã đối chiếu bản chép tay"));

            assertThat(sau.status()).isEqualTo("APPROVED");
            assertThat(sau.reviewerId()).isEqualTo(truongChiGiap.id());
            assertThat(sau.reviewedAt()).isNotNull();
            assertThat(clan.publishedEvents).hasSize(1);
            assertThat(clan.publishedEvents.get(0)).isInstanceOf(ChangeRequestApprovedEvent.class);
        }

        @Test
        @DisplayName("Trưởng chi của chi cha duyệt được yêu cầu của cành con")
        void chiChaPhuCanhCon() {
            thanhVienChiGiap("sub-nguoi-gui");
            UUID nguoiNganhTruong = clan.personIn(clan.nganhTruongId);
            ChangeRequestView yeuCau = service.submit(new SubmitChangeRequestCommand(
                    ChangeRequestType.UPDATE_PERSON, nguoiNganhTruong, null, Map.of(), "sửa"));

            AppUser truongChiGiap = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChiGiap, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThat(service.review(new ReviewChangeRequestCommand(yeuCau.id(), true, null))
                    .status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("Trưởng cành con KHÔNG duyệt được yêu cầu của chi cha")
        void canhConKhongVoiLenChiCha() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser truongNganh = clan.account("sub-truong-nganh", clan.nganhTruongId);
            clan.branchHeadOf(truongNganh, clan.nganhTruongId);
            TestSecurity.loginAs("sub-truong-nganh", "BRANCH_HEAD");

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, null)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu duyệt được mọi chi")
        void hoiDongDuyetMoiChi() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");

            assertThat(service.review(new ReviewChangeRequestCommand(yeuCau.id(), true, null))
                    .status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("Thành viên thường không duyệt được — sai vai, chặn trước khi xét nhánh")
        void thanhVienKhongDuyet() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            clan.account("sub-thanh-vien-khac", clan.chiGiapId);
            TestSecurity.loginAs("sub-thanh-vien-khac", "MEMBER");

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, null)))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.FORBIDDEN);
        }

        @Test
        @DisplayName("Nhân khẩu không rõ chi: chỉ vai toàn dòng họ duyệt được")
        void nhanKhauKhongRoChi() {
            clan.account("sub-nguoi-gui", clan.chiGiapId);
            TestSecurity.loginAs("sub-nguoi-gui", "MEMBER");
            UUID nguoiKhongChi = clan.personWithoutBranch();
            ChangeRequestView yeuCau = service.submit(new SubmitChangeRequestCommand(
                    ChangeRequestType.UPDATE_PERSON, nguoiKhongChi, null, Map.of(), "sửa"));
            assertThat(yeuCau.targetBranchId()).isNull();

            AppUser truongChiGiap = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChiGiap, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");
            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, null)))
                    .isInstanceOf(ForbiddenException.class);

            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");
            assertThatCode(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, null)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Từ chối và rút lại")
    class TuChoiVaRutLai {

        @Test
        @DisplayName("Từ chối phát sự kiện kèm lý do, KHÔNG kèm nội dung đề nghị")
        void tuChoi() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");
            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            ChangeRequestView sau = service.review(new ReviewChangeRequestCommand(
                    yeuCau.id(), false, "Trái với bản chép tay năm Bảo Đại thứ 10"));

            assertThat(sau.status()).isEqualTo("REJECTED");
            assertThat(clan.publishedEvents).hasSize(1);
            ChangeRequestRejectedEvent event =
                    (ChangeRequestRejectedEvent) clan.publishedEvents.get(0);
            assertThat(event.reason()).contains("Bảo Đại");
            assertThat(event.requesterAppUserId()).isEqualTo(yeuCau.requestedBy());
        }

        @Test
        @DisplayName("Từ chối không kèm lý do -> VALIDATION_FAILED")
        void tuChoiThieuLyDo() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");
            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), false, "  ")))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("Người gửi rút lại được đề nghị của mình")
        void nguoiGuiRutLai() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            assertThat(service.cancel(yeuCau.id()).status()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("Người khác không rút hộ được")
        void nguoiKhacKhongRutHo() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");
            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThatThrownBy(() -> service.cancel(yeuCau.id()))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Không tự duyệt và không xử lý lại")
    class LuatKhac {

        @Test
        @DisplayName("Trưởng chi tự duyệt đề nghị của chính mình -> SELF_REVIEW_FORBIDDEN")
        void khongTuDuyet() {
            // Neu tu duyet duoc thi luong duyet chi con la mot buoc bam them, va audit_log ghi lai
            // mot cuoc phe duyet khong co ai kiem tra ai.
            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");
            UUID nguoi = clan.personIn(clan.chiGiapId);
            ChangeRequestView yeuCau = service.submit(new SubmitChangeRequestCommand(
                    ChangeRequestType.UPDATE_PERSON, nguoi, null, Map.of(), "sửa"));

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), true, "tự duyệt")))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.SELF_REVIEW_FORBIDDEN);
        }

        @Test
        @DisplayName("Yêu cầu đã xử lý thì không xử lý lại -> CHANGE_REQUEST_CLOSED")
        void khongXuLyLai() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");
            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");
            service.review(new ReviewChangeRequestCommand(yeuCau.id(), true, null));

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(yeuCau.id(), false, "đổi ý")))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getCode())
                    .isEqualTo(MembershipProblemCodes.CHANGE_REQUEST_CLOSED);
        }

        @Test
        @DisplayName("Yêu cầu không tồn tại -> NOT_FOUND")
        void khongTonTai() {
            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThatThrownBy(() -> service.review(
                    new ReviewChangeRequestCommand(UUID.randomUUID(), true, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Hàng đợi duyệt lọc theo phạm vi")
    class HangDoiTheoPhamVi {

        @Test
        @DisplayName("Trưởng Chi Ất KHÔNG thấy hàng đợi của Chi Giáp")
        void khongThayHangDoiChiKhac() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser truongChiAt = clan.account("sub-truong-chi-at", clan.chiAtId);
            clan.branchHeadOf(truongChiAt, clan.chiAtId);
            TestSecurity.loginAs("sub-truong-chi-at", "BRANCH_HEAD");

            assertThat(service.pendingForReview(0, 20)).isEmpty();
            assertThat(service.countPendingForReview()).isZero();
        }

        @Test
        @DisplayName("Trưởng Chi Giáp thấy đúng hàng đợi của mình")
        void thayHangDoiCuaMinh() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser truongChiGiap = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChiGiap, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThat(service.pendingForReview(0, 20)).hasSize(1);
            assertThat(service.countPendingForReview()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Trưởng chi chưa được giao chi nào thấy danh sách RỖNG, không phải thấy tất")
        void chuaCoPhamViThiThayRong() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            clan.account("sub-truong-chi-khong-pham-vi", clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-khong-pham-vi", "BRANCH_HEAD");

            assertThat(service.pendingForReview(0, 20)).isEmpty();
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu thấy toàn bộ hàng đợi")
        void hoiDongThayTat() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser hoiDong = clan.account("sub-hoi-dong", clan.gocId);
            clan.councilWide(hoiDong);
            TestSecurity.loginAs("sub-hoi-dong", "COUNCIL");

            assertThat(service.pendingForReview(0, 20)).hasSize(1);
        }

        @Test
        @DisplayName("Thành viên không xem được hàng đợi duyệt")
        void thanhVienKhongXemHangDoi() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            assertThatThrownBy(() -> service.pendingForReview(0, 20))
                    .isInstanceOf(ForbiddenException.class);
            // Nhung dem cho badge thi tra 0 thay vi nem — badge khong duoc lam vo mot man hinh.
            assertThat(service.countPendingForReview()).isZero();
        }

        @Test
        @DisplayName("Người gửi theo dõi được đề nghị của chính mình")
        void theoDoiDeNghiCuaMinh() {
            guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            List<ChangeRequestView> cuaToi = service.mine(0, 20);

            assertThat(cuaToi).hasSize(1);
            assertThat(cuaToi.get(0).payload()).containsKey("deathLunar");
        }
    }

    @Nested
    @DisplayName("Nội dung đề nghị chỉ hiện với người được phép")
    class LoNoiDung {

        @Test
        @DisplayName("Người gửi và người có quyền duyệt thấy payload")
        void nguoiDuocPhepThayPayload() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");
            assertThat(service.byId(yeuCau.id()).payload()).containsKey("deathLunar");

            AppUser truongChi = clan.account("sub-truong-chi-giap", clan.chiGiapId);
            clan.branchHeadOf(truongChi, clan.chiGiapId);
            TestSecurity.loginAs("sub-truong-chi-giap", "BRANCH_HEAD");

            assertThat(service.byId(yeuCau.id()).payload()).containsKey("deathLunar");
        }

        @Test
        @DisplayName("Người ngoài chỉ thấy TÊN trường, không thấy giá trị")
        void nguoiNgoaiChiThayTenTruong() {
            // Noi dung de nghi co the chua so dien thoai hoac dia chi cua mot nguoi con song.
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            clan.account("sub-nguoi-ngoai", clan.chiAtId);
            TestSecurity.loginAs("sub-nguoi-ngoai", "MEMBER");

            ChangeRequestView thay = service.byId(yeuCau.id());

            assertThat(thay.payload()).isEmpty();
            assertThat(thay.payloadFields()).containsExactly("deathLunar");
        }

        @Test
        @DisplayName("Trưởng Chi Ất cũng chỉ thấy tên trường của yêu cầu thuộc Chi Giáp")
        void truongChiKhacNhanhCungBiCat() {
            ChangeRequestView yeuCau = guiYeuCauSuaNguoiChiGiap("sub-nguoi-gui");

            AppUser truongChiAt = clan.account("sub-truong-chi-at", clan.chiAtId);
            clan.branchHeadOf(truongChiAt, clan.chiAtId);
            TestSecurity.loginAs("sub-truong-chi-at", "BRANCH_HEAD");

            assertThat(service.byId(yeuCau.id()).payload()).isEmpty();
        }
    }
}
