package vn.giapha.genealogy.api.graphql;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import vn.giapha.genealogy.api.rest.GenealogyDtoMapper;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.api.rest.dto.TreeProjectionDto;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.TreeProjectionService;
import vn.giapha.genealogy.application.command.TreeQuery;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.TreeDirection;

/**
 * Resolver GraphQL cho {@code TreeProjection}.
 *
 * <p>Cùng ngữ nghĩa với {@code GET /api/v1/tree} và dùng chung một use case, nên hai cổng không
 * bao giờ nói hai điều khác nhau về cùng một cây. Khác biệt chỉ ở chỗ ra: GraphQL cho client tự
 * chọn cần gì trong mỗi node, thay vì nhận trọn dạng rút gọn.</p>
 *
 * <p>Node trên schema mang kiểu {@code Person} <b>đầy đủ</b> trong khi projection chỉ giữ dạng rút
 * gọn, nên hồ sơ được nạp bù. Việc này chạy qua {@link BatchMapping} - <b>một</b> truy vấn cho cả
 * projection. Nạp lẻ từng node thì một cây 500 người thành 500 truy vấn, và đó chính là kiểu lỗi
 * làm sập trang phả đồ ở đúng lúc dòng họ đông người nhất.</p>
 */
@Controller
public class TreeGraphQlController {

    private final TreeProjectionService treeProjection;
    private final PersonQueryService personQuery;

    public TreeGraphQlController(TreeProjectionService treeProjection, PersonQueryService personQuery) {
        this.treeProjection = treeProjection;
        this.personQuery = personQuery;
    }

    /**
     * Projection cây phẳng. {@code depth} tối đa 10 và {@code maxNodes} tối đa 2000; vượt trần thì
     * bị kẹp lại và {@code meta.truncated} bật lên, chứ không âm thầm trả cây thiếu.
     */
    @QueryMapping
    public TreeProjectionDto tree(@Argument UUID rootId, @Argument Integer depth,
                                  @Argument TreeDirection direction, @Argument Boolean includeSpouses,
                                  @Argument Integer maxNodes) {
        return GenealogyDtoMapper.toDto(treeProjection.project(new TreeQuery(
                rootId,
                depth == null ? 3 : depth,
                direction == null ? TreeDirection.DESCENDANTS : direction,
                includeSpouses == null || includeSpouses,
                false,
                maxNodes == null ? 500 : maxNodes)));
    }

    @SchemaMapping(typeName = "TreeProjection", field = "root")
    public PersonDto root(TreeProjectionDto projection) {
        return personQuery.find(projection.rootId()).map(GenealogyDtoMapper::toDto).orElse(null);
    }

    /** Nạp hồ sơ đầy đủ cho toàn bộ node trong một lượt - xem javadoc của lớp. */
    @BatchMapping(typeName = "TreeNode", field = "person")
    public Map<TreeProjectionDto.TreeNodeDto, PersonDto> person(
            List<TreeProjectionDto.TreeNodeDto> nodes) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TreeProjectionDto.TreeNodeDto node : nodes) {
            ids.add(node.id());
        }
        Map<UUID, PersonDto> byId = new LinkedHashMap<>();
        for (PersonView view : personQuery.visibleByIds(ids)) {
            byId.put(view.id(), GenealogyDtoMapper.toDto(view));
        }
        Map<TreeProjectionDto.TreeNodeDto, PersonDto> result = new LinkedHashMap<>();
        for (TreeProjectionDto.TreeNodeDto node : nodes) {
            PersonDto person = byId.get(node.id());
            if (person != null) {
                result.put(node, person);
            }
        }
        return result;
    }

    /** {@code childCount} là {@code Int!} trong schema; projection để {@code null} khi chưa đếm được. */
    @SchemaMapping(typeName = "TreeNode")
    public int childCount(TreeProjectionDto.TreeNodeDto node) {
        return node.childCount() == null ? 0 : node.childCount();
    }

    /** Danh sách rỗng thay vì {@code null} cho các trường {@code [UUID!]!} của schema. */
    @SchemaMapping(typeName = "TreeNode")
    public Collection<UUID> parentIds(TreeProjectionDto.TreeNodeDto node) {
        return node.parentIds() == null ? List.of() : node.parentIds();
    }

    @SchemaMapping(typeName = "TreeNode")
    public Collection<UUID> spouseIds(TreeProjectionDto.TreeNodeDto node) {
        return node.spouseIds() == null ? List.of() : node.spouseIds();
    }
}
