package vn.giapha.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;
import vn.giapha.dataimport.domain.ImportLimits;

/**
 * Ghim rằng <b>trần multipart của Spring</b> và <b>trần nghiệp vụ</b> không trôi khỏi nhau.
 *
 * <h2>Lỗi mà lớp test này tồn tại để chặn</h2>
 * Khi {@code spring.servlet.multipart.max-file-size} không được khai báo, Spring Boot dùng mặc
 * định <b>1 MB</b>, trong khi {@link ImportLimits#MAX_FILE_BYTES} là 10 MB. Một tệp .xlsx 2 MB
 * hoàn toàn hợp lệ của một chi 400 người bị chặn ở cửa — và vì tầng api <i>đã</i> có handler cho
 * {@code MaxUploadSizeExceededException}, người dùng nhận về đúng mã {@code 413} kèm đúng câu
 * "vượt trần 10 MB". Một thông báo <b>sai</b> mà trông như đúng: nó nói dối về ngưỡng, nên người
 * nhập sẽ đi xoá ảnh trong một tệp không có ảnh nào.
 *
 * <p>Hai con số nằm ở hai tệp khác loại (YAML và Java) nên không có cách nào để trình biên dịch
 * bắt chúng lệch nhau. Bài kiểm này là cách duy nhất, và nó cố ý đọc thẳng
 * {@code application.yml} chứ không đọc một bean đã bind: bind được nghĩa là ứng dụng đã khởi động
 * xong, mà cái cần canh là <b>giá trị trong tệp cấu hình</b>.</p>
 */
@DisplayName("Trần multipart phải khớp ImportLimits")
class ImportMultipartLimitTest {

    private static final String TRAN_TEP = "spring.servlet.multipart.max-file-size";
    private static final String TRAN_YEU_CAU = "spring.servlet.multipart.max-request-size";

    @Test
    @DisplayName("max-file-size trong application.yml đúng bằng ImportLimits.MAX_FILE_BYTES")
    void tranTepKhopVoiImportLimits() throws IOException {
        DataSize tranTep = DataSize.parse(String.valueOf(giaTri(TRAN_TEP)));

        assertThat(tranTep.toBytes())
                .as("%s phai bang ImportLimits.MAX_FILE_BYTES; lech mot trong hai ben la mot tep"
                        + " hop le bi tu choi kem mot cau giai thich sai", TRAN_TEP)
                .isEqualTo(ImportLimits.MAX_FILE_BYTES);
    }

    @Test
    @DisplayName("max-request-size lớn hơn max-file-size — phần bao multipart không được ăn vào trần tệp")
    void tranYeuCauLonHonTranTep() throws IOException {
        DataSize tranTep = DataSize.parse(String.valueOf(giaTri(TRAN_TEP)));
        DataSize tranYeuCau = DataSize.parse(String.valueOf(giaTri(TRAN_YEU_CAU)));

        // Mot yeu cau multipart khong chi co tep: con boundary, header cua tung phan, va hai tham
        // so branchId/force. Dat hai tran bang nhau thi mot tep DUNG BANG tran se bi tu choi, va
        // trieu chung la "9,9 MB thi duoc ma 10 MB thi khong" — rat kho doan ra nguyen nhan.
        assertThat(tranYeuCau.toBytes())
                .as("%s phai lon hon %s", TRAN_YEU_CAU, TRAN_TEP)
                .isGreaterThan(tranTep.toBytes());
    }

    private static Object giaTri(String khoa) throws IOException {
        List<PropertySource<?>> nguon = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        for (PropertySource<?> ps : nguon) {
            Object value = ps.getProperty(khoa);
            if (value != null) {
                return value;
            }
        }
        throw new AssertionError("application.yml khong khai bao " + khoa
                + "; thieu no thi Spring dung mac dinh 1 MB va moi tep that deu bi chan");
    }
}
