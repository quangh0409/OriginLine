package vn.giapha.dataimport.infrastructure.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import vn.giapha.dataimport.domain.ImportRejectedException;
import vn.giapha.dataimport.domain.RawRow;
import vn.giapha.dataimport.domain.TextNormalizer;

/**
 * Gom một trang tính thành danh sách {@link RawRow}, <b>theo dòng</b>, không giữ cả trang trong bộ
 * nhớ dưới dạng đối tượng POI.
 *
 * <h2>Ghép cột theo tên tiêu đề, không theo vị trí</h2>
 * Người nhập chèn thêm một cột ghi chú ở giữa là chuyện chắc chắn sẽ xảy ra. Ghép theo chỉ số cột
 * thì cả tệp lệch một ô và mọi họ tên biến thành mã cha — hỏng im lặng và thảm hoạ. Vì thế lớp này
 * tìm <b>dòng tiêu đề</b> trong vài dòng đầu rồi ánh xạ chữ cái cột sang tên cột.
 *
 * <h2>Ô trống không được bỏ qua âm thầm</h2>
 * SAX chỉ gọi lại cho những ô <b>có nội dung</b>, nên nếu chỉ nối tiếp các lần gọi thì một dòng
 * thiếu ô sẽ bị dồn cột. Lớp này ánh xạ theo chữ cái cột lấy từ địa chỉ ô, nên ô trống đơn giản là
 * vắng mặt trong bản đồ chứ không đẩy cột nào.
 */
class SheetRowCollector implements SheetContentsHandler {

    private final int maxRows;
    private final String sheetLabel;
    private final int headerScanRows;
    private final HeaderMatcher headerMatcher;

    /** chữ cái cột -> tên cột chính tắc. Rỗng cho tới khi tìm thấy dòng tiêu đề. */
    private final Map<String, String> theoCot = new LinkedHashMap<>();

    private final List<String> headers = new ArrayList<>();
    private final List<RawRow> rows = new ArrayList<>();

    private Map<String, String> rawHienTai = new LinkedHashMap<>();
    private int rowNoHienTai;
    private boolean daCoTieuDe;

    /** Ghép một ô tiêu đề thành tên cột chính tắc; trả {@code null} nếu không nhận ra. */
    interface HeaderMatcher {
        String match(String rawHeader);
    }

    SheetRowCollector(String sheetLabel, int maxRows, int headerScanRows, HeaderMatcher matcher) {
        this.sheetLabel = sheetLabel;
        this.maxRows = maxRows;
        this.headerScanRows = headerScanRows;
        this.headerMatcher = matcher;
    }

    @Override
    public void startRow(int rowNum) {
        rawHienTai = new LinkedHashMap<>();
        // POI dem tu 0; nguoi nhap doc so dong cua Excel, dem tu 1.
        rowNoHienTai = rowNum + 1;
    }

    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
        if (cellReference == null) {
            return;
        }
        String cot = new CellReference(cellReference).getCellRefParts()[2];
        String value = XlsxGuards.capCell(formattedValue);
        if (!daCoTieuDe) {
            rawHienTai.put(cot, value);
            return;
        }
        String ten = theoCot.get(cot);
        if (ten != null) {
            rawHienTai.put(ten, value);
        }
    }

    @Override
    public void endRow(int rowNum) {
        if (!daCoTieuDe) {
            if (thuNhanTieuDe()) {
                daCoTieuDe = true;
            } else if (rowNum + 1 >= headerScanRows) {
                throw new ImportRejectedException(ImportRejectedException.MISSING_COLUMN,
                        "Không tìm thấy dòng tiêu đề trên trang " + sheetLabel + " trong "
                                + headerScanRows + " dòng đầu. Dòng tiêu đề phải có các cột như"
                                + " Mã, Họ tên — đừng xoá nó.");
            }
            return;
        }
        if (rawHienTai.isEmpty()) {
            return;
        }
        if (rows.size() >= maxRows) {
            throw new ImportRejectedException(ImportRejectedException.TOO_MANY_ROWS,
                    "Trang " + sheetLabel + " vượt quá " + maxRows + " dòng. Không phải vì máy"
                            + " không chịu nổi, mà vì không ai đối soát nổi một tệp lớn thế này với"
                            + " cuốn sổ đặt cạnh — tách thành nhiều tệp nhỏ hơn.");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        rawHienTai.forEach((k, v) -> normalized.put(k, TextNormalizer.normalize(v)));
        RawRow row = new RawRow(rowNoHienTai, rawHienTai, normalized);
        if (!row.rong()) {
            rows.add(row);
        }
    }

    /** Dòng hiện tại có phải dòng tiêu đề không; nếu phải thì dựng luôn bản đồ cột. */
    private boolean thuNhanTieuDe() {
        Map<String, String> ungVien = new LinkedHashMap<>();
        List<String> tieuDeGoc = new ArrayList<>();
        for (Map.Entry<String, String> e : rawHienTai.entrySet()) {
            String ten = headerMatcher.match(e.getValue());
            if (ten != null) {
                ungVien.put(e.getKey(), ten);
            }
            if (e.getValue() != null && !e.getValue().isBlank()) {
                tieuDeGoc.add(e.getValue());
            }
        }
        // Hai cot nhan ra duoc la du de ket luan: mot dong tieu de that luon co it nhat Ma va Ho ten,
        // con mot dong tieu de gia (vi du dong tua "DANH SACH NHAN KHAU CHI AT") thi khong.
        if (ungVien.size() < 2) {
            return false;
        }
        theoCot.putAll(ungVien);
        headers.addAll(tieuDeGoc);
        return true;
    }

    @Override
    public void headerFooter(String text, boolean isHeader, String tagName) {
        // Dau trang / chan trang khong phai du lieu.
    }

    public void hyperlinkCell(String cellReference, String text, String location, String tooltip,
                              XSSFComment comment) {
        cell(cellReference, text, comment);
    }

    boolean daCoTieuDe() {
        return daCoTieuDe;
    }

    /** Các tên cột chính tắc nhận ra được — dùng để báo thiếu cột bắt buộc. */
    java.util.Collection<String> tenCotNhanRa() {
        return theoCot.values();
    }

    List<String> headers() {
        return List.copyOf(headers);
    }

    List<RawRow> rows() {
        return List.copyOf(rows);
    }

}
