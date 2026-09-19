package vn.giapha.dataimport.domain.port;

import java.io.InputStream;
import vn.giapha.dataimport.domain.ParsedWorkbook;

/**
 * Cổng đọc một tệp người dùng gửi lên thành các dòng chờ.
 *
 * <p>Cố ý nhận {@link InputStream} chứ không nhận mảng byte: hiện thực phải đọc <b>theo dòng</b>
 * để bộ nhớ không phụ thuộc kích thước tệp. Một chữ ký nhận mảng byte là lời mời gọi nạp cả
 * workbook vào heap, và đó là cách chắc chắn nhất để một tệp 20 MB làm sập tiến trình web.</p>
 *
 * <p>Hiện thực phải <b>tự phòng thủ</b>: giới hạn kích thước, giới hạn số dòng, chống zip bomb,
 * chặn thực thể ngoài XML, không bao giờ tính lại công thức. Xem
 * {@code vn.giapha.dataimport.domain.ImportLimits}.</p>
 */
public interface WorkbookReaderPort {

    /**
     * @param filename tên tệp gốc, chỉ để ghi log và báo lỗi. <b>Không tin đuôi tệp</b>: nhận dạng
     *        phải dựa vào chữ ký tệp.
     * @throws vn.giapha.dataimport.domain.ImportRejectedException khi tệp không hợp lệ hoặc vượt
     *         giới hạn
     */
    ParsedWorkbook read(InputStream in, String filename);
}
