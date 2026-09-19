package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.FakeDuplicateCandidatePort.NguoiGia;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeTabooNamePort;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>Facade {@code PersonScreeningService}</b> — cửa duy nhất để context khác dùng lại hai phép
 * quét trước-khi-ghi của {@code genealogy}.
 *
 * <p>Test này giữ hai lời hứa, và cả hai đều là lời hứa <b>kiến trúc</b>, không phải lời hứa về
 * điểm số:</p>
 *
 * <ol>
 *   <li><b>Không rò value object của domain.</b> Chữ ký công khai của facade và của các kiểu mang
 *       nhãn {@code "do-trung"} không được nhắc tới {@code vn.giapha.genealogy.domain} — nếu rò
 *       thì để Spring Modulith cho qua sẽ phải gắn siêu dữ liệu framework lên lớp domain, đúng thứ
 *       {@code DomainPurityTest} cấm.</li>
 *   <li><b>Không nhân đôi khái niệm.</b> {@code ScreeningSubject} là danh sách tham số của phép
 *       quét, không phải bản sao thứ hai của mô hình nhân khẩu. Hai test dưới đây ghim điều đó:
 *       mọi thành phần của {@code DuplicateProbe} phải được {@code ScreeningSubject} nuôi, và kết
 *       quả đi qua facade phải <b>trùng khít</b> kết quả gọi thẳng {@code DuplicatePersonChecker}.
 *       Hai bộ chấm điểm song song sẽ lệch nhau, và triệu chứng là màn nhập liệu bảo trùng còn màn
 *       thêm người bảo không.</li>
 * </ol>
 */
class PersonScreeningServiceTest {

    private static final UUID CHI_GIAP = UUID.randomUUID();

    private final FakeDuplicateCandidatePort candidates = new FakeDuplicateCandidatePort();
    private final FakeTabooNamePort tabooPort = new FakeTabooNamePort();
    private final DuplicatePersonChecker duplicates = new DuplicatePersonChecker(candidates);
    private final TabooNameChecker taboos = new TabooNameChecker(tabooPort);
    private final PersonScreeningService screening =
            new PersonScreeningService(duplicates, taboos);

    // -------------------------------------------------------------------------------------
    // Lời hứa 1 — không rò value object của domain qua ranh giới context
    // -------------------------------------------------------------------------------------

    /**
     * Năm kiểu mang nhãn {@code @NamedInterface("do-trung")}. Danh sách này chính là bề mặt tiếp
     * xúc mà context khác nhìn thấy; thêm kiểu vào đây là một quyết định kiến trúc, không phải
     * thao tác dọn lỗi biên dịch.
     */
    private static final List<Class<?>> BE_MAT_DO_TRUNG = List.of(
            PersonScreeningService.class, ScreeningSubject.class, TabooHit.class,
            DuplicateReport.class, DuplicateMatch.class);

    @Test
    @DisplayName("Bề mặt \"do-trung\" không nhắc tới một kiểu domain nào")
    void beMatKhongRoKieuDomain() {
        List<String> viPham = new ArrayList<>();
        for (Class<?> lop : BE_MAT_DO_TRUNG) {
            for (RecordComponent rc : lop.isRecord() ? lop.getRecordComponents()
                    : new RecordComponent[0]) {
                for (Class<?> kieu : kieuTrongChuKy(rc.getGenericType())) {
                    if (laKieuDomain(kieu)) {
                        viPham.add(lop.getSimpleName() + "#" + rc.getName() + " -> " + kieu.getName());
                    }
                }
            }
            Arrays.stream(lop.getDeclaredMethods())
                    .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                    .forEach(m -> {
                        Set<Class<?>> kieu = new LinkedHashSet<>();
                        Arrays.stream(m.getGenericParameterTypes())
                                .forEach(t -> kieu.addAll(kieuTrongChuKy(t)));
                        kieu.addAll(kieuTrongChuKy(m.getGenericReturnType()));
                        kieu.stream().filter(PersonScreeningServiceTest::laKieuDomain)
                                .forEach(k -> viPham.add(
                                        lop.getSimpleName() + "." + m.getName() + " -> " + k.getName()));
                    });
        }

        assertThat(viPham)
                .as("value object cua domain khong duoc di qua ranh gioi bounded context")
                .isEmpty();
    }

    /**
     * Đúng năm kiểu ấy mang nhãn, và <b>không kiểu domain nào</b> mang nhãn.
     *
     * <p>Đây là vế thứ hai của {@code DomainPurityTest}: test kia soi danh sách lớp domain, test
     * này soi từ phía bề mặt tiếp xúc, để việc "mở thêm một kiểu domain cho nhanh" không lọt qua
     * được cửa nào.</p>
     */
    @Test
    @DisplayName("Nhãn \"do-trung\" chỉ nằm ở tầng application")
    void nhanChiNamOTangApplication() {
        for (Class<?> lop : BE_MAT_DO_TRUNG) {
            assertThat(lop.getPackageName())
                    .as("%s phai o tang application", lop.getSimpleName())
                    .isEqualTo("vn.giapha.genealogy.application");
            assertThat(lop.getAnnotation(org.springframework.modulith.NamedInterface.class))
                    .as("%s phai mang nhan do-trung", lop.getSimpleName())
                    .isNotNull();
        }
        for (Class<?> lop : List.of(PersonName.class, NameType.class, LifeDate.class,
                DatePrecision.class, vn.giapha.genealogy.domain.TabooConflict.class)) {
            assertThat(lop.getAnnotations())
                    .as("%s la POJO thuan — khong annotation framework nao, ke ca @NamedInterface",
                            lop.getSimpleName())
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------------------------
    // Lời hứa 2 — không nhân đôi khái niệm
    // -------------------------------------------------------------------------------------

    /**
     * Mọi thành phần của {@code DuplicateProbe} đều được {@code ScreeningSubject} nuôi.
     *
     * <p>Bảng ánh xạ dưới đây là <b>duy nhất một chỗ</b> khai báo hai bên tương ứng thế nào. Thêm
     * một thành phần vào {@code DuplicateProbe} mà quên nuôi nó từ facade sẽ làm test này đỏ ngay,
     * kèm đúng tên thành phần bị bỏ quên — đó là chuông báo lệch, thứ mà một record mirror không có
     * chuông thì rất dễ âm thầm trôi.</p>
     */
    private static final Map<String, String> PROBE_LAY_TU_SUBJECT = Map.ofEntries(
            Map.entry("ref", "ref"),
            // Ba lop ten cua ScreeningSubject gop lai thanh List<PersonName> cua probe.
            Map.entry("names", "fullName + tabooName + posthumousName"),
            Map.entry("gender", "gender"),
            Map.entry("generation", "generation"),
            Map.entry("branchId", "branchId"),
            Map.entry("birth", "birthYear"),
            Map.entry("death", "gio"),
            Map.entry("nativePlace", "nativePlace"),
            Map.entry("excludePersonId", "excludePersonId"));

    @Test
    @DisplayName("DuplicateProbe không có thành phần nào mà ScreeningSubject bỏ quên")
    void khongThanhPhanNaoBiBoQuen() {
        List<String> cuaProbe = Arrays.stream(DuplicateProbe.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertThat(cuaProbe)
                .as("them thanh phan vao DuplicateProbe thi phai quyet dinh facade co mang no khong")
                .containsExactlyInAnyOrderElementsOf(PROBE_LAY_TU_SUBJECT.keySet());

        Set<String> cuaSubject = Arrays.stream(ScreeningSubject.class.getRecordComponents())
                .map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        for (String nguon : PROBE_LAY_TU_SUBJECT.values()) {
            for (String truong : nguon.split("\\s*\\+\\s*")) {
                assertThat(cuaSubject).as("ScreeningSubject phai co truong %s", truong)
                        .contains(truong);
            }
        }
    }

    @Test
    @DisplayName("Đi qua facade cho kết quả TRÙNG KHÍT với gọi thẳng bộ dò")
    void ketQuaTrungKhitVoiGoiThang() {
        UUID cuId = candidates.them(NguoiGia.ten("Nguyễn Văn Cẩn").id(UUID.randomUUID()).doi(3)
                .chi(CHI_GIAP).namMat(1950).gio(8, 15).nguyenQuan("Hà Đông"));

        ScreeningSubject subject = new ScreeningSubject("AT-03-001", "Nguyễn Văn Cẩn", "Cẩn",
                "Trung Chính", Gender.MALE, 3, CHI_GIAP, 1890,
                new LunarDate(1950, 8, 15, false), "Hà Đông", null);

        // Ban "goi thang": dung DuplicateProbe bang tay, dung cach ma luong them-mot-nguoi lam.
        DuplicateProbe thuCong = new DuplicateProbe("AT-03-001",
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Cẩn", true),
                        PersonName.of(NameType.HUY, "Cẩn", false),
                        PersonName.of(NameType.THUY, "Trung Chính", false)),
                Gender.MALE, 3, CHI_GIAP,
                LifeDate.of(LocalDate.of(1890, 1, 1), null, DatePrecision.YEAR),
                LifeDate.ofLunar(new LunarDate(1950, 8, 15, false)),
                "Hà Đông", null);

        assertThat(PersonScreeningService.toProbe(subject))
                .as("facade phai dung DUNG probe ma luong them-mot-nguoi dung")
                .isEqualTo(thuCong);

        List<DuplicateReport> quaFacade = screening.scanDuplicates(List.of(subject));
        List<DuplicateReport> goiThang = duplicates.check(List.of(thuCong));

        assertThat(quaFacade).isEqualTo(goiThang);
        assertThat(quaFacade).singleElement()
                .satisfies(r -> assertThat(r.matches()).isNotEmpty()
                        .first().extracting(DuplicateMatch::personId).isEqualTo(cuId));
    }

    // -------------------------------------------------------------------------------------
    // Hành vi — facade không tự chấm điểm, chỉ chuyển tiếp
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Lô rỗng hoặc null thì không gọi CSDL lần nào")
    void loRongThiImLang() {
        assertThat(screening.scanDuplicates(null)).isEmpty();
        assertThat(screening.scanDuplicates(List.of())).isEmpty();
        assertThat(screening.scanTabooNames(null)).isEmpty();
        assertThat(screening.scanTabooNames(List.of())).isEmpty();
        assertThat(candidates.soLanTraUngVien).isZero();
        assertThat(tabooPort.daHoi).isEmpty();
    }

    @Test
    @DisplayName("Cả lô nghi trùng chỉ tốn đúng một vòng gọi cửa lọc")
    void caLoMotVongGoi() {
        candidates.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920));

        screening.scanDuplicates(List.of(
                subjectNguoi("r1", "Nguyễn Văn Tuấn", 5, 1920),
                subjectNguoi("r2", "Nguyễn Văn Bảy", 5, 1922),
                subjectNguoi("r3", "Nguyen Van Tuan", 5, 1921)));

        assertThat(candidates.soLanTraUngVien).isEqualTo(1);
        assertThat(candidates.soLanBoDau).isEqualTo(1);
    }

    @Test
    @DisplayName("Kỵ húy: chỉ hỏi dòng CÓ tên huý, và chỉ hỏi bằng lớp tên HUY")
    void kyHuyChiHoiDongCoTenHuy() {
        tabooPort.vaChamVoi("Cẩn", FakeTabooNamePort.cuTo("Cẩn", 3));

        List<TabooHit> hits = screening.scanTabooNames(List.of(
                ScreeningSubject.kyHuy("AT-07-001", "Cẩn", 7, null),
                ScreeningSubject.kyHuy("AT-07-002", "  ", 7, null),
                ScreeningSubject.kyHuy("AT-07-003", null, 7, null),
                ScreeningSubject.kyHuy("AT-07-004", "Tân", 7, null)));

        assertThat(tabooPort.daHoi)
                .as("dong khong co ten huy khong duoc tao mot luot truy van nao")
                .containsExactly("Cẩn", "Tân");
        assertThat(tabooPort.doiThuDaNhan).containsExactly(7, 7);
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.ref()).isEqualTo("AT-07-001");
            assertThat(hit.tabooName()).isEqualTo("Cẩn");
            assertThat(hit.ancestorGeneration()).isEqualTo(3);
            assertThat(hit.ancestorPersonId()).isNotNull();
        });
    }

    @Test
    @DisplayName("Kỵ húy: một dòng trùng huý nhiều bậc trên thì trả về đủ từng ấy va chạm")
    void kyHuyTraDuMoiVaCham() {
        tabooPort.vaChamVoi("Cẩn", FakeTabooNamePort.cuTo("Cẩn", 3),
                FakeTabooNamePort.cuTo("Cẩn", 5));

        List<TabooHit> hits = screening.scanTabooNames(
                List.of(ScreeningSubject.kyHuy("AT-07-001", "Cẩn", 7, null)));

        assertThat(hits).hasSize(2)
                .allSatisfy(hit -> assertThat(hit.ref()).isEqualTo("AT-07-001"))
                .extracting(TabooHit::ancestorGeneration)
                .containsExactly(3, 5);
    }

    @Test
    @DisplayName("Tên huý là tín hiệu so trùng, không chỉ là căn cứ kỵ húy")
    void tenHuyCungLaTinHieuSoTrung() {
        UUID cuId = candidates.them(NguoiGia.ten("Nguyễn Văn Khác")
                .themTen(NameType.HUY, "Cẩn").doi(3).chi(CHI_GIAP).namSinh(1890));

        List<DuplicateReport> reports = screening.scanDuplicates(List.of(new ScreeningSubject(
                "AT-03-002", "Nguyễn Văn Nào Đó", "Cẩn", null, Gender.MALE, 3, CHI_GIAP, 1890,
                null, null, null)));

        assertThat(reports).singleElement()
                .satisfies(r -> assertThat(r.matches()).isNotEmpty()
                        .first().extracting(DuplicateMatch::personId).isEqualTo(cuId));
    }

    // -------------------------------------------------------------------------------------

    private static ScreeningSubject subjectNguoi(String ref, String ten, Integer doi, Integer namSinh) {
        return new ScreeningSubject(ref, ten, null, null, Gender.MALE, doi, CHI_GIAP, namSinh,
                null, null, null);
    }

    /** Kiểu thô của một chữ ký, kèm mọi tham số generic của nó ({@code List<X>} cho ra cả X). */
    private static Set<Class<?>> kieuTrongChuKy(Type type) {
        Set<Class<?>> out = new LinkedHashSet<>();
        if (type instanceof Class<?> c) {
            out.add(c);
        } else if (type instanceof ParameterizedType p) {
            out.addAll(kieuTrongChuKy(p.getRawType()));
            for (Type arg : p.getActualTypeArguments()) {
                out.addAll(kieuTrongChuKy(arg));
            }
        }
        return out;
    }

    private static boolean laKieuDomain(Class<?> kieu) {
        return kieu.getName().startsWith("vn.giapha.genealogy.domain");
    }
}
