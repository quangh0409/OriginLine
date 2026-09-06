package vn.giapha.kinship.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Tính tổ chung gần nhất từ <b>tập cạnh cha–con</b>, thuần trong bộ nhớ.
 *
 * <p>Tách khỏi adapter là có chủ ý: {@code AgeLcaAdapter} chỉ còn việc kéo tập cạnh tổ tiên bằng
 * một câu Cypher rồi giao cho lớp này, còn toàn bộ phần dễ sai (chọn LCA khi hoà, dựng lại đường
 * đi, cờ con nuôi, bỏ qua người đã xoá mềm) nằm ở domain và test được không cần CSDL. Fixture đồ
 * thị trong test dùng lại đúng lớp này nên hành vi của test và của production không thể lệch nhau.</p>
 *
 * <p><b>Phá hoà phải tất định.</b> Anh chị em ruột có cả cha lẫn mẹ là tổ chung ở khoảng cách 1–1;
 * nếu để CSDL trả bừa thì cùng một câu hỏi sẽ cho hai đường quan hệ khác nhau ở hai lần gọi. Quy
 * ước: tổng số bậc nhỏ nhất → ưu tiên tổ chung là NAM (bên nội, hợp lối gọi truyền thống) → id nhỏ
 * hơn.</p>
 */
public final class LcaCalculator {

    private LcaCalculator() {
    }

    /**
     * @param egoEdges   cạnh cha–con trong bao đóng tổ tiên của ego (gồm cả các đời trung gian)
     * @param alterEdges cạnh cha–con trong bao đóng tổ tiên của alter
     * @param lookup     tra bản chiếu nhân khẩu; người đã xoá mềm không được chọn làm LCA
     */
    public static Optional<LcaResult> compute(PersonId ego, PersonId alter,
            Collection<ParentEdge> egoEdges, Collection<ParentEdge> alterEdges,
            Function<PersonId, PersonView> lookup) {

        Walk egoWalk = walkUp(ego, egoEdges);
        Walk alterWalk = walkUp(alter, alterEdges);

        Set<String> adoptEdgeMarks = new HashSet<>();
        markAdoptEdges(egoEdges, adoptEdgeMarks);
        markAdoptEdges(alterEdges, adoptEdgeMarks);

        PersonId best = null;
        int bestTotal = Integer.MAX_VALUE;
        boolean bestIsMale = false;

        for (Map.Entry<PersonId, Integer> entry : egoWalk.distance.entrySet()) {
            Integer alterDistance = alterWalk.distance.get(entry.getKey());
            if (alterDistance == null) {
                continue;
            }
            PersonView candidate = lookup.apply(entry.getKey());
            if (candidate != null && candidate.deleted()) {
                // Người đã xoá mềm vẫn được đi XUYÊN QUA nhưng không được làm tổ chung.
                continue;
            }
            int total = entry.getValue() + alterDistance;
            boolean male = candidate != null && candidate.gender() == Gender.MALE;
            if (isBetter(total, male, entry.getKey(), bestTotal, bestIsMale, best)) {
                best = entry.getKey();
                bestTotal = total;
                bestIsMale = male;
            }
        }

        if (best == null) {
            return Optional.empty();
        }

        List<PersonId> egoPath = egoWalk.pathTo(best);
        List<PersonId> alterPath = alterWalk.pathTo(best);
        boolean viaAdoption = usesAdoptEdge(egoPath, adoptEdgeMarks)
                || usesAdoptEdge(alterPath, adoptEdgeMarks);
        return Optional.of(new LcaResult(egoPath, alterPath, viaAdoption));
    }

    private static boolean isBetter(int total, boolean male, PersonId candidate,
            int bestTotal, boolean bestIsMale, PersonId best) {
        if (best == null || total < bestTotal) {
            return true;
        }
        if (total > bestTotal) {
            return false;
        }
        if (male != bestIsMale) {
            return male;
        }
        return candidate.value().compareTo(best.value()) < 0;
    }

    /** Duyệt lên tổ tiên theo BFS, ghi lại khoảng cách và nút liền trước để dựng lại đường đi. */
    private static Walk walkUp(PersonId start, Collection<ParentEdge> edges) {
        Map<PersonId, Set<PersonId>> parents = new HashMap<>();
        for (ParentEdge edge : edges) {
            parents.computeIfAbsent(edge.child(), key -> new TreeSet<>(byId())).add(edge.parent());
        }

        Walk walk = new Walk();
        walk.distance.put(start, 0);
        Deque<PersonId> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            PersonId current = queue.poll();
            int distance = walk.distance.get(current);
            for (PersonId parent : parents.getOrDefault(current, Set.of())) {
                if (walk.distance.containsKey(parent)) {
                    continue;
                }
                walk.distance.put(parent, distance + 1);
                walk.cameFrom.put(parent, current);
                queue.add(parent);
            }
        }
        return walk;
    }

    private static Comparator<PersonId> byId() {
        return Comparator.comparing(PersonId::value);
    }

    private static void markAdoptEdges(Collection<ParentEdge> edges, Set<String> marks) {
        for (ParentEdge edge : edges) {
            if (edge.adopt()) {
                marks.add(edgeKey(edge.child(), edge.parent()));
            }
        }
    }

    /**
     * Một người có thể vừa có cạnh ruột tới cha đẻ vừa có cạnh nuôi tới cha nuôi, nên phải đánh dấu
     * theo <b>cặp cạnh</b> chứ không theo người con — đánh dấu theo người sẽ báo nhầm "qua con nuôi"
     * cho cả đường đi bên nhà cha đẻ.
     */
    private static boolean usesAdoptEdge(List<PersonId> pathUp, Set<String> adoptEdges) {
        for (int i = 0; i < pathUp.size() - 1; i++) {
            if (adoptEdges.contains(edgeKey(pathUp.get(i), pathUp.get(i + 1)))) {
                return true;
            }
        }
        return false;
    }

    private static String edgeKey(PersonId child, PersonId parent) {
        return child.value() + ">" + parent.value();
    }

    private static final class Walk {
        private final Map<PersonId, Integer> distance = new LinkedHashMap<>();
        private final Map<PersonId, PersonId> cameFrom = new HashMap<>();

        List<PersonId> pathTo(PersonId ancestor) {
            List<PersonId> reversed = new ArrayList<>();
            PersonId cursor = ancestor;
            while (cursor != null) {
                reversed.add(cursor);
                cursor = cameFrom.get(cursor);
            }
            List<PersonId> path = new ArrayList<>(reversed.size());
            for (int i = reversed.size() - 1; i >= 0; i--) {
                path.add(reversed.get(i));
            }
            return path;
        }
    }
}
