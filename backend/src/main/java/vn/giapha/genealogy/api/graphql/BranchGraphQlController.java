package vn.giapha.genealogy.api.graphql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import vn.giapha.genealogy.api.rest.GenealogyDtoMapper;
import vn.giapha.genealogy.api.rest.dto.BranchRefDto;
import vn.giapha.genealogy.application.BranchQueryService;
import vn.giapha.genealogy.application.view.BranchRef;

/**
 * Resolver GraphQL cho {@code Query.branches} và {@code Branch.memberCount}.
 *
 * <h2>Vì sao chỉ có hai trường ở đây</h2>
 * Client hỏi đúng năm trường — {@code id name path region memberCount}. Bốn trường đầu đọc thẳng
 * từ {@link BranchRefDto} nên không cần resolver; {@code memberCount} thì có. Bốn trường còn lại
 * của {@code type Branch} ({@code parent}, {@code children}, {@code head}, {@code persons}) cố ý
 * <b>chưa</b> có resolver: GraphQL không đòi mọi trường phải có người trả lời nếu không ai hỏi, và
 * riêng {@code head}/{@code persons} kéo theo trọn bộ lọc công bố nhân khẩu — một hạng mục riêng,
 * không phải phần thêm vào của ô chọn phạm vi.
 *
 * <h2>Nguồn dữ liệu dùng chung với REST</h2>
 * {@link BranchRefDto} chính là kiểu mà {@code Person.branch} đang trả về, nên
 * {@link #memberCount} tự động phục vụ cả {@code person { branch { memberCount } }} — một bản cài
 * đặt, không phải hai.
 *
 * <h2>{@code @BatchMapping} chứ không phải {@code @SchemaMapping}</h2>
 * Một lượt {@code branches} trả 17 chi ở dòng họ đang vận hành. Đếm lẻ từng chi nghĩa là 17 lần
 * nạp toàn bộ nhân khẩu — vì bộ đếm chạy <b>sau</b> bộ lọc riêng tư nên mỗi lần đếm là một lần
 * nạp thật. Gom vào một lượt thì cả phản hồi chỉ nạp một lần.
 */
@Controller
public class BranchGraphQlController {

    private final BranchQueryService branchQuery;

    public BranchGraphQlController(BranchQueryService branchQuery) {
        this.branchQuery = branchQuery;
    }

    /**
     * Cây chi/ngành, sắp ổn định theo {@code path} ({@code ltree} = thứ tự cây).
     *
     * <p>{@code rootPath} cắt một cây con theo ngữ nghĩa {@code <@}: chính chi ấy <b>và</b> mọi
     * nhánh dưới nó. Bỏ trống thì trả cả dòng họ.</p>
     */
    @QueryMapping
    public List<BranchRefDto> branches(@Argument String rootPath) {
        return branchQuery.list(rootPath).stream().map(GenealogyDtoMapper::toDto).toList();
    }

    /**
     * Số nhân khẩu trong chi <b>và các nhánh con</b>, đã qua bộ lọc phân tầng riêng tư của
     * <i>người đang gọi</i>.
     *
     * <p>Hai người gọi khác vai nhìn cùng một chi sẽ thấy hai con số khác nhau, và đó là hành vi
     * đúng — xem {@code BranchQueryService.memberCounts}. Đừng cache giá trị này theo chi: nội
     * dung phụ thuộc người gọi.</p>
     */
    @BatchMapping(typeName = "Branch", field = "memberCount")
    public Map<BranchRefDto, Integer> memberCount(List<BranchRefDto> branches) {
        List<BranchRef> refs = new ArrayList<>(branches.size());
        for (BranchRefDto dto : branches) {
            refs.add(new BranchRef(dto.id(), dto.name(), dto.path(), dto.region()));
        }
        Map<UUID, Integer> counts = branchQuery.memberCounts(refs);
        Map<BranchRefDto, Integer> result = new LinkedHashMap<>();
        for (BranchRefDto dto : branches) {
            // getOrDefault: memberCount la Int! nen mot chi khong co ai nhin thay duoc phai tra 0,
            // khong duoc vang mat - vang mat o day thanh loi "non null returned null" cho ca truy van.
            result.put(dto, counts.getOrDefault(dto.id(), 0));
        }
        return result;
    }
}
