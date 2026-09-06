package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.domain.GraphNodeRef;

/**
 * <b>Khung xương</b> của một lượt duyệt phả đồ: chỉ gồm id đỉnh và độ sâu, không một mẩu hồ sơ nào.
 *
 * <h2>Vì sao chỉ cache thứ này</h2>
 * Phần đắt của {@code GET /tree} là phép duyệt Cypher; phần rẻ là nạp hồ sơ theo khoá chính. Quan
 * trọng hơn: hồ sơ trả về <b>khác nhau theo người gọi</b> vì phân tầng riêng tư, nên cache thứ đã
 * lọc thì sớm muộn cũng có người nhận bản cache của một vai khác - một lỗi rò rỉ dữ liệu không để
 * lại dấu vết nào trong log. Khung xương không chứa dữ liệu cá nhân nên dùng chung được cho mọi
 * vai, còn việc lọc thì luôn chạy tươi trên mọi phản hồi.
 *
 * <p>Mã hoá dạng văn bản thuần để cache có thể là bất cứ thứ gì lưu được chuỗi (Redis ở Giai đoạn
 * 1) mà không cần một lớp serializer nào biết tới kiểu Java này.</p>
 */
public record TreeSkeleton(UUID rootId, int depth, TreeDirection direction, List<GraphNodeRef> nodes,
                           boolean truncated, List<UUID> truncatedNodeIds) {

    private static final String VERSION = "v1";
    private static final String LINE_SEPARATOR = "\n";

    public TreeSkeleton {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        truncatedNodeIds = truncatedNodeIds == null ? List.of() : List.copyOf(truncatedNodeIds);
    }

    public List<UUID> nodeIds() {
        return nodes.stream().map(GraphNodeRef::personId).toList();
    }

    public String encode() {
        StringBuilder text = new StringBuilder();
        text.append(VERSION).append('|').append(rootId).append('|').append(depth).append('|')
                .append(direction.name()).append('|').append(truncated ? '1' : '0').append('|')
                .append(String.join(",", truncatedNodeIds.stream().map(UUID::toString).toList()));
        for (GraphNodeRef node : nodes) {
            text.append(LINE_SEPARATOR).append(node.personId()).append(':').append(node.depth());
        }
        return text.toString();
    }

    /**
     * Giải mã bản cache. Trả {@code null} khi chuỗi hỏng hoặc thuộc phiên bản định dạng khác - gọi
     * lại phép duyệt còn hơn dựng một cây sai từ dữ liệu không đọc nổi.
     */
    public static TreeSkeleton decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String[] lines = encoded.split(LINE_SEPARATOR);
            String[] header = lines[0].split("\\|", -1);
            if (header.length < 6 || !VERSION.equals(header[0])) {
                return null;
            }
            List<UUID> truncatedIds = new ArrayList<>();
            if (!header[5].isBlank()) {
                for (String raw : header[5].split(",")) {
                    truncatedIds.add(UUID.fromString(raw));
                }
            }
            List<GraphNodeRef> nodes = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                int separator = lines[i].lastIndexOf(':');
                if (separator > 0) {
                    nodes.add(new GraphNodeRef(UUID.fromString(lines[i].substring(0, separator)),
                            Integer.parseInt(lines[i].substring(separator + 1))));
                }
            }
            return new TreeSkeleton(UUID.fromString(header[1]), Integer.parseInt(header[2]),
                    TreeDirection.valueOf(header[3]), nodes, "1".equals(header[4]), truncatedIds);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
