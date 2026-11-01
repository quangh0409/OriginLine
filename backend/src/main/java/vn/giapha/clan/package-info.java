/**
 * Bounded context <b>clan</b> — hồ sơ dòng họ và lối <b>"Khởi tạo dòng họ"</b> cho quản trị hệ
 * thống (quyết định của chủ dự án 26/09/2026).
 *
 * <h2>Vì sao có context này</h2>
 * Trước nó hệ thống là "một bản cài = một dòng họ" mà không nói dòng họ ấy là ai: tên họ nằm
 * trong dữ liệu mẫu (tên chi gốc {@code DONG_HO}), luật danh xưng cấu hình bằng SQL, mã mời phát
 * bằng script. Không có màn hình nào để triển khai một dòng họ mới. Context này giữ <b>một</b>
 * nguồn chân lý — {@code clan_profile} — và điều phối bốn việc khởi tạo: hồ sơ, chi gốc, bộ luật
 * danh xưng tầng {@code CLAN}, tài khoản Hội đồng Tộc biểu; cộng một bảng "còn thiếu bước nào".
 *
 * <h2>{@code clan_profile} là dòng đầu tiên của bảng {@code clan} ở bước 2</h2>
 * Bước 2 (nhiều dòng họ trên một bản cài) đang được BA thiết kế song song. Mọi thứ ở đây được
 * dựng để bước ấy <b>kế thừa</b> chứ không viết lại: id là UUID ổn định (sẽ thành
 * {@code clan_id}), nhãn gốc {@code ltree} là duy nhất, và không lớp nào ngoài
 * {@code ClanProfileRepository.find()} giả định "chỉ có một dòng". Xem đầu tệp
 * {@code V23__clan_profile.sql} để biết bước 2 thêm gì.
 *
 * <h2>Phụ thuộc — chỉ đi RA, không ai phụ thuộc NGƯỢC vào đây</h2>
 * <ul>
 *   <li>{@code genealogy} — qua mặt tiền {@code "goc-dong-ho"} ({@code ClanRootService}): tạo /
 *       đổi tên / đổi nhãn chi gốc, đếm nhân khẩu và chi. Không đọc bảng {@code branch} ở đây.</li>
 *   <li>{@code kinship} — qua mặt tiền {@code "bo-luat-dong-ho"} ({@code ClanRuleSetService}):
 *       dựng/nối lại bộ luật tầng {@code CLAN}.</li>
 *   <li>{@code membership} — qua {@code membership.application} (đã là named interface): tạo tài
 *       khoản Hội đồng, đếm Hội đồng và mã mời còn hiệu lực. Chiều ngược (membership cần <i>tên
 *       dòng họ</i> cho thư mời) được <b>đảo phụ thuộc</b>: {@code membership} khai cổng
 *       {@code ClanNameProvider}, context này hiện thực ở {@code infrastructure.membership}. Một
 *       cạnh {@code membership → clan} sẽ tạo chu trình và làm {@code ModularityTests} đỏ.</li>
 *   <li>{@code audit} — ghi {@code audit_log} cho mọi lần ghi hồ sơ.</li>
 * </ul>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Domain là POJO thuần — không annotation Spring/JPA.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Clan — Hồ sơ & khởi tạo dòng họ")
package vn.giapha.clan;
