package vn.giapha.content.infrastructure.jpa;

import java.util.List;
import java.util.StringJoiner;
import vn.giapha.shared.vo.BranchPath;

/**
 * Dựng literal mảng của Postgres từ danh sách phạm vi: {@code {goc.chi_giap,goc.chi_at}}.
 *
 * <h2>Vì sao một chuỗi, không phải một mảng Java</h2>
 * Truyền mảng Java qua native query của Hibernate cho hành vi khác nhau giữa các phiên bản driver;
 * một chuỗi rồi {@code CAST(... AS ltree[])} thì không phụ thuộc gì cả. Đây là cùng cách
 * {@code ChangeRequestRepositoryAdapter} đã làm, và lý do được ghi lại ở đó.
 *
 * <h2>Vì sao không cần thoát ký tự</h2>
 * {@link BranchPath} đã bảo đảm mọi nhãn chỉ gồm {@code [A-Za-z0-9_]} ngăn cách bởi dấu chấm, nên
 * không có gì để tiêm. Danh sách rỗng cho ra {@code {}} — một mảng rỗng, và
 * {@code '{}'::ltree[] @> x} là {@code false}, đúng nghĩa <b>"không phạm vi nào"</b>. Đây là chỗ
 * duy nhất mà lỗi kinh điển "rỗng nghĩa là toàn quyền" có thể chui vào, nên nó được nói ra ở đây.
 */
final class ScopeLiteral {

    private ScopeLiteral() {
    }

    static String of(List<BranchPath> scopes) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        if (scopes != null) {
            for (BranchPath path : scopes) {
                if (path != null) {
                    joiner.add(path.value());
                }
            }
        }
        return joiner.toString();
    }
}
