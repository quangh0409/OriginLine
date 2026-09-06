package vn.giapha.demo.generator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import vn.giapha.demo.model.DemoBranch;
import vn.giapha.demo.model.DemoDataset;
import vn.giapha.demo.model.DemoName;
import vn.giapha.demo.model.DemoPerson;
import vn.giapha.demo.model.DemoRelation;

/**
 * Sinh toàn bộ cây phả đồ giả trong bộ nhớ. Không chạm CSDL — việc ghi là của
 * {@code vn.giapha.demo.writer.DemoDataWriter}.
 *
 * <h2>Trình tự sinh (không được đảo)</h2>
 * <ol>
 *   <li>{@link #buildBranches()} — chi/ngành, sinh trước vì nhân khẩu phải trỏ vào branch.</li>
 *   <li>Vòng lặp theo đời: gả vợ/chồng cho đời g rồi mới sinh con ra đời g+1. Nhờ thứ tự này mà
 *       khi tính ngày sinh chỉ cần MỘT lượt duyệt theo đúng thứ tự tạo.</li>
 *   <li>{@link #injectStructuralEdgeCases()} — con nuôi, kế tự, đích tôn, con riêng... Những ca
 *       biên KHÔNG phụ thuộc ngày tháng.</li>
 *   <li>{@link #assignDates()} — ngày sinh (từ trên xuống) rồi ngày mất (ràng buộc: cha phải sống
 *       tới lúc con ra đời).</li>
 *   <li>{@link #injectDateDependentEdgeCases()} — Tầng 3, xoá mềm, ẩn danh: đều cần biết ai còn
 *       sống.</li>
 *   <li>{@link #buildNames()} rồi {@link #injectKyHuyCollisions()} — tên thuỵ chỉ đặt cho người đã
 *       mất nên phải sau bước ngày tháng.</li>
 *   <li>{@link #buildRelations()} — quy đổi cấu trúc thành dòng {@code relationship} + cạnh AGE.</li>
 * </ol>
 *
 * <h2>Tính tất định</h2>
 * <p>Bốn nguồn ngẫu nhiên tách biệt theo mục đích (cấu trúc / ngày tháng / tên / ca biên). Tách ra
 * để sửa kho tên không làm xê dịch cấu trúc cây, nếu không mọi id sẽ đổi và test Sprint 3 đỏ hàng
 * loạt. Mọi vòng lặp đều duyệt {@code List} theo thứ tự tạo, không bao giờ duyệt {@code HashMap}.</p>
 */
public final class ClanTreeGenerator {

    /** Tỉ lệ sinh con trai — nhỉnh hơn 50% đúng như thực tế nhân khẩu học. */
    private static final double MALE_RATIO = 0.52;

    private final DemoSeedConfig config;
    private final DemoIds ids;
    private final LunarDateSource lunar;

    private final Random structureRnd;
    private final Random dateRnd;
    private final Random nameRnd;
    private final Random edgeRnd;

    private final List<DemoBranch> branches = new ArrayList<>();
    private final List<DemoPerson> persons = new ArrayList<>();
    private final List<DemoName> names = new ArrayList<>();
    private final List<DemoRelation> relations = new ArrayList<>();
    private final List<Marriage> marriages = new ArrayList<>();
    private final List<Adoption> adoptions = new ArrayList<>();
    private final List<HeirLink> heirs = new ArrayList<>();
    private final Set<UUID> relationKeys = new LinkedHashSet<>();

    private final Map<Integer, List<DemoPerson>> byGeneration = new LinkedHashMap<>();
    private final Map<String, DemoPerson> byKey = new LinkedHashMap<>();
    private final Map<String, UUID> anchors = new LinkedHashMap<>();
    private final Map<String, Integer> stats = new LinkedHashMap<>();

    private DemoBranch rootBranch;
    private final List<DemoBranch> chiBranches = new ArrayList<>();
    private final List<List<DemoBranch>> nhanhBranches = new ArrayList<>();

    private DemoPerson root;
    private int personCounter;

    public ClanTreeGenerator(DemoSeedConfig config, LunarDateSource lunar) {
        this.config = config;
        this.lunar = lunar;
        this.ids = new DemoIds(config.seed());
        this.structureRnd = new Random(config.seed());
        this.dateRnd = new Random(config.seed() * 31 + 7);
        this.nameRnd = new Random(config.seed() * 37 + 13);
        this.edgeRnd = new Random(config.seed() * 41 + 23);
    }

    public DemoDataset generate() {
        buildBranches();
        buildRoot();
        for (int g = 1; g <= config.generations(); g++) {
            assignSpouses(g);
            if (g < config.generations()) {
                buildChildren(g);
            }
        }
        assignBranches();
        injectStructuralEdgeCases();
        assignDeathDates();
        injectDateDependentEdgeCases();
        assignBranchHeads();
        buildNames();
        injectKyHuyCollisions();
        buildRelations();
        computeStats();
        return new DemoDataset(List.copyOf(branches), List.copyOf(persons), List.copyOf(names),
                List.copyOf(relations), Map.copyOf(anchors), Map.copyOf(stats));
    }

    // =================================================================================
    // 1. Chi / ngành / nhánh
    // =================================================================================

    /**
     * Dựng cây chi/ngành. {@code slug} và {@code path} KHÔNG tính ở đây: writer để PostgreSQL sinh
     * bằng {@code vn_slugify()} rồi đọc ngược về. Một hệ thống chỉ được có duy nhất một cách bỏ dấu.
     */
    private void buildBranches() {
        rootBranch = addBranch("goc", "Họ Nguyễn Đình", "DONG_HO", "BAC", null, 0,
                config.rootBirthYear() + 25, "Thuỷ tổ khai canh làng Đông Ngạc");

        String[] chiNames = {"Chi Giáp", "Chi Ất", "Chi Bính", "Chi Đinh"};
        String[] nhanhNames = {"Ngành Trưởng", "Ngành Thứ", "Ngành Út"};
        for (int i = 0; i < config.chiCount(); i++) {
            // Chi Đinh di cư vào Trung Bộ — cố ý để bộ luật danh xưng theo vùng (FR-1.3a) có dữ liệu
            // kiểm thử kế thừa DEFAULT -> REGION -> CLAN -> BRANCH chứ không chỉ một vùng duy nhất.
            String region = i == config.chiCount() - 1 ? "TRUNG" : "BAC";
            DemoBranch chi = addBranch("chi-" + (i + 1), chiNames[i], "CHI", region, rootBranch, i,
                    config.rootBirthYear() + 55, null);
            chiBranches.add(chi);

            List<DemoBranch> nhanhOfChi = new ArrayList<>();
            for (int j = 0; j < config.nhanhPerChi(); j++) {
                nhanhOfChi.add(addBranch("chi-" + (i + 1) + "-nhanh-" + (j + 1), nhanhNames[j],
                        "NGANH", region, chi, j, config.rootBirthYear() + 88, null));
            }
            nhanhBranches.add(nhanhOfChi);
        }
    }

    private DemoBranch addBranch(String key, String name, String kind, String region,
                                 DemoBranch parent, int sortOrder, Integer foundedYear, String note) {
        DemoBranch branch = new DemoBranch(ids.branch(key), key, name, kind, region, parent,
                sortOrder, foundedYear, note);
        branches.add(branch);
        return branch;
    }

    // =================================================================================
    // 2. Nhân khẩu
    // =================================================================================

    private void buildRoot() {
        root = newPerson("thuy-to", 1, true, "MALE");
        root.birthOrder(1);
        assignBirthDate(root);
        root.attributes().put("clan_title", "THUY_TO");
        root.attributes().put("note", "Thuỷ tổ khai canh, đời thứ nhất");
        anchor("THUY_TO", root);
    }

    private DemoPerson newPerson(String key, int generation, boolean bloodline, String gender) {
        DemoPerson person = new DemoPerson(ids.person(key), key, generation, bloodline);
        person.gender(gender);
        person.attributes().put("demo_key", key);
        persons.add(person);
        byKey.put(key, person);
        byGeneration.computeIfAbsent(generation, g -> new ArrayList<>()).add(person);
        return person;
    }

    private List<DemoPerson> generation(int g) {
        return byGeneration.getOrDefault(g, List.of());
    }

    private List<DemoPerson> bloodlineOf(int g) {
        return generation(g).stream().filter(DemoPerson::bloodline).toList();
    }

    // ---------------------------------------------------------------------------------
    // 2.1 Hôn phối — dâu, rể, đa thê, tái hôn
    // ---------------------------------------------------------------------------------

    /**
     * Gả vợ/chồng cho các nhân khẩu huyết thống của đời {@code g}.
     *
     * <p>Người kết hôn vào dòng họ (dâu/rể) là nhân khẩu đầy đủ, có đời bằng đời của vợ/chồng —
     * đúng tập quán ghi gia phả và cũng là thứ mà rule danh xưng cần để suy ra "thím", "mợ", "dượng".</p>
     */
    private void assignSpouses(int g) {
        List<DemoPerson> pool = new ArrayList<>(bloodlineOf(g));
        int quota = config.spouseOf(g);

        // Lượt 0: BỘ KHUNG phải có vợ trước tất cả — xem backboneOf().
        for (DemoPerson person : backboneOf(g)) {
            if (quota == 0) {
                break;
            }
            if (spouseCount(person) == 0) {
                marry(person, 1, false);
                quota--;
            }
        }
        // Lượt 1: bốc theo xác suất trên một bản sao đã xáo — nếu duyệt đúng thứ tự sinh thì toàn bộ
        // phần đuôi danh sách sẽ không ai có vợ/chồng, tạo ra thiên lệch rất lộ trên cây.
        List<DemoPerson> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, structureRnd);
        for (DemoPerson person : shuffled) {
            if (quota == 0) {
                break;
            }
            if (spouseCount(person) > 0) {
                // Đã được gả ở lượt 0. Bỏ qua TRƯỚC khi bốc số: cưới lần nữa với order = 1 sẽ sinh
                // trùng khoá "sp-<key>-1" và vỡ khoá chính của bảng person.
                continue;
            }
            if (structureRnd.nextDouble() < marriageProbability(g, person.male())) {
                marry(person, 1, false);
                quota--;
            }
        }
        // Lượt 2: nếu chỉ tiêu chưa hết thì gả tiếp cho người chưa có đôi, theo thứ tự sinh.
        for (DemoPerson person : pool) {
            if (quota == 0) {
                break;
            }
            if (spouseCount(person) == 0) {
                marry(person, 1, false);
                quota--;
            }
        }
        // Lượt 3: đa thê / tái hôn theo kế hoạch cố định (xem polygamyPlan).
        List<int[]> plan = polygamyPlan(g);
        List<DemoPerson> marriedMales = pool.stream()
                .filter(DemoPerson::male)
                .filter(p -> spouseCount(p) > 0)
                .toList();
        for (int i = 0; i < plan.size() && i < marriedMales.size(); i++) {
            int extra = plan.get(i)[0];
            boolean remarriage = plan.get(i)[1] == 1;
            DemoPerson husband = marriedMales.get(i);
            for (int e = 0; e < extra; e++) {
                marry(husband, spouseCount(husband) + 1, remarriage);
                quota--;
            }
            if (remarriage) {
                marriageOf(husband, 1).ended = true;
                marriageOf(husband, 1).endReason = i % 2 == 0 ? "DEATH" : "DIVORCE";
                husband.attributes().put("marriage_case", "TAI_HON");
                anchor("TAI_HON_" + nextIndex("TAI_HON"), husband);
            } else if (extra >= 1) {
                husband.attributes().put("marriage_case", "DA_THE");
                anchor("DA_THE_" + nextIndex("DA_THE"), husband);
            }
        }
        // Lượt 4: chỉ tiêu vẫn dư (đời trẻ ít người lấy vợ) thì gả thêm cho người chưa có đôi.
        for (DemoPerson person : pool) {
            if (quota <= 0) {
                break;
            }
            if (spouseCount(person) == 0) {
                marry(person, 1, false);
                quota--;
            }
        }
    }

    /**
     * Những người <b>bắt buộc</b> phải lập gia đình ở đời {@code g}: thuỷ tổ, bốn vị tổ chi, mười
     * hai vị trưởng ngành, và người nối dòng đích tôn của dòng sâu bảy đời.
     *
     * <p>Vì sao không phó mặc cho xác suất: {@link #buildSkeletonChildren(int)} <b>bắt buộc</b> phải
     * sinh được con cho những người này để dựng đủ 4 chi, 12 ngành và một dòng chạy thẳng tới đời 7.
     * Nhưng chỉ tiêu dâu/rể của đời 2 chỉ có 5 suất cho 9 người huyết thống, nên rất dễ xảy ra
     * chuyện một vị tổ chi không được bốc trúng và thành ra không có vợ — lúc ấy generator vỡ ngay
     * khi đi tìm mẹ cho đứa con đầu của chi ấy. Đây chính là lỗi đã xảy ra thật ở lượt chạy đầu
     * tiên: bộ khung của cây phả đồ không được để cho may rủi quyết định.</p>
     */
    private List<DemoPerson> backboneOf(int g) {
        List<DemoPerson> backbone = new ArrayList<>();
        if (g == 1) {
            backbone.add(root);
        } else if (g == 2) {
            for (int i = 0; i < config.chiCount(); i++) {
                backbone.add(byKey.get("chi-" + (i + 1) + "-to"));
            }
        } else if (g == 3) {
            for (int i = 0; i < config.chiCount(); i++) {
                for (int j = 0; j < config.nhanhPerChi(); j++) {
                    backbone.add(byKey.get("chi-" + (i + 1) + "-nhanh-" + (j + 1) + "-to"));
                }
            }
        } else {
            backbone.add(byKey.get("deep-g" + g));
        }
        backbone.removeIf(person -> person == null);
        return backbone;
    }

    /**
     * Kế hoạch đa thê / tái hôn theo đời: mỗi phần tử là {@code {số vợ thêm, có phải tái hôn}} áp
     * cho người đàn ông đã có vợ thứ i của đời đó.
     *
     * <p>Cố định chứ không ngẫu nhiên: plan §10 yêu cầu tối thiểu 3 ca đa thê và 2 ca tái hôn +
     * con riêng. Thả cho xác suất quyết định thì có ngày seed đổi là ca biên biến mất, và test
     * kinship mất chỗ dựa mà không ai biết.</p>
     */
    private List<int[]> polygamyPlan(int g) {
        return switch (g) {
            case 1 -> List.of(new int[] {1, 0});                       // thuỷ tổ hai bà
            case 3 -> List.of(new int[] {2, 0}, new int[] {1, 0});     // một cụ ba bà, một cụ hai bà
            case 4 -> List.of(new int[] {1, 0}, new int[] {1, 1});     // một đa thê, một tái hôn
            case 5 -> List.of(new int[] {1, 1});                       // tái hôn
            case 6 -> List.of(new int[] {1, 0});
            default -> List.of();
        };
    }

    private double marriageProbability(int generation, boolean male) {
        if (generation <= 5) {
            return male ? 0.93 : 0.62;
        }
        if (generation == 6) {
            return male ? 0.88 : 0.72;
        }
        return male ? 0.42 : 0.38; // đời 7 còn nhiều người trẻ chưa lập gia đình
    }

    /**
     * Tạo người phối ngẫu cho {@code clanMember} và ghi nhận cuộc hôn nhân.
     * Vợ/chồng luôn mang họ khác — đây chính là các ca "dâu" và "rể".
     */
    private Marriage marry(DemoPerson clanMember, int order, boolean remarriage) {
        String key = "sp-" + clanMember.key() + "-" + order;
        String gender = clanMember.male() ? "FEMALE" : "MALE";
        DemoPerson spouse = newPerson(key, clanMember.generation(), false, gender);
        spouse.birthOrder(1 + structureRnd.nextInt(4)); // thứ tự sinh bên nhà ngoại, vẫn phải có
        spouse.attributes().put("in_law_role", clanMember.male() ? "DAU" : "RE");
        spouse.attributes().put("married_into_branch", true);

        DemoPerson husband = clanMember.male() ? clanMember : spouse;
        DemoPerson wife = clanMember.male() ? spouse : clanMember;
        husband.wives().add(wife);
        wife.husband(husband);
        // Ngày sinh của dâu/rể suy từ người bạn đời, nên chỉ tính được SAU khi hai chiều quan hệ đã
        // nối xong — assignBirthDate() đọc husband()/wives() để biết chênh lệch tuổi.
        assignBirthDate(spouse);

        Marriage marriage = new Marriage(husband, wife, clanMember, order, remarriage);
        marriages.add(marriage);
        return marriage;
    }

    private int spouseCount(DemoPerson person) {
        return person.male() ? person.wives().size() : (person.husband() == null ? 0 : 1);
    }

    private Marriage marriageOf(DemoPerson husband, int order) {
        return marriages.stream()
                .filter(m -> m.husband == husband && m.order == order)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Khong tim thay cuoc hon nhan thu " + order));
    }

    // ---------------------------------------------------------------------------------
    // 2.2 Sinh con — bộ khung (chi/ngành/dòng sâu 7 đời) trước, phần còn lại rải sau
    // ---------------------------------------------------------------------------------

    private void buildChildren(int g) {
        int target = config.bloodlineOf(g + 1);
        int created = buildSkeletonChildren(g);

        List<DemoPerson> parents = parentPool(g);
        if (parents.isEmpty()) {
            return;
        }
        Map<String, Integer> capacity = new LinkedHashMap<>();
        for (DemoPerson parent : parents) {
            capacity.put(parent.key(), familySize(g));
        }
        int guard = 0;
        while (created < target && guard++ < 100) {
            boolean progressed = false;
            for (DemoPerson parent : parents) {
                if (created >= target) {
                    break;
                }
                if (parent.children().size() < capacity.get(parent.key())) {
                    addChild(parent);
                    created++;
                    progressed = true;
                }
            }
            if (!progressed) {
                // Hết chỗ mà chưa đủ chỉ tiêu: nới hạn mức mỗi nhà thêm một con rồi chạy tiếp.
                capacity.replaceAll((key, value) -> value + 1);
            }
        }
    }

    /**
     * Bộ khung bắt buộc phải có, không phó mặc cho xác suất:
     * 4 chi (con trai thuỷ tổ), 3 ngành mỗi chi, và một dòng trực hệ chạy đủ 7 đời để test LCA xa.
     */
    private int buildSkeletonChildren(int g) {
        int created = 0;
        if (g == 1) {
            for (int i = 0; i < config.chiCount(); i++) {
                DemoPerson founder = addChild(root, "chi-" + (i + 1) + "-to", "MALE");
                founder.attributes().put("clan_title", "TO_CHI");
                anchor("CHI_" + (i + 1) + "_TO", founder);
                created++;
            }
        } else if (g == 2) {
            for (int i = 0; i < config.chiCount(); i++) {
                DemoPerson chiFounder = byKey.get("chi-" + (i + 1) + "-to");
                for (int j = 0; j < config.nhanhPerChi(); j++) {
                    DemoPerson founder = addChild(chiFounder,
                            "chi-" + (i + 1) + "-nhanh-" + (j + 1) + "-to", "MALE");
                    founder.attributes().put("clan_title", "TRUONG_NGANH");
                    anchor("CHI_" + (i + 1) + "_NHANH_" + (j + 1) + "_TO", founder);
                    created++;
                }
            }
        } else {
            // Dòng đích tôn chạy thẳng xuống đời 7 — đây là dữ liệu để test LCA khoảng cách xa và
            // chuỗi đích tôn. Không có nó thì việc một nhánh có đủ 7 đời chỉ là may rủi.
            DemoPerson deepFather = g == 3 ? byKey.get("chi-1-nhanh-1-to") : byKey.get("deep-g" + g);
            if (deepFather != null) {
                DemoPerson child = addChild(deepFather, "deep-g" + (g + 1), "MALE");
                child.attributes().put("lineage_note", "Dòng đích tôn — nhánh sâu đủ 7 đời");
                anchor("DEEP_G" + (g + 1), child);
                created++;
            }
        }
        return created;
    }

    /** Cha/mẹ có thể sinh con ở đời {@code g}: đàn ông đã có vợ, cộng vài người mẹ bên ngoại. */
    private List<DemoPerson> parentPool(int g) {
        List<DemoPerson> pool = new ArrayList<>(bloodlineOf(g).stream()
                .filter(DemoPerson::male)
                .filter(p -> !p.wives().isEmpty())
                .toList());
        reserveChildlessCouples(g, pool);
        // FR-1.8: con gái và bên ngoại được ghi nhận ngang bằng con trai. Ở gia phả thật, cháu ngoại
        // thường bị bỏ trắng; ở đây cố ý ghi đủ hai dòng cháu ngoại để rule danh xưng có dữ liệu
        // phân biệt nội/ngoại (cháu nội gọi ông là "ông nội", cháu ngoại gọi là "ông ngoại").
        if (g == 4 || g == 5) {
            bloodlineOf(g).stream()
                    .filter(p -> !p.male() && p.husband() != null)
                    .findFirst()
                    .ifPresent(mother -> {
                        mother.attributes().put("lineage_note", "Có ghi nhận dòng cháu ngoại");
                        anchor("NGOAI_ME_" + (g - 3), mother);
                        pool.add(mother);
                    });
        }
        return pool;
    }

    /**
     * Giữ lại vài cặp vợ chồng <b>không có con</b> ở các đời 4–6.
     *
     * <p>Thuật toán rải con vốn phát cho mọi người đàn ông đã có vợ ít nhất một đứa trước khi vòng
     * lại, nên nếu không chừa chỗ thì <b>không ai</b> tuyệt tự cả — và ba ca biên bắt buộc của plan
     * §10 mất sạch đầu vào cùng lúc: tuyệt tự, kế tự, và con nuôi (người nhận nuôi trong tập quán
     * Việt thường là người không con). Đó đúng là điều đã xảy ra ở lượt chạy thật: 0/1, 0/1 và 1/5.</p>
     *
     * <p>Chừa từ cuối danh sách vì bộ khung (tổ chi, trưởng ngành, dòng đích tôn) luôn được tạo
     * trước nên nằm ở đầu — họ tuyệt đối không được để tuyệt tự, cây sẽ cụt.</p>
     */
    private void reserveChildlessCouples(int g, List<DemoPerson> pool) {
        int reserved = switch (g) {
            case 4, 5, 6 -> 4;
            default -> 0;
        };
        List<String> backboneKeys = backboneOf(g).stream().map(DemoPerson::key).toList();
        for (int i = pool.size() - 1; i >= 0 && reserved > 0; i--) {
            DemoPerson candidate = pool.get(i);
            if (backboneKeys.contains(candidate.key())) {
                continue;
            }
            pool.remove(i);
            candidate.attributes().put("childless_couple", true);
            reserved--;
        }
    }

    /** Quy mô gia đình theo đời — đời xa đông con, đời gần ít con. */
    private int familySize(int g) {
        return switch (g) {
            case 1, 2 -> 4 + structureRnd.nextInt(5);   // 4..8
            case 3 -> 3 + structureRnd.nextInt(5);      // 3..7
            case 4 -> 3 + structureRnd.nextInt(4);      // 3..6
            case 5 -> 2 + structureRnd.nextInt(4);      // 2..5
            default -> 1 + structureRnd.nextInt(3);     // 1..3
        };
    }

    private DemoPerson addChild(DemoPerson parent) {
        String gender = structureRnd.nextDouble() < MALE_RATIO ? "MALE" : "FEMALE";
        return addChild(parent, "g" + (parent.generation() + 1) + "-" + (++personCounter), gender);
    }

    private DemoPerson addChild(DemoPerson parent, String key, String gender) {
        DemoPerson child = newPerson(key, parent.generation() + 1, true, gender);
        DemoPerson father;
        DemoPerson mother;
        if (parent.male()) {
            father = parent;
            mother = pickWife(parent);
        } else {
            // Dòng cháu ngoại: cha là rể, mẹ là con gái dòng họ.
            father = parent.husband();
            mother = parent;
            child.attributes().put("line", "NGOAI");
        }
        child.father(father);
        child.mother(mother);
        parent.children().add(child);
        child.birthOrder(parent.children().size());
        assignBirthDate(child);
        return child;
    }

    /**
     * Chọn mẹ cho đứa con kế tiếp của một người đàn ông nhiều vợ: các con đầu thuộc vợ cả, các con
     * sau thuộc vợ kế. Với ca tái hôn thì đúng như đời thật — con của người vợ sau sinh muộn hơn.
     */
    private DemoPerson pickWife(DemoPerson father) {
        List<DemoPerson> wives = father.wives();
        if (wives.isEmpty()) {
            // Chốt chặn có chủ đích: nếu rơi vào đây nghĩa là bộ khung không được bảo đảm có vợ
            // (xem backboneOf). Ném lỗi nói rõ tên khoá còn hơn một IndexOutOfBounds vô nghĩa.
            throw new IllegalStateException(
                    "Khong tim duoc me cho con cua " + father.key() + ": nguoi nay chua co vo");
        }
        if (wives.size() == 1) {
            return wives.get(0);
        }
        int index = Math.min(wives.size() - 1, father.children().size() / 3);
        return wives.get(index);
    }

    // =================================================================================
    // 3. Gán chi/ngành
    // =================================================================================

    private void assignBranches() {
        root.branchId(rootBranch.id());
        for (int i = 0; i < config.chiCount(); i++) {
            byKey.get("chi-" + (i + 1) + "-to").branchId(chiBranches.get(i).id());
            for (int j = 0; j < config.nhanhPerChi(); j++) {
                byKey.get("chi-" + (i + 1) + "-nhanh-" + (j + 1) + "-to")
                        .branchId(nhanhBranches.get(i).get(j).id());
            }
        }
        // Con khác của tổ chi (không phải tổ ngành) rải đều vào các ngành để không ai lơ lửng ngoài
        // phạm vi RBAC nào: @RequiresBranch kiểm tra theo ltree, người không thuộc ngành nào thì
        // Trưởng ngành nào cũng không duyệt được hồ sơ của họ.
        for (int i = 0; i < config.chiCount(); i++) {
            DemoPerson chiFounder = byKey.get("chi-" + (i + 1) + "-to");
            int cursor = 0;
            for (DemoPerson child : chiFounder.children()) {
                if (child.branchId() == null) {
                    child.branchId(nhanhBranches.get(i).get(cursor % config.nhanhPerChi()).id());
                    cursor++;
                }
            }
        }
        // Còn lại: thừa hưởng chi/ngành của cha (hoặc của mẹ với dòng cháu ngoại và con riêng),
        // vợ/chồng theo người trong họ. Duyệt theo thứ tự tạo nên cha luôn đã có branch trước con.
        for (DemoPerson person : persons) {
            if (person.branchId() != null) {
                continue;
            }
            if (person.father() != null && person.father().branchId() != null) {
                person.branchId(person.father().branchId());
            } else if (person.mother() != null && person.mother().branchId() != null) {
                person.branchId(person.mother().branchId());
            } else if (person.husband() != null && person.husband().branchId() != null) {
                person.branchId(person.husband().branchId());
            } else if (!person.wives().isEmpty() && person.wives().get(0).branchId() != null) {
                person.branchId(person.wives().get(0).branchId());
            } else {
                person.branchId(rootBranch.id());
            }
        }
    }

    /**
     * Trưởng tộc / trưởng chi là <b>chức danh dòng tộc theo huyết thống</b> (đích tôn), hoàn toàn
     * tách khỏi vai trò kỹ thuật trong bảng {@code role} — CLAUDE.md nói rõ điểm này. Ở đây lấy
     * người còn sống ở đời cao nhất của dòng đích tôn.
     */
    private void assignBranchHeads() {
        rootBranch.headPersonId(livingHeadOfLine("deep-g", root).id());
        for (int i = 0; i < config.chiCount(); i++) {
            DemoPerson founder = byKey.get("chi-" + (i + 1) + "-to");
            chiBranches.get(i).headPersonId(eldestLivingDescendant(founder).id());
            for (int j = 0; j < config.nhanhPerChi(); j++) {
                DemoPerson nhanhFounder = byKey.get("chi-" + (i + 1) + "-nhanh-" + (j + 1) + "-to");
                nhanhBranches.get(i).get(j).headPersonId(eldestLivingDescendant(nhanhFounder).id());
            }
        }
    }

    private DemoPerson livingHeadOfLine(String keyPrefix, DemoPerson fallback) {
        for (int g = config.generations(); g >= 2; g--) {
            DemoPerson candidate = byKey.get(keyPrefix + g);
            if (candidate != null && candidate.alive() && !candidate.deleted()) {
                return candidate;
            }
        }
        return fallback;
    }

    /** Người còn sống, là nam, ở đời nhỏ nhất trong hậu duệ trực hệ của {@code ancestor}. */
    private DemoPerson eldestLivingDescendant(DemoPerson ancestor) {
        DemoPerson best = ancestor;
        List<DemoPerson> frontier = new ArrayList<>(List.of(ancestor));
        while (!frontier.isEmpty()) {
            List<DemoPerson> next = new ArrayList<>();
            for (DemoPerson person : frontier) {
                if (person.alive() && person.male() && !person.deleted()
                        && (!best.alive() || person.generation() < best.generation()
                            || (best == ancestor && !best.alive()))) {
                    if (!best.alive() || person.generation() <= best.generation()) {
                        best = person;
                    }
                }
                next.addAll(person.children());
            }
            frontier = next;
        }
        return best;
    }

    // =================================================================================
    // 4. Ca biên cấu trúc
    // =================================================================================

    private void injectStructuralEdgeCases() {
        injectAdoptions();
        injectStepChildren();
        injectLineageDiscontinuity();
        injectHeirs();
    }

    /**
     * Con nuôi (FR-1.1): ba ca nhận cháu trong họ làm con nuôi (đứa trẻ giữ NGUYÊN cạnh cha mẹ ruột
     * — mất cạnh ruột là mất đường tính họ hàng), và hai ca nhận con nuôi từ ngoài dòng họ.
     */
    private void injectAdoptions() {
        List<DemoPerson> childless = new ArrayList<>();
        List<DemoPerson> donors = new ArrayList<>();
        for (int g = 5; g <= 6; g++) {
            for (DemoPerson person : bloodlineOf(g)) {
                if (!person.male() || person.wives().isEmpty()) {
                    continue;
                }
                if (person.children().isEmpty()) {
                    childless.add(person);
                } else if (person.children().size() >= 3) {
                    donors.add(person);
                }
            }
        }
        int pairs = Math.min(3, Math.min(childless.size(), donors.size()));
        for (int i = 0; i < pairs; i++) {
            DemoPerson adopter = childless.get(i * 3 % Math.max(1, childless.size()));
            DemoPerson donor = donors.get(i * 7 % Math.max(1, donors.size()));
            DemoPerson child = donor.children().get(donor.children().size() - 1);
            if (child.father() == adopter) {
                continue;
            }
            adoptions.add(new Adoption(adopter, child, "Cháu ruột trong họ, cho làm con nuôi để nối hương hoả"));
            if (!adopter.wives().isEmpty()) {
                adoptions.add(new Adoption(adopter.wives().get(0), child, null));
            }
            child.attributes().put("adoption", "TRONG_HO");
            anchor("CON_NUOI_" + nextIndex("CON_NUOI"), child);
        }
        // Hai đứa trẻ mang họ khác, được nhận về nuôi: không có cạnh cha mẹ ruột nào trong đồ thị.
        for (int i = 0; i < 2 && i < childless.size(); i++) {
            DemoPerson adopter = childless.get(childless.size() - 1 - i);
            DemoPerson child = newPerson("con-nuoi-ngoai-" + (i + 1), adopter.generation() + 1, false, i == 0 ? "MALE" : "FEMALE");
            child.birthOrder(1);
            child.attributes().put("adoption", "NGOAI_HO");
            child.branchId(adopter.branchId());
            adoptions.add(new Adoption(adopter, child, "Nhận nuôi từ ngoài dòng họ"));
            if (!adopter.wives().isEmpty()) {
                adoptions.add(new Adoption(adopter.wives().get(0), child, null));
            }
            // Đứa trẻ này không có cha mẹ ruột trong đồ thị nên assignBirthDate() phải suy năm sinh
            // từ người nhận nuôi — và vì thế nó bắt buộc chạy SAU khi cạnh nhận nuôi đã được ghi
            // nhận. Quên gọi thì cả bộ dữ liệu vỡ ở bước tính ngày mất.
            assignBirthDate(child);
            anchor("CON_NUOI_" + nextIndex("CON_NUOI"), child);
        }
    }

    /**
     * Tái hôn + con riêng: người vợ sau mang theo con của đời chồng trước. Một ca được cha dượng
     * nhận làm con nuôi (có cạnh PARENT_ADOPT), một ca không — chỉ còn cạnh mẹ. Ca thứ hai mới là
     * ca khó: đứa trẻ chỉ có MỘT cạnh cha mẹ, LCA và danh xưng phải xử lý được.
     */
    private void injectStepChildren() {
        int index = 0;
        for (Marriage marriage : marriages) {
            if (!marriage.remarriage) {
                continue;
            }
            DemoPerson husband = marriage.husband;
            DemoPerson secondWife = marriage.wife;
            DemoPerson stepChild = husband.children().stream()
                    .filter(c -> c.mother() == secondWife)
                    .findFirst()
                    .orElseGet(() -> husband.children().isEmpty() ? null
                            : husband.children().get(husband.children().size() - 1));
            if (stepChild == null) {
                continue;
            }
            index++;
            husband.children().remove(stepChild);
            stepChild.father(null);
            stepChild.mother(secondWife);
            stepChild.birthOrder(1);
            stepChild.attributes().put("step_child", true);
            stepChild.attributes().put("note", "Con riêng của mẹ với người chồng trước, ngoài dòng họ");
            if (index == 1) {
                adoptions.add(new Adoption(husband, stepChild, "Cha dượng nhận con riêng của vợ làm con nuôi"));
                stepChild.attributes().put("adoption", "CON_RIENG_CUA_VO");
                anchor("CON_NUOI_" + nextIndex("CON_NUOI"), stepChild);
            }
            anchor("CON_RIENG_" + index, stepChild);
            // Đánh lại thứ tự sinh cho các con còn lại của người cha: birth_order là thứ tự trong
            // đàn con CỦA CÙNG NGƯỜI CHA, bỏ một đứa ra thì phải đánh số lại, nếu không rule
            // "anh của bố = bác, em của bố = chú" sẽ tính sai.
            int order = 0;
            for (DemoPerson child : husband.children()) {
                child.birthOrder(++order);
            }
        }
    }

    /** Tuyệt tự và kế tự (FR-1.2) — hai trạng thái dòng dõi mà schema có riêng cột để ghi. */
    private void injectLineageDiscontinuity() {
        List<DemoPerson> candidates = new ArrayList<>();
        for (int g = 4; g <= 5; g++) {
            for (DemoPerson person : bloodlineOf(g)) {
                if (person.male() && !person.wives().isEmpty() && person.children().isEmpty()) {
                    candidates.add(person);
                }
            }
        }
        // Tuyệt tự: không con nối dõi, cũng không lập ai kế tự.
        for (int i = 0; i < 2 && i < candidates.size(); i++) {
            DemoPerson person = candidates.get(i);
            person.lineageStatus("TUYET_TU");
            person.attributes().put("lineage_note", "Không có người nối dõi");
            anchor("TUYET_TU_" + (i + 1), person);
        }
        // Kế tự: lập một người cháu trong họ đứng ra nối dõi, thờ tự phần hương hoả.
        // Duyệt HẾT các ứng viên còn lại chứ không chốt cứng vào một chỉ số: người không con thứ ba
        // rất có thể cũng không có cháu trai nào để lập kế tự, và khi ấy ca biên bắt buộc này im
        // lặng biến mất (đã xảy ra thật ở một lượt chạy).
        for (int i = 2; i < candidates.size(); i++) {
            DemoPerson person = candidates.get(i);
            DemoPerson heir = findNephew(person);
            if (heir == null) {
                continue;
            }
            person.lineageStatus("KE_TU");
            person.attributes().put("lineage_note", "Đã lập người kế tự nối dõi");
            heirs.add(new HeirLink(person, heir, "KE_TU"));
            anchor("KE_TU_1", person);
            anchor("KE_TU_HEIR_1", heir);
            break;
        }
    }

    /**
     * Tìm một người cháu trai trong cùng chi để lập kế tự — trước hết là cháu ruột (con của anh em
     * ruột), nếu không có thì mở rộng sang cháu của ông nội (con của anh em họ). Đúng thứ tự ưu
     * tiên trong tập quán lập kế tự: càng gần huyết thống càng được chọn trước.
     */
    private DemoPerson findNephew(DemoPerson person) {
        DemoPerson father = person.father();
        if (father == null) {
            return null;
        }
        DemoPerson nephew = nephewAmongSiblings(father, person);
        if (nephew != null) {
            return nephew;
        }
        DemoPerson grandfather = father.father();
        if (grandfather == null) {
            return null;
        }
        for (DemoPerson uncle : grandfather.children()) {
            if (uncle == father) {
                continue;
            }
            DemoPerson cousinSon = nephewAmongSiblings(uncle, null);
            if (cousinSon != null) {
                return cousinSon;
            }
        }
        return null;
    }

    /** Cháu trai đầu tiên trong đàn con của {@code parent}, bỏ qua {@code exclude}. */
    private DemoPerson nephewAmongSiblings(DemoPerson parent, DemoPerson exclude) {
        for (DemoPerson sibling : parent.children()) {
            if (sibling == exclude) {
                continue;
            }
            for (DemoPerson candidate : sibling.children()) {
                if (candidate.male()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Đích tôn: cháu đích tôn là con trai trưởng của con trai trưởng. Chuỗi này chạy dọc dòng sâu
     * 7 đời nên vừa là dữ liệu test đích tôn, vừa là dữ liệu test LCA khoảng cách xa.
     */
    private void injectHeirs() {
        List<DemoPerson> line = new ArrayList<>();
        line.add(root);
        line.add(byKey.get("chi-1-to"));
        line.add(byKey.get("chi-1-nhanh-1-to"));
        for (int g = 4; g <= config.generations(); g++) {
            line.add(byKey.get("deep-g" + g));
        }
        int created = 0;
        for (int i = 0; i + 2 < line.size(); i++) {
            DemoPerson ancestor = line.get(i);
            DemoPerson grandson = line.get(i + 2);
            if (ancestor == null || grandson == null) {
                continue;
            }
            heirs.add(new HeirLink(ancestor, grandson, "DICH_TON"));
            grandson.attributes().put("clan_title", "DICH_TON");
            anchor("DICH_TON_" + (++created), grandson);
        }
        // Thừa tự: một người con thứ được giao lo phần hương hoả của một chi khác.
        DemoPerson benefactor = byKey.get("chi-2-to");
        if (benefactor != null && benefactor.children().size() > 1) {
            DemoPerson successor = benefactor.children().get(1);
            heirs.add(new HeirLink(benefactor, successor, "THUA_TU"));
            anchor("THUA_TU_1", successor);
        }
    }

    // =================================================================================
    // 5. Ngày tháng — dương lịch và âm lịch phải khớp nhau
    // =================================================================================

    /**
     * Gán ngày sinh NGAY khi nhân khẩu được tạo.
     *
     * <p>Vì sao không gom thành một lượt ở cuối: việc gả vợ/chồng phải biết tuổi. Nếu tính ngày
     * sinh sau cùng thì generator sẽ vô tư gả vợ cho một cháu bé đời 7 sinh năm 2020 — dữ liệu demo
     * kiểu đó đem trình dòng họ là mất mặt. Thứ tự tạo bảo đảm cha/vợ/chồng luôn đã có ngày sinh
     * trước khi tới lượt người này.</p>
     */
    private void assignBirthDate(DemoPerson person) {
        if (person.birthSolar() != null) {
            return;
        }
        int year;
        if (person == root) {
            year = config.rootBirthYear();
        } else if (person.father() != null && person.father().birthSolar() != null) {
            year = childBirthYear(person.father().birthSolar().getYear(), person.birthOrder());
        } else if (person.mother() != null && person.mother().birthSolar() != null) {
            year = person.mother().birthSolar().getYear() + 20 + dateRnd.nextInt(5);
        } else if (person.husband() != null && person.husband().birthSolar() != null) {
            // Dâu: thường kém chồng vài tuổi; vợ kế kém nhiều hơn.
            int gap = person.husband().wives().size() > 1 ? 4 + dateRnd.nextInt(11) : dateRnd.nextInt(7);
            year = person.husband().birthSolar().getYear() + gap;
        } else if (!person.wives().isEmpty() && person.wives().get(0).birthSolar() != null) {
            // Rể: thường hơn vợ vài tuổi.
            year = person.wives().get(0).birthSolar().getYear() - dateRnd.nextInt(6);
        } else {
            DemoPerson adopter = adoptions.stream()
                    .filter(a -> a.child == person)
                    .map(a -> a.parent)
                    .filter(p -> p.birthSolar() != null)
                    .findFirst()
                    .orElse(null);
            year = adopter != null
                    ? childBirthYear(adopter.birthSolar().getYear(), 1)
                    : config.rootBirthYear();
        }
        // Không ai được sinh sau "hôm nay" của bộ dữ liệu (trừ hao một năm cho an toàn).
        year = Math.min(year, config.referenceDate().getYear() - 1);
        person.birthSolar(randomDateIn(year));
        person.birthLunar(lunar.toLunar(person.birthSolar()));
    }

    /**
     * Năm sinh của con tính từ năm sinh cha: con đầu lòng khi cha 25–31 tuổi, các con sau cách nhau
     * 2–4 năm. Khoảng cách thế hệ trung bình khoảng 31 năm — chính con số này quyết định tỉ lệ
     * còn sống / đã khuất của cả bộ dữ liệu, đừng chỉnh nếu chưa đo lại tỉ lệ đó.
     */
    private int childBirthYear(int fatherBirthYear, Integer birthOrder) {
        int order = birthOrder == null ? 1 : birthOrder;
        int ageAtFirstChild = 25 + dateRnd.nextInt(7);
        int spacing = 0;
        for (int i = 1; i < order; i++) {
            spacing += 2 + dateRnd.nextInt(3);
        }
        return fatherBirthYear + Math.min(ageAtFirstChild + spacing, 52);
    }

    private LocalDate randomDateIn(int year) {
        int month = 1 + dateRnd.nextInt(12);
        int day = 1 + dateRnd.nextInt(28);
        return LocalDate.of(year, month, day);
    }

    /**
     * Ngày mất. Hai ràng buộc bắt buộc:
     * <ul>
     *   <li>Cha/mẹ phải còn sống tới khi đứa con cuối ra đời — nếu không thì dữ liệu demo tự mâu
     *       thuẫn ngay trên màn hình cây, ai nhìn cũng thấy.</li>
     *   <li>Không ai còn sống quá 100 tuổi so với {@code referenceDate}.</li>
     * </ul>
     */
    private void assignDeathDates() {
        Map<String, Integer> lastChildYear = new LinkedHashMap<>();
        for (DemoPerson person : persons) {
            if (person.birthSolar() == null) {
                continue;
            }
            int year = person.birthSolar().getYear();
            if (person.father() != null) {
                lastChildYear.merge(person.father().key(), year, Math::max);
            }
            if (person.mother() != null) {
                lastChildYear.merge(person.mother().key(), year, Math::max);
            }
        }

        int referenceYear = config.referenceDate().getYear();
        for (DemoPerson person : persons) {
            if (person.birthSolar() == null) {
                // Moi nhan khau deu phai co ngay sinh truoc buoc nay; thieu la loi lap trinh o
                // mot ca bien nao do vua them, khong phai du lieu xau.
                throw new IllegalStateException(
                        "Nhan khau " + person.key() + " chua duoc gan ngay sinh truoc khi tinh ngay mat");
            }
            int birthYear = person.birthSolar().getYear();
            boolean childlessAndSingle = person.children().isEmpty()
                    && person.wives().isEmpty() && person.husband() == null;
            int lifespan = drawLifespan(birthYear, childlessAndSingle);
            int minDeathYear = lastChildYear.getOrDefault(person.key(), birthYear) + 1;
            int deathYear = Math.max(birthYear + lifespan, minDeathYear);

            int ageAtReference = referenceYear - birthYear;
            if (ageAtReference > 100) {
                deathYear = Math.max(minDeathYear, birthYear + 80 + dateRnd.nextInt(20));
            }
            if (deathYear > referenceYear) {
                person.alive(true);
                continue;
            }
            person.alive(false);
            LocalDate death = randomDateIn(deathYear);
            if (!death.isAfter(person.birthSolar())) {
                death = person.birthSolar().plusDays(1 + dateRnd.nextInt(300));
            }
            person.deathSolar(death);
            person.deathLunar(lunar.toLunar(death));
        }
    }

    /** Tuổi thọ theo thời kỳ, kèm tỉ lệ chết yểu của các đời xa. */
    private int drawLifespan(int birthYear, boolean childlessAndSingle) {
        double mean;
        if (birthYear < 1880) {
            mean = 57;
        } else if (birthYear < 1920) {
            mean = 62;
        } else if (birthYear < 1950) {
            mean = 70;
        } else if (birthYear < 1980) {
            mean = 76;
        } else {
            mean = 80;
        }
        double infantRate = birthYear < 1930 ? 0.10 : 0.03;
        if (childlessAndSingle && dateRnd.nextDouble() < infantRate) {
            return dateRnd.nextInt(14); // mất khi còn nhỏ
        }
        int lifespan = (int) Math.round(mean + dateRnd.nextGaussian() * 13);
        return Math.max(1, Math.min(lifespan, 99));
    }

    // =================================================================================
    // 6. Ca biên phụ thuộc ngày tháng — phân tầng riêng tư, xoá mềm, ẩn danh
    // =================================================================================

    private void injectDateDependentEdgeCases() {
        injectMinors();
        injectTier3Contacts();
        injectPrivacyLevels();
        injectSoftDeleted();
        injectAnonymized();
    }

    /**
     * Trẻ vị thành niên (BA v2 §10): kéo ngày sinh của vài em út đời cuối về sát {@code
     * referenceDate}.
     *
     * <p>Vì sao phải nhồi tay: với thuỷ tổ sinh năm {@value DemoSeedConfig#DEFAULT_ROOT_BIRTH_YEAR},
     * đời thứ bảy sinh vào quãng thập niên 1960 nên <b>không có lấy một em nào dưới 18 tuổi</b>.
     * Mà "trẻ vị thành niên ẩn tối đa — trần là Tầng 1, kể cả với người cùng chi" lại là một nhánh
     * riêng trong {@code PrivacyTierService}. Không có dữ liệu thì nhánh ấy chưa từng được chạy, và
     * đây đúng là loại lỗi mà không ai phát hiện cho tới khi hồ sơ một đứa trẻ lộ ra ngoài.</p>
     *
     * <p>Chỉ chọn con út của người cha <b>còn sống</b>: sinh sau ngày mất của cha là một mâu thuẫn
     * nhìn thấy ngay trên phả đồ.</p>
     */
    private void injectMinors() {
        int created = 0;
        for (DemoPerson person : generation(config.generations())) {
            if (created >= 6) {
                break;
            }
            if (!person.alive() || !person.bloodline() || person.deleted()) {
                continue;
            }
            if (!person.children().isEmpty() || person.husband() != null || !person.wives().isEmpty()) {
                continue;
            }
            DemoPerson father = person.father();
            if (father == null || !father.alive() || person.birthOrder() == null
                    || person.birthOrder() != father.children().size()) {
                continue; // chỉ con út, để thứ tự sinh của đàn con không bị đảo lộn
            }
            int year = config.referenceDate().getYear() - (3 + edgeRnd.nextInt(14)); // 3–16 tuổi
            LocalDate birth = randomDateIn(year);
            person.birthSolar(birth);
            person.birthLunar(lunar.toLunar(birth));
            person.attributes().put("minor", true);
            // Đặt trước để composeName() không gán cho một đứa trẻ một nghề nghiệp người lớn.
            person.attributes().put("occupation", "Học sinh");
            created++;
            anchor("VI_THANH_NIEN_" + created, person);
        }
        stats.put("minors_injected", created);
    }

    /**
     * Người còn sống có dữ liệu Tầng 3 (BA v2 §10): số điện thoại, email, địa chỉ đầy đủ. Đây là
     * dữ liệu mà guest KHÔNG được thấy một chữ nào và thành viên thường cũng không — chỉ chính chủ,
     * quản trị, hoặc người đã opt-in. Không có nhóm này thì không test được bộ lọc phân tầng.
     */
    private void injectTier3Contacts() {
        int created = 0;
        for (DemoPerson person : persons) {
            if (created >= 24) {
                break;
            }
            if (!person.alive() || person.generation() < 6 || person.deleted()) {
                continue;
            }
            if (edgeRnd.nextInt(100) >= 12) {
                continue;
            }
            created++;
            person.privacyLevel("TIER_3");
            person.attributes().put("phone", "09" + (10000000 + edgeRnd.nextInt(89999999)));
            // Tên miền .test được RFC 2606 dành riêng cho ví dụ — không bao giờ trỏ tới hộp thư thật.
            person.attributes().put("email", "thanhvien" + created + "@giapha.test");
            person.attributes().put("address", (100 + edgeRnd.nextInt(800)) + " đường Lạc Long Quân, "
                    + VietnameseNameBank.currentPlace(edgeRnd));
            person.attributes().put("consent_tier3", true);
            if (created <= 3) {
                anchor("TIER3_" + created, person);
            }
        }
        stats.put("tier3_persons", created);
    }

    /** Rải mức riêng tư tự chọn để bộ lọc hiển thị có đủ bốn nhánh rẽ. */
    private void injectPrivacyLevels() {
        for (DemoPerson person : persons) {
            if (!person.alive() || !"DEFAULT".equals(person.privacyLevel())) {
                continue;
            }
            int roll = edgeRnd.nextInt(100);
            if (roll < 6) {
                person.privacyLevel("TIER_1");
            } else if (roll < 12) {
                person.privacyLevel("TIER_2");
            }
        }
    }

    /**
     * Xoá mềm (FR-1.5): node vẫn nằm nguyên trong đồ thị, chỉ bật cờ. Ba bản ghi trùng do nhập liệu
     * hai lần — đúng thứ hay xảy ra khi số hoá gia phả giấy.
     */
    private void injectSoftDeleted() {
        int created = 0;
        for (DemoPerson person : persons) {
            if (created >= 3) {
                break;
            }
            if (person.generation() < 6 || person.bloodline() || person.deleted()) {
                continue;
            }
            if (edgeRnd.nextInt(100) >= 3) {
                continue;
            }
            created++;
            person.deleted(true);
            person.attributes().put("delete_reason", "Bản ghi trùng khi nhập từ gia phả giấy");
            anchor("SOFT_DELETED_" + created, person);
        }
        stats.put("soft_deleted", created);
    }

    /**
     * Quyền xoá dữ liệu theo Nghị định 13/2023 được phục vụ bằng <b>ẩn danh hoá</b>: xoá sạch dữ
     * liệu Tầng 3, GIỮ NGUYÊN node phả hệ để cây không đứt.
     */
    private void injectAnonymized() {
        for (DemoPerson person : persons) {
            if (person.alive() && "TIER_3".equals(person.privacyLevel()) && !person.deleted()) {
                person.attributes().remove("phone");
                person.attributes().remove("email");
                person.attributes().remove("address");
                person.attributes().remove("consent_tier3");
                person.privacyLevel("DEFAULT");
                person.anonymized(true);
                person.currentPlace(null);
                person.attributes().put("anonymize_reason", "Chủ thể yêu cầu xoá dữ liệu (ND 13/2023)");
                anchor("ANONYMIZED_1", person);
                stats.put("anonymized", 1);
                return;
            }
        }
    }

    // =================================================================================
    // 7. Tên đa lớp
    // =================================================================================

    private void buildNames() {
        for (DemoPerson person : persons) {
            composeName(person);
            addName(person, "HUY", person.fullName(), true);

            boolean oldGeneration = person.generation() <= 4;
            if (oldGeneration && person.male() && nameRnd.nextInt(100) < 80) {
                addName(person, "TU", VietnameseNameBank.tuName(nameRnd), false);
            }
            if (oldGeneration && person.male() && nameRnd.nextInt(100) < 55) {
                addName(person, "HIEU", VietnameseNameBank.hieuName(nameRnd), false);
            }
            if (!person.alive() && person.generation() <= 5 && nameRnd.nextInt(100) < 70) {
                addName(person, "THUY", VietnameseNameBank.thuyName(person.male(), nameRnd), false);
            }
            if (person.generation() >= 5 && nameRnd.nextInt(100) < 35) {
                addName(person, "THUONG_GOI", person.surname() + " " + person.givenName(), false);
            } else if (person.generation() < 5 && nameRnd.nextInt(100) < 30) {
                addName(person, "THUONG_GOI",
                        (person.male() ? "Ông " : "Bà ") + ordinalWord(person.birthOrder()), false);
            }
            if (nameRnd.nextInt(100) < 3) {
                addName(person, "PHAP_DANH", VietnameseNameBank.phapDanh(person.male(), nameRnd), false);
            }
        }
    }

    private void composeName(DemoPerson person) {
        String surname = person.bloodline() || person.father() != null
                ? VietnameseNameBank.CLAN_SURNAME
                : VietnameseNameBank.otherSurname(nameRnd);
        if (person.attributes().containsKey("line") || Boolean.TRUE.equals(person.attributes().get("step_child"))
                || "NGOAI_HO".equals(person.attributes().get("adoption"))) {
            // Cháu ngoại, con riêng, con nuôi ngoài họ: mang họ cha đẻ, không mang họ dòng họ này.
            surname = person.father() != null && person.father().surname() != null
                    ? person.father().surname()
                    : VietnameseNameBank.otherSurname(nameRnd);
        }
        person.surname(surname);
        person.middleName(person.male()
                ? VietnameseNameBank.middleForGeneration(person.generation())
                : VietnameseNameBank.femaleMiddle(person.generation(), nameRnd));
        person.givenName(person.male()
                ? VietnameseNameBank.maleGiven(nameRnd)
                : VietnameseNameBank.femaleGiven(person.generation(), nameRnd));

        person.nativePlace(person.bloodline()
                ? VietnameseNameBank.clanNativePlace()
                : VietnameseNameBank.nativePlace(nameRnd));
        // Người đã ẩn danh hoá thì KHÔNG gán lại nơi ở: đó là dữ liệu Tầng 3 vừa bị xoá theo yêu
        // cầu của chủ thể (ND 13/2023). buildNames() chạy sau injectAnonymized() nên nếu quên điều
        // kiện này thì việc ẩn danh bị lặng lẽ hoàn tác.
        if (person.alive() && !person.anonymized() && person.currentPlace() == null) {
            person.currentPlace(VietnameseNameBank.currentPlace(nameRnd));
        }
        person.attributes().putIfAbsent("occupation",
                VietnameseNameBank.occupation(person.birthSolar().getYear(), nameRnd));
    }

    private void addName(DemoPerson person, String type, String fullName, boolean primary) {
        // Hán-Nôm chỉ khắc cho các đời trên và chỉ khi tra đủ chữ trong từ điển — thà để trống còn
        // hơn ghi sai một chữ lên bản khắc bia.
        String hannom = person.generation() <= 3 && "HUY".equals(type)
                ? VietnameseNameBank.toHanNom(fullName)
                : null;
        names.add(new DemoName(ids.name(person.key(), type), person.id(), type, fullName, hannom, primary, null));
        person.names().add(names.get(names.size() - 1));
    }

    private String ordinalWord(Integer birthOrder) {
        int order = birthOrder == null ? 1 : birthOrder;
        String[] words = {"Cả", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười"};
        return words[Math.min(order, words.length) - 1];
    }

    /**
     * Trùng tên huý với bậc trên (FR-1.6). Sinh hai kiểu va chạm vì hai kiểu này cần hai cách dò
     * khác nhau:
     * <ul>
     *   <li><b>FULL</b> — trùng nguyên tên đầy đủ với tên huý của cụ tổ.</li>
     *   <li><b>GIVEN</b> — chỉ trùng chữ tên (chữ đệm theo đời khác nhau). Đây mới là ca hay gặp và
     *       là ca dễ lọt lưới nếu hệ thống chỉ so tên đầy đủ.</li>
     * </ul>
     */
    private void injectKyHuyCollisions() {
        List<DemoPerson> ancestors = new ArrayList<>();
        for (int g = 2; g <= 3; g++) {
            ancestors.addAll(bloodlineOf(g).stream().filter(DemoPerson::male).toList());
        }
        if (ancestors.isEmpty()) {
            return;
        }
        List<DemoPerson> candidates = new ArrayList<>();
        for (int g = 6; g <= config.generations(); g++) {
            candidates.addAll(bloodlineOf(g).stream()
                    .filter(DemoPerson::male)
                    .filter(p -> !p.deleted())
                    .toList());
        }
        int created = 0;
        for (int i = 0; i < candidates.size() && created < 5; i += 47) {
            DemoPerson descendant = candidates.get(i);
            DemoPerson ancestor = ancestors.get(created % ancestors.size());
            boolean fullMatch = created < 3;
            String newName = fullMatch
                    ? ancestor.fullName()
                    : descendant.surname() + " " + descendant.middleName() + " " + ancestor.givenName();
            if (!fullMatch) {
                descendant.givenName(ancestor.givenName());
            } else {
                descendant.middleName(ancestor.middleName());
                descendant.givenName(ancestor.givenName());
            }
            replacePrimaryName(descendant, newName);
            descendant.attributes().put("ky_huy_conflict_with", ancestor.key());
            descendant.attributes().put("ky_huy_kind", fullMatch ? "FULL" : "GIVEN");
            created++;
            anchor("KY_HUY_" + created, descendant);
            anchor("KY_HUY_ANCESTOR_" + created, ancestor);
        }
        stats.put("ky_huy_collisions", created);
    }

    private void replacePrimaryName(DemoPerson person, String newFullName) {
        for (int i = 0; i < names.size(); i++) {
            DemoName name = names.get(i);
            if (name.personId().equals(person.id()) && "HUY".equals(name.type())) {
                DemoName replacement = new DemoName(name.id(), name.personId(), name.type(), newFullName,
                        null, name.primary(), "Ca kiểm thử kỵ húy — trùng tên huý bậc trên");
                names.set(i, replacement);
                person.names().remove(name);
                person.names().add(replacement);
                return;
            }
        }
    }

    // =================================================================================
    // 8. Quan hệ — nguồn cho cả bảng relationship lẫn cạnh AGE
    // =================================================================================

    private void buildRelations() {
        for (DemoPerson person : persons) {
            if (person.father() != null) {
                addRelation(person.father(), person, "PARENT_BIO", null, null, null, null, null, null);
            }
            if (person.mother() != null) {
                addRelation(person.mother(), person, "PARENT_BIO", null, null, null, null, null, null);
            }
        }
        for (Adoption adoption : adoptions) {
            addRelation(adoption.parent, adoption.child, "PARENT_ADOPT", null, null,
                    adoption.child.birthSolar(), null, null, adoption.note);
        }
        for (Marriage marriage : marriages) {
            LocalDate from = marriageDate(marriage);
            LocalDate to = null;
            String endReason = null;
            if (marriage.ended) {
                endReason = marriage.endReason;
                to = from.plusYears(5 + edgeRnd.nextInt(12));
                if (!marriage.husband.alive() && marriage.husband.deathSolar() != null
                        && to.isAfter(marriage.husband.deathSolar())) {
                    to = marriage.husband.deathSolar();
                }
                // Chồng có thể mất TRƯỚC cái ngày cưới mà công thức tuổi cưới suy ra (người yểu
                // mệnh). Không kẹp lại thì ck_relationship_valid_range vỡ và cả transaction đổ.
                if (to.isBefore(from)) {
                    to = from;
                }
            }
            addRelation(marriage.husband, marriage.wife, "SPOUSE", marriage.order, null,
                    from, to, endReason, marriage.remarriage ? "Tái hôn" : null);
        }
        for (HeirLink heir : heirs) {
            addRelation(heir.benefactor, heir.heir, "HEIR", null, heir.heirType, null, null, null, null);
        }
    }

    /** Ngày cưới: chú rể quãng 22–28 tuổi, vợ kế thì muộn hơn. Luôn sau ngày sinh của cả hai. */
    private LocalDate marriageDate(Marriage marriage) {
        int base = Math.max(marriage.husband.birthSolar().getYear(), marriage.wife.birthSolar().getYear());
        int age = 22 + edgeRnd.nextInt(7) + (marriage.order - 1) * 8;
        int year = Math.max(base + 18, marriage.husband.birthSolar().getYear() + age);
        return LocalDate.of(year, 1 + edgeRnd.nextInt(12), 1 + edgeRnd.nextInt(28));
    }

    /**
     * Thêm một quan hệ, bỏ qua bản trùng.
     *
     * <p>Chốt chặn {@link #relationKeys} là cần thiết chứ không thừa: id quan hệ sinh tất định từ
     * bộ {@code (from, to, relType, spouse_order)}, nên một cặp trùng sẽ vừa vỡ khoá chính vừa vỡ
     * {@code ux_relationship_parent} — và vì cả bộ dữ liệu ghi trong MỘT transaction nên chỉ một
     * dòng trùng là mất trắng cả 1.500 nhân khẩu. Ca có thật: người nhận con nuôi tình cờ cũng là
     * cha/mẹ ruột của đứa trẻ đó.</p>
     */
    private void addRelation(DemoPerson from, DemoPerson to, String relType, Integer spouseOrder,
                             String heirType, LocalDate validFrom, LocalDate validTo,
                             String endReason, String note) {
        if (from == to) {
            return; // ck_relationship_no_self
        }
        int discriminator = spouseOrder == null ? 0 : spouseOrder;
        UUID id = ids.relation(from.key(), to.key(), relType, discriminator);
        if (!relationKeys.add(id)) {
            return;
        }
        relations.add(new DemoRelation(id, from.id(), to.id(), relType, spouseOrder, heirType,
                validFrom, validTo, endReason, note, Map.of()));
    }

    // =================================================================================
    // 9. Thống kê & mốc neo
    // =================================================================================

    private void anchor(String name, DemoPerson person) {
        anchors.putIfAbsent(name, person.id());
    }

    private int nextIndex(String prefix) {
        int index = 1;
        while (anchors.containsKey(prefix + "_" + index)) {
            index++;
        }
        return index;
    }

    private void computeStats() {
        stats.put("branches", branches.size());
        stats.put("persons", persons.size());
        stats.put("names", names.size());
        stats.put("relations", relations.size());
        stats.put("bloodline", (int) persons.stream().filter(DemoPerson::bloodline).count());
        stats.put("alive", (int) persons.stream().filter(DemoPerson::alive).count());
        stats.put("deceased", (int) persons.stream().filter(p -> !p.alive()).count());
        stats.put("minors", (int) persons.stream()
                .filter(DemoPerson::alive)
                .filter(p -> p.birthSolar().isAfter(config.referenceDate().minusYears(18)))
                .count());
        for (int g = 1; g <= config.generations(); g++) {
            stats.put("generation_" + g, generation(g).size());
        }
        stats.put("dau", (int) persons.stream()
                .filter(p -> "DAU".equals(p.attributes().get("in_law_role"))).count());
        stats.put("re", (int) persons.stream()
                .filter(p -> "RE".equals(p.attributes().get("in_law_role"))).count());
        stats.put("da_the", (int) persons.stream().filter(p -> p.wives().size() > 1).count());
        stats.put("tai_hon", (int) marriages.stream().filter(m -> m.remarriage).count());
        stats.put("con_nuoi", (int) relations.stream()
                .filter(r -> "PARENT_ADOPT".equals(r.relType())).count());
        stats.put("con_rieng", (int) persons.stream()
                .filter(p -> Boolean.TRUE.equals(p.attributes().get("step_child"))).count());
        stats.put("tuyet_tu", (int) persons.stream()
                .filter(p -> "TUYET_TU".equals(p.lineageStatus())).count());
        stats.put("ke_tu", (int) persons.stream()
                .filter(p -> "KE_TU".equals(p.lineageStatus())).count());
        stats.put("dich_ton", (int) heirs.stream().filter(h -> "DICH_TON".equals(h.heirType)).count());
        stats.put("spouse_relations", (int) relations.stream()
                .filter(r -> "SPOUSE".equals(r.relType())).count());
        stats.put("parent_relations", (int) relations.stream()
                .filter(r -> r.relType().startsWith("PARENT")).count());
        stats.put("deep_line_depth", deepLineDepth());
        stats.put("anchors", anchors.size());
    }

    private int deepLineDepth() {
        int depth = 0;
        DemoPerson current = byKey.get("deep-g" + config.generations());
        while (current != null) {
            depth++;
            current = current.father();
        }
        return depth;
    }

    // =================================================================================
    // Kiểu dữ liệu nội bộ
    // =================================================================================

    private static final class Marriage {
        private final DemoPerson husband;
        private final DemoPerson wife;
        private final DemoPerson clanMember;
        private final int order;
        private final boolean remarriage;
        private boolean ended;
        private String endReason;

        private Marriage(DemoPerson husband, DemoPerson wife, DemoPerson clanMember, int order,
                         boolean remarriage) {
            this.husband = husband;
            this.wife = wife;
            this.clanMember = clanMember;
            this.order = order;
            this.remarriage = remarriage;
        }
    }

    private record Adoption(DemoPerson parent, DemoPerson child, String note) {
    }

    private record HeirLink(DemoPerson benefactor, DemoPerson heir, String heirType) {
    }
}
