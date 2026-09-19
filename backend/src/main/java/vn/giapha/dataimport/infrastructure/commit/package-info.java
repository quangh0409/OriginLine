/**
 * Adapter <b>ghi</b> — chỗ duy nhất của {@code dataimport} gọi sang {@code genealogy} để viết vào
 * phả thật.
 *
 * <h2>Một tệp, không một dòng logic nghiệp vụ</h2>
 * {@code PhaWriteAdapter} dựng tham số, gọi facade {@code GenealogyBulkWriter} (named interface
 * {@code "ghi-pha"}), rồi ánh xạ kết quả về kiểu của {@code dataimport}. Không một câu SQL, không
 * một câu Cypher. Nhờ thế cả {@code domain} lẫn {@code application} của context này không biết gì
 * về {@code genealogy}.
 *
 * <h2>Vì sao tách khỏi {@code infrastructure.genealogy}</h2>
 * Gói bên cạnh nhốt các phép <b>đọc</b> mà đường nhập liệu mượn của {@code genealogy}: dò trùng và
 * kỵ húy. Gói này nhốt phép <b>ghi</b>. Tách ra vì hai bề mặt tiếp xúc có rủi ro rất khác nhau —
 * một phép dò sai thì sinh ra một cảnh báo thừa, một phép ghi sai thì sinh ra một cây phả hệ lệch
 * mà không có gì báo — nên chúng đáng được đọc và duyệt riêng.
 *
 * <h2>Điều tuyệt đối KHÔNG được làm ở đây</h2>
 * Viết thẳng vào {@code person}, {@code relationship} hay đồ thị AGE "cho nhanh", dù chỉ một lần,
 * dù chỉ một cột. Bất biến <b>cạnh AGE và dòng {@code relationship} ghi trong cùng một
 * transaction</b> ({@code V2__core.sql} mục 2.4, ghim bằng {@code GraphRelationalConsistencyIT})
 * sống ở đúng một chỗ trong hệ thống. Một đường ghi thứ hai ở đây chính là cách nó bị vi phạm sáu
 * tháng sau, bởi một người không đọc mục 2.4 — và triệu chứng sẽ là phả đồ (đọc từ đồ thị) và báo
 * cáo (đọc từ bảng) nói hai điều khác nhau, không ai biết bên nào đúng.
 *
 * <p><b>Ngoại lệ duy nhất, và nó được nói ra ở chỗ khác:</b> hai cột vô hướng của bảng
 * {@code person} mà aggregate của {@code genealogy} không chở nổi — {@code native_place_code} (do
 * chính V9 thêm) và {@code death_lunar} <b>khi không rõ năm</b>. Chúng được vá qua
 * {@code CommitLedgerPort.vaCotNgoai}, ở cuối cùng transaction ghi, và <b>không</b> chạm tới
 * {@code relationship} hay đồ thị. Javadoc của cổng ấy giải thích đầy đủ lý do.</p>
 */
package vn.giapha.dataimport.infrastructure.commit;
