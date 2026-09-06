package vn.giapha.genealogy.api.graphql;

/**
 * Tham số phân trang của GraphQL, ánh xạ input {@code PageInput}.
 *
 * <p>Cố ý dùng offset ({@code page} 0-based + {@code size}) chứ không phải Relay cursor
 * connection: quy mô hàng vạn nhân khẩu không cần tới nó, và giữ một kiểu phân trang duy nhất cho
 * cả REST lẫn GraphQL thì frontend đỡ phải viết hai lớp xử lý.</p>
 */
public record PageInputGql(Integer page, Integer size, String sort) {

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null || size < 1 ? 20 : Math.min(size, 100);
    }

    public static PageInputGql orDefault(PageInputGql value) {
        return value == null ? new PageInputGql(0, 20, null) : value;
    }
}
