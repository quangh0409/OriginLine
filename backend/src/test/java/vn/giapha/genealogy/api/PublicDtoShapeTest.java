package vn.giapha.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.giapha.genealogy.api.rest.dto.PersonAccessMetaDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPageDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonSummaryDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicRelationDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicTreeDto;

/**
 * Ghim <b>hình dạng</b> của mọi DTO công khai.
 *
 * <h2>Vì sao một phép kiểm bằng phản chiếu lại đáng giá hơn nó trông</h2>
 * Lớp bảo vệ mạnh nhất của cổng thông tin công khai không phải một câu lệnh {@code if}, mà là việc
 * DTO công khai <b>không có chỗ</b> để chứa dữ liệu nhạy cảm: không có trường thì không có lỗi lập
 * trình nào làm rò rỉ được. Nhưng "không có chỗ" là một thuộc tính rất dễ mất — chỉ cần một lần ai
 * đó thêm {@code contact} vào {@code PublicPersonDto} cho tiện, và mọi ca test theo
 * {@code jsonPath} vẫn xanh vì chúng chỉ kiểm những gì chúng biết mà hỏi.
 *
 * <p>Ca test này hỏi ngược lại: <b>có trường nào không được phép có mặt không?</b> Nó fail ngay tại
 * thời điểm trường bị thêm vào, chứ không đợi tới lúc có ai đó gửi request đúng kiểu.</p>
 *
 * <p><b>Nếu ca này fail:</b> đừng nới danh sách cấm. Hãy hỏi vì sao bề mặt không cần đăng nhập lại
 * cần trường đó — câu trả lời gần như luôn là "không cần", và chỗ đúng của nó là
 * {@code /api/v1/persons} sau khi đăng nhập.</p>
 */
@DisplayName("Hình dạng DTO công khai — không có chỗ cho dữ liệu Tầng 2/Tầng 3")
class PublicDtoShapeTest {

    /**
     * Tên trường tuyệt đối không được xuất hiện trên bề mặt công khai, đã chuẩn hoá về chữ thường.
     *
     * <ul>
     *   <li>{@code contact}/{@code phone}/{@code email}/{@code zaloid} — Tầng 3, và với người đã
     *       khuất thì thực chất là liên hệ của người thân <b>đang sống</b>.</li>
     *   <li>{@code occupation}, {@code currentplace*} — Tầng 2; địa chỉ của một cụ đã mất thường
     *       chính là nơi con cháu đang ở.</li>
     *   <li>{@code attributes} — JSONB tự do, nội dung do người nhập quyết định, không lớp nào ở
     *       giữa kiểm duyệt được.</li>
     *   <li>{@code privacylevel}, {@code version}, {@code createdat}, {@code updatedat},
     *       {@code access}, {@code isdeleted} — siêu dữ liệu vận hành, không phải phả hệ.</li>
     *   <li>{@code childcount} — <b>đếm cả người còn sống</b>, nên để lộ là gián tiếp nói cụ này
     *       còn mấy người con mà khách không được thấy.</li>
     *   <li>{@code totalelements}, {@code totalpages} — phép đếm dân số dòng họ.</li>
     * </ul>
     */
    private static final Set<String> TRUONG_BI_CAM = Set.of(
            "contact", "phone", "email", "zaloid", "occupation",
            "currentplace", "currentplacefull", "currentplaceprovince",
            "attributes", "privacylevel", "version", "createdat", "updatedat",
            "access", "isdeleted", "deleted", "childcount",
            "totalelements", "totalpages", "note", "validto", "validfrom");

    private static final List<Class<?>> DTO_CONG_KHAI = List.of(
            PublicPersonDto.class,
            // Kiểu của PublicPersonDto.meta. Có mặt ở đây vì danh sách cấm chỉ soi MỘT tầng: một
            // record lồng bên trong DTO công khai là một lỗ hổng y hệt nếu nó mang trường cấm.
            PersonAccessMetaDto.class,
            PublicPersonSummaryDto.class,
            PublicRelationDto.class,
            PublicPageDto.class,
            PublicPageDto.PublicPageMetaDto.class,
            PublicTreeDto.class,
            PublicTreeDto.PublicTreeNodeDto.class,
            PublicTreeDto.PublicTreeEdgeDto.class,
            PublicTreeDto.PublicTreeMetaDto.class);

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("dtoCongKhai")
    @DisplayName("không DTO công khai nào mang trường thuộc danh sách cấm")
    void khongMangTruongBiCam(Class<?> dto) {
        assertThat(dto.isRecord())
                .as("%s phai la record de hinh dang cua no ghim duoc bang phan chieu", dto.getName())
                .isTrue();

        List<String> viPham = Arrays.stream(dto.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(name -> TRUONG_BI_CAM.contains(name.toLowerCase(Locale.ROOT)))
                .toList();

        assertThat(viPham)
                .as("%s dang phoi du lieu khong duoc phep ra be mat cong khai", dto.getSimpleName())
                .isEmpty();
    }

    static List<Class<?>> dtoCongKhai() {
        return DTO_CONG_KHAI;
    }

    @org.junit.jupiter.api.Test
    @DisplayName("hồ sơ công khai vẫn giữ đủ dữ liệu phả hệ của người đã khuất")
    void vanGiuDuDuLieuPhaHe() {
        List<String> truong = Arrays.stream(PublicPersonDto.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        // BA v2 §10: nguoi da khuat cong khai ten, ngay sinh-mat, tieu su, vai ve. Siet qua tay
        // cung la mot kieu hong: cong thong tin dong ho ma khong tra duoc doi thu may thi vo dung.
        assertThat(truong).contains("names", "displayName", "generation", "birth", "death",
                "biography", "nativePlace", "primaryBranch", "relations");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("meta có mặt — thiếu nó thì ngăn hồ sơ của Khách không nối được vào cổng công khai")
    void giuLaiCoMeta() {
        assertThat(Arrays.stream(PublicPersonDto.class.getRecordComponents())
                .map(RecordComponent::getName))
                .as("Khach bam vao mot node tren pha do phai mo duoc ngan ho so")
                .contains("meta");

        // Ten phai la "meta", KHONG phai "access": "access" nam trong danh sach cam o tren va
        // frontend da doc khoa nay tren /api/v1/persons/{id}. Hai be mat dung chung mot ten thi
        // ngan ho so khong phai viet hai nhanh.
        assertThat(Arrays.stream(PublicPersonDto.class.getRecordComponents())
                .map(RecordComponent::getName)).doesNotContain("access");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("isAlive vẫn có mặt — nó là mệnh đề mà guard và bộ test ghim vào")
    void giuLaiCoIsAlive() {
        assertThat(Arrays.stream(PublicPersonDto.class.getRecordComponents())
                .map(RecordComponent::getName)).contains("isAlive");
        assertThat(Arrays.stream(PublicPersonSummaryDto.class.getRecordComponents())
                .map(RecordComponent::getName)).contains("isAlive");
    }
}
