package vn.giapha.kinship.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ánh xạ bảng {@code kinship_rule} (migration V3) — một luật danh xưng.
 *
 * <p>Mọi cột so khớp đều là kiểu bọc ({@code Integer}, {@code Boolean}) chứ không phải kiểu nguyên
 * thuỷ, vì {@code null} ở đây mang nghĩa nghiệp vụ rõ ràng: <b>"không xét chiều này"</b>. Đổi sang
 * {@code int} là mất luôn khác biệt giữa "không xét" và "bằng 0" — mà {@code gen_delta = 0} nghĩa
 * là cùng đời, một giá trị hoàn toàn hợp lệ.</p>
 */
@Entity
@Table(name = "kinship_rule")
public class KinshipRuleEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(name = "relation_code", nullable = false, length = 48)
    private String relationCode;

    @Column(name = "gen_delta")
    private Integer genDelta;

    @Column(name = "gen_delta_min")
    private Integer genDeltaMin;

    @Column(name = "gen_delta_max")
    private Integer genDeltaMax;

    @Column(name = "collateral_degree")
    private Integer collateralDegree;

    @Column(name = "collateral_degree_min")
    private Integer collateralDegreeMin;

    @Column(name = "collateral_degree_max")
    private Integer collateralDegreeMax;

    @Column(name = "side", length = 10)
    private String side;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "is_elder")
    private Boolean isElder;

    @Column(name = "link_side", length = 10)
    private String linkSide;

    @Column(name = "link_gender", length = 10)
    private String linkGender;

    @Column(name = "in_law_direction", length = 16)
    private String inLawDirection;

    @Column(name = "direct_link", length = 20)
    private String directLink;

    @Column(name = "direct_link_subtype", length = 16)
    private String directLinkSubtype;

    @Column(name = "direct_link_reversed", nullable = false)
    private boolean directLinkReversed;

    @Column(name = "title", nullable = false, length = 64)
    private String title;

    @Column(name = "title_short", length = 32)
    private String titleShort;

    @Column(name = "ego_self_term", length = 32)
    private String egoSelfTerm;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KinshipRuleEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRuleSetId() {
        return ruleSetId;
    }

    public void setRuleSetId(UUID ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    public String getRelationCode() {
        return relationCode;
    }

    public void setRelationCode(String relationCode) {
        this.relationCode = relationCode;
    }

    public Integer getGenDelta() {
        return genDelta;
    }

    public void setGenDelta(Integer genDelta) {
        this.genDelta = genDelta;
    }

    public Integer getGenDeltaMin() {
        return genDeltaMin;
    }

    public void setGenDeltaMin(Integer genDeltaMin) {
        this.genDeltaMin = genDeltaMin;
    }

    public Integer getGenDeltaMax() {
        return genDeltaMax;
    }

    public void setGenDeltaMax(Integer genDeltaMax) {
        this.genDeltaMax = genDeltaMax;
    }

    public Integer getCollateralDegree() {
        return collateralDegree;
    }

    public void setCollateralDegree(Integer collateralDegree) {
        this.collateralDegree = collateralDegree;
    }

    public Integer getCollateralDegreeMin() {
        return collateralDegreeMin;
    }

    public void setCollateralDegreeMin(Integer collateralDegreeMin) {
        this.collateralDegreeMin = collateralDegreeMin;
    }

    public Integer getCollateralDegreeMax() {
        return collateralDegreeMax;
    }

    public void setCollateralDegreeMax(Integer collateralDegreeMax) {
        this.collateralDegreeMax = collateralDegreeMax;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Boolean getIsElder() {
        return isElder;
    }

    public void setIsElder(Boolean isElder) {
        this.isElder = isElder;
    }

    public String getLinkSide() {
        return linkSide;
    }

    public void setLinkSide(String linkSide) {
        this.linkSide = linkSide;
    }

    public String getLinkGender() {
        return linkGender;
    }

    public void setLinkGender(String linkGender) {
        this.linkGender = linkGender;
    }

    public String getInLawDirection() {
        return inLawDirection;
    }

    public void setInLawDirection(String inLawDirection) {
        this.inLawDirection = inLawDirection;
    }

    public String getDirectLink() {
        return directLink;
    }

    public void setDirectLink(String directLink) {
        this.directLink = directLink;
    }

    public String getDirectLinkSubtype() {
        return directLinkSubtype;
    }

    public void setDirectLinkSubtype(String directLinkSubtype) {
        this.directLinkSubtype = directLinkSubtype;
    }

    public boolean isDirectLinkReversed() {
        return directLinkReversed;
    }

    public void setDirectLinkReversed(boolean directLinkReversed) {
        this.directLinkReversed = directLinkReversed;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleShort() {
        return titleShort;
    }

    public void setTitleShort(String titleShort) {
        this.titleShort = titleShort;
    }

    public String getEgoSelfTerm() {
        return egoSelfTerm;
    }

    public void setEgoSelfTerm(String egoSelfTerm) {
        this.egoSelfTerm = egoSelfTerm;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
