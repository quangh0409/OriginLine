/**
 * Bounded context <b>kinship</b> — tìm tổ tiên chung gần nhất (LCA) và suy luận danh xưng qua rule engine cấu hình được.
 *
 * <p>Khái niệm lõi: KinshipRuleSet, KinshipRule, KinshipResolver (POJO thuần, không Spring/DB), RelationFacts; port LcaPort, RuleSetRepository. Luật kế thừa DEFAULT → REGION → CLAN → BRANCH.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Kinship — Danh xưng")
package vn.giapha.kinship;
