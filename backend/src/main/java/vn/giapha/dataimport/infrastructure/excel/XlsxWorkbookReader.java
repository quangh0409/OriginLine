package vn.giapha.dataimport.infrastructure.excel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.domain.ImportRejectedException;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.dataimport.domain.ParsedWorkbook;
import vn.giapha.dataimport.domain.port.WorkbookReaderPort;

/**
 * Đọc tệp {@code .xlsx} bằng <b>API sự kiện</b> của POI ({@code XSSFReader} + SAX).
 *
 * <h2>Vì sao SAX chứ không phải WorkbookFactory</h2>
 * {@code WorkbookFactory.create()} dựng cả workbook thành đối tượng trong heap và tốn <b>gấp 5–10
 * lần</b> kích thước tệp. Trên một tệp 20 MB đó là cách chắc chắn nhất để làm sập tiến trình web —
 * và tiến trình web ở đây phục vụ cả dòng họ, không riêng người đang tải tệp. Đọc theo dòng thì bộ
 * nhớ không phụ thuộc kích thước tệp.
 *
 * <h2>Mã hoá ký tự: vì sao "Nguyễn" không thành "Nguyá»…n" ở đây</h2>
 * Bên trong {@code .xlsx} là XML UTF-8, nên tai nạn kinh điển ấy <b>không xảy ra với xlsx</b> — nó
 * xảy ra với CSV, và đó là lý do chính không nhận CSV: Excel bản tiếng Việt xuất CSV theo bảng mã
 * hệ thống, không BOM, và không có cách nào đoán đúng 100%. Một họ tên sai bảng mã đi thẳng vào
 * {@code person_name}, sinh cột không dấu rác, rồi hỏng cả tìm kiếm lẫn cảnh báo kỵ húy.
 *
 * <p>Thứ <b>vẫn</b> xảy ra với xlsx là NFD: macOS và một số bộ gõ sinh "ễ" thành "e" cộng hai dấu
 * tổ hợp. Hai chuỗi trông giống hệt nhau mà không bằng nhau. Vì vậy mọi ô chữ đi qua
 * {@code TextNormalizer} ngay tại chỗ này, trước mọi phép so sánh.</p>
 *
 * <h2>Ba quy tắc cho đầu vào không tin cậy</h2>
 * Chữ ký tệp thay vì đuôi tệp · không bao giờ tính lại công thức · chặn thực thể ngoài XML. Xem
 * {@link XlsxGuards}.
 */
@Component
public class XlsxWorkbookReader implements WorkbookReaderPort {

    private static final Logger log = LoggerFactory.getLogger(XlsxWorkbookReader.class);

    /** Số dòng đầu được quét để tìm dòng tiêu đề — người nhập hay thêm một dòng tựa ở trên. */
    private static final int QUET_TIEU_DE = 10;

    private static final Map<String, ImportColumn> COT_NHAN_KHAU = ImportColumn.bangTra();
    private static final Map<String, MarriageColumn> COT_HON_PHOI = MarriageColumn.bangTra();

    public XlsxWorkbookReader() {
        XlsxGuards.applyGlobalLimits();
    }

    @Override
    public ParsedWorkbook read(InputStream in, String filename) {
        byte[] content = docHet(in, filename);
        XlsxGuards.requireXlsx(content, filename);

        try (OPCPackage pkg = OPCPackage.open(new java.io.ByteArrayInputStream(content))) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            DataFormatter formatter = XlsxGuards.dataFormatter();

            SheetRowCollector nhanKhau = new SheetRowCollector("Nhân khẩu",
                    ImportLimits.MAX_PERSON_ROWS, QUET_TIEU_DE, XlsxWorkbookReader::ghepCotNhanKhau);
            SheetRowCollector honPhoi = new SheetRowCollector("Hôn phối",
                    ImportLimits.MAX_MARRIAGE_ROWS, QUET_TIEU_DE, XlsxWorkbookReader::ghepCotHonPhoi);

            boolean thayNhanKhau = false;
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    String ten = ImportColumn.khoa(sheets.getSheetName());
                    if (laNhanKhau(ten)) {
                        parse(sheet, strings, styles, formatter, nhanKhau);
                        thayNhanKhau = true;
                    } else if (laHonPhoi(ten)) {
                        parse(sheet, strings, styles, formatter, honPhoi);
                    }
                }
            }
            if (!thayNhanKhau) {
                throw new ImportRejectedException(ImportRejectedException.MISSING_SHEET,
                        "Không tìm thấy trang Nhân khẩu trong tệp " + filename
                                + ". Tên trang phải là Nhân khẩu — đừng đổi tên trang trong mẫu.");
            }
            kiemCotBatBuoc(nhanKhau);

            log.info("Doc tep {}: {} dong nhan khau, {} dong hon phoi", filename,
                    nhanKhau.rows().size(), honPhoi.rows().size());
            return new ParsedWorkbook(nhanKhau.rows(), honPhoi.rows(), nhanKhau.headers(),
                    honPhoi.headers());

        } catch (ImportRejectedException ex) {
            throw ex;
        } catch (OLE2NotOfficeXmlFileException ex) {
            throw new ImportRejectedException(ImportRejectedException.BAD_FORMAT,
                    "Tệp " + filename + " là định dạng Excel cũ (.xls). Lưu lại thành .xlsx.", ex);
        } catch (OpenXML4JException | SAXException | IOException | RuntimeException ex) {
            throw thanhLoiTuChoi(ex, filename);
        }
    }

    /**
     * Chạy SAX trên một trang tính.
     *
     * <p>{@link XMLHelper#newXMLReader()} là lối duy nhất được dùng để tạo {@code XMLReader}: nó
     * tắt sẵn DOCTYPE và thực thể ngoài. Tự tạo {@code SAXParserFactory} ở đây là mở lại cửa XXE mà
     * POI đã đóng hộ.</p>
     */
    private void parse(InputStream sheet, ReadOnlySharedStringsTable strings, StylesTable styles,
                       DataFormatter formatter, SheetRowCollector collector) {
        try {
            XMLReader xmlReader = XMLHelper.newXMLReader();
            // formulasNotResults = false: gap o cong thuc thi lay GIA TRI DA LUU SAN, khong bao gio
            // tinh lai. Tinh lai tren tep la la mo cua cho tham chieu ngoai va ham WEBSERVICE.
            xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, collector,
                    formatter, false));
            xmlReader.parse(new InputSource(sheet));
        } catch (ImportRejectedException ex) {
            throw ex;
        } catch (SAXException ex) {
            // POI tu boc ImportRejectedException cua ta trong SAXException khi handler nem ra.
            Throwable goc = ex.getCause();
            if (goc instanceof ImportRejectedException rejected) {
                throw rejected;
            }
            throw new ImportRejectedException(ImportRejectedException.UNSAFE_FILE,
                    "Nội dung XML của tệp không hợp lệ hoặc không an toàn để đọc: " + ex.getMessage(),
                    ex);
        } catch (IOException | ParserConfigurationException ex) {
            throw new ImportRejectedException(ImportRejectedException.CORRUPT_FILE,
                    "Không đọc được một trang tính: " + ex.getMessage(), ex);
        }
    }

    private static void kiemCotBatBuoc(SheetRowCollector nhanKhau) {
        if (!nhanKhau.daCoTieuDe()) {
            throw new ImportRejectedException(ImportRejectedException.MISSING_COLUMN,
                    "Trang Nhân khẩu không có dòng tiêu đề nhận ra được.");
        }
        for (ImportColumn cot : ImportColumn.values()) {
            if (cot.batBuoc() && !nhanKhau.tenCotNhanRa().contains(cot.tieuDe())) {
                throw new ImportRejectedException(ImportRejectedException.MISSING_COLUMN,
                        "Trang Nhân khẩu thiếu cột bắt buộc: " + cot.tieuDe()
                                + ". Tải lại mẫu và chép dữ liệu sang, đừng tự dựng bảng.");
            }
        }
    }

    private static String ghepCotNhanKhau(String raw) {
        ImportColumn cot = COT_NHAN_KHAU.get(ImportColumn.khoa(raw));
        return cot == null ? null : cot.tieuDe();
    }

    private static String ghepCotHonPhoi(String raw) {
        MarriageColumn cot = COT_HON_PHOI.get(ImportColumn.khoa(raw));
        return cot == null ? null : cot.tieuDe();
    }

    private static boolean laNhanKhau(String tenTrangDaBoDau) {
        return tenTrangDaBoDau.contains("nhan khau") || tenTrangDaBoDau.equals("persons")
                || tenTrangDaBoDau.contains("nhan kh");
    }

    private static boolean laHonPhoi(String tenTrangDaBoDau) {
        return tenTrangDaBoDau.contains("hon phoi") || tenTrangDaBoDau.contains("hon nhan")
                || tenTrangDaBoDau.equals("marriages");
    }

    private static byte[] docHet(InputStream in, String filename) {
        try {
            byte[] content = in.readAllBytes();
            if (content.length > ImportLimits.MAX_FILE_BYTES) {
                throw new ImportRejectedException(ImportRejectedException.FILE_TOO_LARGE,
                        "Tệp " + filename + " vượt trần "
                                + (ImportLimits.MAX_FILE_BYTES / 1024 / 1024) + " MB.");
            }
            return content;
        } catch (IOException ex) {
            throw new ImportRejectedException(ImportRejectedException.CORRUPT_FILE,
                    "Không đọc được tệp " + filename, ex);
        }
    }

    private static ImportRejectedException thanhLoiTuChoi(Exception ex, String filename) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        // POI bao cao zip bomb bang mot IOException co chu "Zip bomb detected".
        if (msg.contains("Zip bomb") || msg.contains("ratio")) {
            return new ImportRejectedException(ImportRejectedException.UNSAFE_FILE,
                    "Tệp " + filename + " giải nén ra lớn bất thường và bị từ chối vì lý do an"
                            + " toàn. Nếu đây là tệp thật thì xuất lại từ Excel rồi tải lên.", ex);
        }
        return new ImportRejectedException(ImportRejectedException.CORRUPT_FILE,
                "Không đọc được tệp " + filename + ": " + msg, ex);
    }

}
