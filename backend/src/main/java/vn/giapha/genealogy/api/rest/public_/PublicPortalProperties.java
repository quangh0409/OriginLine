package vn.giapha.genealogy.api.rest.public_;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Trần và hạn mức của cổng thông tin công khai, tiền tố {@code giapha.public-portal}.
 *
 * <h2>Vì sao bề mặt công khai có trần riêng, chặt hơn hẳn bề mặt thành viên</h2>
 * "Người đã khuất là công khai" và "ai cũng tải được cả cuốn gia phả 1.500 người về máy" là hai
 * chuyện khác hẳn nhau. Một dòng họ số hoá phả hệ để con cháu tra cứu, không phải để bất kỳ ai
 * dựng lại toàn bộ cấu trúc huyết thống của họ bằng vài trăm lượt gọi. Vì thế cổng công khai:
 * <ul>
 *   <li>không có endpoint liệt kê toàn bộ nhân khẩu — muốn xem cây thì phải <b>biết trước</b> một
 *       {@code rootId};</li>
 *   <li>siết {@code depth} và {@code maxNodes} xuống thấp hơn nhiều so với bản thành viên
 *       ({@code 10} / {@code 2000}), nên một lượt gọi không kéo về được cả một chi;</li>
 *   <li>chặn phân trang sâu ở tìm kiếm: qua {@link #getMaxSearchPage()} là hết đường, không thể
 *       lật trang cho tới khi hết dòng họ;</li>
 *   <li>đếm hạn mức theo <b>chi phí</b>, không theo số lượt gọi — xem {@link PublicRateLimitFilter}.</li>
 * </ul>
 *
 * <p><b>Mặc định nằm ngay trong lớp này</b>, không nằm ở {@code application.yml}: dựng một môi
 * trường mới mà quên chép cấu hình thì cổng công khai vẫn phải có trần, chứ không phải mở toang.
 * YAML chỉ để <b>ghi đè</b>.</p>
 */
@Component
@ConfigurationProperties(prefix = "giapha.public-portal")
public class PublicPortalProperties {

    /** Độ sâu mặc định khi client không nói gì. */
    private int defaultTreeDepth = 3;

    /** Trần độ sâu phả đồ công khai. Bản thành viên là 10. */
    private int maxTreeDepth = 4;

    /** Trần số node một lượt. Bản thành viên là 2000. */
    private int maxTreeNodes = 200;

    /** Số kết quả tìm kiếm mỗi trang. */
    private int maxSearchSize = 20;

    /** Trang cuối cùng còn được phép hỏi (0-based) — chặn phân trang sâu để quét. */
    private int maxSearchPage = 4;

    /** Độ dài tối thiểu của từ khoá: một ký tự là một phép liệt kê trá hình. */
    private int minQueryLength = 2;

    /**
     * Hạn mức chi phí mỗi phút cho một địa chỉ IP.
     *
     * <p>Đơn vị là <b>điểm chi phí</b> chứ không phải lượt gọi: xem bảng giá trong
     * {@link PublicRateLimitFilter}.</p>
     */
    private int costPerMinute = 120;

    /** Hạn mức chi phí mỗi ngày cho một địa chỉ IP — chặn kiểu quét chậm mà đều. */
    private int costPerDay = 3000;

    /** Số IP theo dõi đồng thời; vượt qua thì quét dọn, chống chính bộ đếm thành lỗ hổng bộ nhớ. */
    private int maxTrackedClients = 20_000;

    /** Thời gian sống của {@code Cache-Control: public}, tính bằng giây. */
    private int cacheSeconds = 300;

    /** Tắt hạn mức — <b>chỉ</b> cho môi trường test và dev, đừng bao giờ tắt trên môi trường thật. */
    private boolean rateLimitEnabled = true;

    public int getDefaultTreeDepth() {
        return defaultTreeDepth;
    }

    public void setDefaultTreeDepth(int defaultTreeDepth) {
        this.defaultTreeDepth = defaultTreeDepth;
    }

    public int getMaxTreeDepth() {
        return maxTreeDepth;
    }

    public void setMaxTreeDepth(int maxTreeDepth) {
        this.maxTreeDepth = maxTreeDepth;
    }

    public int getMaxTreeNodes() {
        return maxTreeNodes;
    }

    public void setMaxTreeNodes(int maxTreeNodes) {
        this.maxTreeNodes = maxTreeNodes;
    }

    public int getMaxSearchSize() {
        return maxSearchSize;
    }

    public void setMaxSearchSize(int maxSearchSize) {
        this.maxSearchSize = maxSearchSize;
    }

    public int getMaxSearchPage() {
        return maxSearchPage;
    }

    public void setMaxSearchPage(int maxSearchPage) {
        this.maxSearchPage = maxSearchPage;
    }

    public int getMinQueryLength() {
        return minQueryLength;
    }

    public void setMinQueryLength(int minQueryLength) {
        this.minQueryLength = minQueryLength;
    }

    public int getCostPerMinute() {
        return costPerMinute;
    }

    public void setCostPerMinute(int costPerMinute) {
        this.costPerMinute = costPerMinute;
    }

    public int getCostPerDay() {
        return costPerDay;
    }

    public void setCostPerDay(int costPerDay) {
        this.costPerDay = costPerDay;
    }

    public int getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(int maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }
}
