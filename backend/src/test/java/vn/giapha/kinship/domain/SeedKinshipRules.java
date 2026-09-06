package vn.giapha.kinship.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.giapha.shared.vo.Gender;

/**
 * Nap bo luat danh xung MAC DINH (mien Bac) tu chinh file migration
 * {@code db/migration/R__seed_kinship_rules_default.sql}.
 *
 * <p><b>Vi sao doc thang file SQL thay vi chep tay sang Java:</b> FR-1.3a noi danh xung la
 * <i>du lieu</i>, khong phai code. Mot ban sao trong test se troi khoi ban seed that ngay lan dau
 * Hoi dong Tuc bieu sua mot dong, va tu do bo test se khang dinh mot the gioi khong ton tai. Doc
 * thang file seed thi bo test luon do dung bo luat dang chay o production, va them mot luat sai
 * uu tien se lam do test ngay.</p>
 *
 * <p>Chi doc, khong bao gio ghi. Khong dung thu vien phan tich SQL nao — file seed la mot loat
 * lenh {@code INSERT ... VALUES (...),(...);} deu dan nen mot bo quet ky tu la du.</p>
 */
final class SeedKinshipRules {

    private static final String SEED_RESOURCE = "/db/migration/R__seed_kinship_rules_default.sql";

    /**
     * Id co dinh cua bo luat DEFAULT trong file seed. File seed con khai them mot bo luat cap
     * REGION (bien the "kinh" tren bac cu, dang TAT cho Hoi dong Toc bieu chot), nen bo quet phai
     * LOC theo rule_set_id — khong loc thi luat cua bo REGION se lot vao mienBac() va lam ca bo
     * test khang dinh mot bo luat khong ai dang chay.
     */
    private static final String DEFAULT_RULE_SET_ID = "00000000-0000-0000-0000-0000000000b1";

    /** Khong khop {@code INSERT INTO kinship_rule_set} vi sau {@code kinship_rule} phai la khoang trang hoac '('. */
    private static final Pattern INSERT_HEAD =
            Pattern.compile("INSERT\\s+INTO\\s+kinship_rule\\s*\\(([^)]*)\\)\\s*VALUES", Pattern.CASE_INSENSITIVE);

    /** Doc file seed dung MOT lan cho ca JVM — bo test ca bien phai chay duoi mot giay. */
    private static final List<KinshipRule> CACHED = parse(DEFAULT_RULE_SET_ID);

    private SeedKinshipRules() {
    }

    /** Bo luat DEFAULT mien Bac, khong ke thua ai — dung lam goc cho moi ca bien. */
    static KinshipRuleSet mienBac() {
        return KinshipRuleSet.of(RuleScope.DEFAULT, CACHED);
    }

    static List<KinshipRule> load() {
        return CACHED;
    }

    /**
     * Luat cua MOT bo luat bat ky trong file seed, tra theo rule_set_id. Dung de doc bo REGION
     * ("kinh") ma khong lam ban bo DEFAULT.
     */
    static List<KinshipRule> loadRuleSet(String ruleSetId) {
        return parse(ruleSetId);
    }

    private static List<KinshipRule> parse(String ruleSetId) {
        String sql = readSeed();
        List<KinshipRule> rules = new ArrayList<>();
        Matcher matcher = INSERT_HEAD.matcher(sql);
        while (matcher.find()) {
            List<String> columns = splitColumns(matcher.group(1));
            int cursor = matcher.end();
            int ruleSetColumn = columns.indexOf("rule_set_id");
            for (List<String> tuple : readTuples(sql, cursor)) {
                if (ruleSetColumn >= 0 && !ruleSetId.equals(text(tuple.get(ruleSetColumn)))) {
                    continue;
                }
                rules.add(toRule(columns, tuple));
            }
        }
        if (rules.isEmpty()) {
            throw new IllegalStateException(
                    "Khong doc duoc luat nao cua bo " + ruleSetId + " tu " + SEED_RESOURCE);
        }
        return List.copyOf(rules);
    }

    private static String readSeed() {
        try (InputStream in = SeedKinshipRules.class.getResourceAsStream(SEED_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay " + SEED_RESOURCE + " tren classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> splitColumns(String raw) {
        List<String> columns = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                columns.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    /** Doc cac bo gia tri {@code (...),(...);} bat dau tu {@code from}, ton trong nhay don va {@code ''}. */
    private static List<List<String>> readTuples(String sql, int from) {
        List<List<String>> tuples = new ArrayList<>();
        int index = from;
        while (index < sql.length()) {
            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
                index++;
            }
            if (index >= sql.length() || sql.charAt(index) != '(') {
                break;
            }
            StringBuilder token = new StringBuilder();
            List<String> tuple = new ArrayList<>();
            boolean inQuote = false;
            index++;
            while (index < sql.length()) {
                char ch = sql.charAt(index);
                if (inQuote) {
                    if (ch == '\'' && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                        token.append("''");
                        index += 2;
                        continue;
                    }
                    if (ch == '\'') {
                        inQuote = false;
                    }
                    token.append(ch);
                    index++;
                    continue;
                }
                if (ch == '\'') {
                    inQuote = true;
                    token.append(ch);
                    index++;
                    continue;
                }
                if (ch == ',') {
                    tuple.add(token.toString().trim());
                    token.setLength(0);
                    index++;
                    continue;
                }
                if (ch == ')') {
                    tuple.add(token.toString().trim());
                    index++;
                    break;
                }
                token.append(ch);
                index++;
            }
            tuples.add(tuple);
            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
                index++;
            }
            if (index < sql.length() && sql.charAt(index) == ',') {
                index++;
                continue;
            }
            break;
        }
        return tuples;
    }

    private static KinshipRule toRule(List<String> columns, List<String> values) {
        if (columns.size() != values.size()) {
            throw new IllegalStateException("Seed SQL loi: " + columns.size() + " cot nhung "
                    + values.size() + " gia tri: " + values);
        }
        String relationCode = requireText(columns, values, "relation_code");
        String title = requireText(columns, values, "title");
        KinshipRule.Builder builder = KinshipRule.builder(relationCode, title);

        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            String raw = values.get(i);
            switch (column) {
                case "rule_set_id", "relation_code", "title", "description" -> {
                    // rule_set_id va description khong tham gia so khop.
                }
                case "gen_delta" -> builder.genDelta(number(raw));
                case "gen_delta_min" -> builder.genDeltaMin(number(raw));
                case "gen_delta_max" -> builder.genDeltaMax(number(raw));
                case "collateral_degree" -> builder.collateralDegree(number(raw));
                case "collateral_degree_min" -> builder.collateralDegreeMin(number(raw));
                case "collateral_degree_max" -> builder.collateralDegreeMax(number(raw));
                case "side" -> builder.side(RelationSide.fromDb(text(raw)));
                case "gender" -> builder.gender(gender(text(raw)));
                case "is_elder" -> builder.isElder(bool(raw));
                case "link_side" -> builder.linkSide(RelationSide.fromDb(text(raw)));
                case "link_gender" -> builder.linkGender(gender(text(raw)));
                case "in_law_direction" -> builder.inLawDirection(InLawDirection.fromDb(text(raw)));
                case "direct_link" -> builder.directLink(DirectLinkType.fromDb(text(raw)));
                case "direct_link_subtype" -> builder.directLinkSubtype(text(raw));
                case "direct_link_reversed" -> builder.directLinkReversed(Boolean.TRUE.equals(bool(raw)));
                case "title_short" -> builder.titleShort(text(raw));
                case "ego_self_term" -> builder.egoSelfTerm(text(raw));
                case "priority" -> builder.priority(number(raw));
                case "is_active" -> builder.active(!Boolean.FALSE.equals(bool(raw)));
                default -> throw new IllegalStateException(
                        "Cot '" + column + "' cua kinship_rule chua duoc anh xa trong SeedKinshipRules");
            }
        }
        return builder.build();
    }

    private static String requireText(List<String> columns, List<String> values, String column) {
        int index = columns.indexOf(column);
        if (index < 0) {
            throw new IllegalStateException("Seed SQL thieu cot bat buoc '" + column + "'");
        }
        String value = text(values.get(index));
        if (value == null) {
            throw new IllegalStateException("Cot '" + column + "' khong duoc NULL");
        }
        return value;
    }

    private static String text(String raw) {
        if (raw == null || raw.isEmpty() || "NULL".equalsIgnoreCase(raw)) {
            return null;
        }
        if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1).replace("''", "'");
        }
        return raw;
    }

    private static Integer number(String raw) {
        String value = text(raw);
        return value == null ? null : Integer.valueOf(value);
    }

    private static Boolean bool(String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "TRUE" -> Boolean.TRUE;
            case "FALSE" -> Boolean.FALSE;
            default -> throw new IllegalStateException("Gia tri boolean la: " + value);
        };
    }

    private static Gender gender(String value) {
        return value == null ? null : Gender.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
