package vn.giapha.genealogy.application.view;

import java.util.List;

/**
 * Một trang danh bạ, kèm hai khối mà một danh sách thường không có.
 *
 * @param coverage tử số/mẫu số của câu "218 / 627 người còn sống đã điền"
 * @param facets   các tỉnh/nghề/chi <b>có thật</b> trong danh bạ của người gọi
 */
public record DirectoryPageView(PageView<DirectoryEntryView> page,
                                DirectoryCoverageView coverage,
                                DirectoryFacetsView facets) {

    public List<DirectoryEntryView> items() {
        return page.items();
    }

    /**
     * Tử số / mẫu số của câu "218 / 627 người còn sống đã điền".
     *
     * <h2>Vì sao con số này phải tồn tại</h2>
     * Danh bạ thưa là chuyện bình thường: mặc định của mô hình đồng thuận là <b>kín</b>, nên ngày
     * đầu tiên danh bạ gần như rỗng. Không có hai con số này thì màn hình trông y hệt một màn hình
     * hỏng, và phản ứng đúng của người dùng — "hệ thống này lỗi" — lại là phản ứng sai.
     * Có chúng thì màn hình tự giải thích: chưa ai điền, và bạn có thể là người điền.
     *
     * @param sharedCount số người còn sống hiện ra trong danh bạ của người gọi, <b>bỏ qua mọi bộ
     *                    lọc</b> — nếu trừ đi phần bị lọc thì con số sẽ tụt xuống mỗi lần người
     *                    dùng chọn một tỉnh, và câu "218 / 627" mất nghĩa
     * @param livingCount tổng số người còn sống mà người gọi được biết là tồn tại. Không phải điều
     *                    bí mật với thành viên: Tầng 1 vốn đã cho họ thấy tên và đời của mọi người
     *                    còn sống, nên đây là con số họ đếm được bằng tay. Với Khách thì không có
     *                    câu hỏi nào ở đây cả — Khách nhận {@code 401}.
     */
    public record DirectoryCoverageView(long sharedCount, long livingCount) {
    }

    /**
     * Giá trị facet và số lần nó xuất hiện <b>trong danh bạ của người gọi</b>.
     *
     * <p>Đếm trên tập đã lọc riêng tư, nên một tỉnh chỉ có mặt khi có ít nhất một người đã mở nhóm
     * {@code residenceProvince} cho người gọi. Đếm trên tập thô sẽ biến chính khối facet thành kênh
     * rò rỉ: "Hà Nội (37)" trong khi danh sách chỉ hiện 4 người là đã nói ra 33 người bị ẩn.</p>
     */
    public record DirectoryFacetValueView(String value, long count) {
    }

    /** Facet chi/ngành mang thêm {@code path} để giao diện thụt đầu dòng theo cây. */
    public record DirectoryBranchFacetView(java.util.UUID id, String name, String path, long count) {
    }

    /**
     * Ba trục lọc.
     *
     * <p>Tất cả đều tính trên tập <b>không áp bộ lọc</b>, nên chọn một tỉnh xong người dùng vẫn
     * còn thấy các tỉnh khác để đổi ý — facet tính trên tập đã lọc sẽ tự bóp mình còn đúng một
     * lựa chọn và biến bộ lọc thành ngõ cụt.</p>
     */
    public record DirectoryFacetsView(List<DirectoryFacetValueView> provinces,
                                      List<DirectoryFacetValueView> occupations,
                                      List<DirectoryBranchFacetView> branches) {

        public static DirectoryFacetsView empty() {
            return new DirectoryFacetsView(List.of(), List.of(), List.of());
        }
    }
}
