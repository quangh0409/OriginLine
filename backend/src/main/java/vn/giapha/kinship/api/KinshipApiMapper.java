package vn.giapha.kinship.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.api.dto.EffectiveKinshipRuleSetDto;
import vn.giapha.kinship.api.dto.KinshipResultDto;
import vn.giapha.kinship.api.dto.KinshipRuleDto;
import vn.giapha.kinship.api.dto.KinshipRuleSetDto;
import vn.giapha.kinship.api.dto.KinshipRuleSetUpdateRequest;
import vn.giapha.kinship.application.EffectiveRuleSetView;
import vn.giapha.kinship.application.KinshipQueryResult;
import vn.giapha.kinship.application.RelationPathStep;
import vn.giapha.kinship.domain.KinshipRule;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.RelationCode;
import vn.giapha.kinship.domain.RelationSide;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.kinship.domain.RuleSetRepository;
import vn.giapha.shared.exception.DomainException;

/**
 * Chuyển đổi giữa hợp đồng HTTP/GraphQL và mô hình của tầng application.
 *
 * <p>Thuần ánh xạ, không một quyết định nghiệp vụ nào — mọi suy luận danh xưng đã xong ở domain
 * trước khi tới đây. Tách thành lớp riêng để controller chỉ còn lo chuyện HTTP và quyền.</p>
 */
final class KinshipApiMapper {

    private KinshipApiMapper() {
    }

    // ---------------------------------------------------------------------------------------
    // Ket qua tra danh xung
    // ---------------------------------------------------------------------------------------

    static KinshipResultDto toDto(KinshipQueryResult result) {
        List<KinshipResultDto.KinshipPathStepDto> path = new ArrayList<>(result.path().size());
        for (RelationPathStep step : result.path()) {
            path.add(new KinshipResultDto.KinshipPathStepDto(step.personId(), step.displayName(),
                    step.generation(), step.direction(), step.viaRelType()));
        }

        KinshipQueryResult.Facts facts = result.facts();
        KinshipResultDto.RelationFactsDto factsDto = facts == null ? null
                : new KinshipResultDto.RelationFactsDto(facts.genDelta(), facts.collateralDegree(),
                        facts.side(), facts.targetGender(), facts.isElder(), facts.linkSide(),
                        facts.linkGender(), facts.inLawDirection(), facts.throughMarriage(),
                        facts.throughAdoption());

        KinshipQueryResult.Lca lca = result.lca();
        KinshipResultDto.LcaInfoDto lcaDto = lca == null ? null
                : new KinshipResultDto.LcaInfoDto(lca.personId(), lca.displayName(),
                        lca.generation(), lca.distanceFrom(), lca.distanceTo());

        return new KinshipResultDto(
                result.status(),
                result.fromPersonId(),
                result.toPersonId(),
                result.title(),
                result.titleShort(),
                result.egoSelfTerm(),
                result.reciprocalTitle(),
                // titleEn: khong co nguon du lieu -> khong bia. Xem javadoc KinshipResultDto.
                null,
                result.relationCode(),
                factsDto,
                lcaDto,
                path,
                result.ruleSetId(),
                result.ruleSetScope(),
                result.ruleId(),
                result.ruleSetChain(),
                result.cached());
    }

    // ---------------------------------------------------------------------------------------
    // Bo luat
    // ---------------------------------------------------------------------------------------

    static KinshipRuleSetDto toDto(KinshipRuleSet set) {
        List<KinshipRuleDto> rules = new ArrayList<>(set.ownRules().size());
        for (KinshipRule rule : set.ownRules()) {
            rules.add(toDto(rule, null, null, null));
        }
        KinshipRuleSet parent = set.parent();
        return new KinshipRuleSetDto(
                set.id(),
                set.code(),
                set.scope(),
                set.name(),
                set.region(),
                // clanId: bang kinship_rule_set (V3) khong co cot nay - xem javadoc KinshipRuleSetDto.
                null,
                set.branchId(),
                parent == null ? null : parent.id(),
                set.scope() == RuleScope.DEFAULT,
                set.active(),
                rules,
                set.version(),
                null,
                null);
    }

    static EffectiveKinshipRuleSetDto toDto(EffectiveRuleSetView view) {
        List<EffectiveKinshipRuleSetDto.ChainEntry> chain = new ArrayList<>(view.chain().size());
        for (EffectiveRuleSetView.RuleSetRef ref : view.chain()) {
            chain.add(new EffectiveKinshipRuleSetDto.ChainEntry(ref.ruleSetId(), ref.scope(),
                    ref.code(), ref.name(), ref.version()));
        }
        List<KinshipRuleDto> rules = new ArrayList<>(view.rules().size());
        for (EffectiveRuleSetView.EffectiveRule rule : view.rules()) {
            rules.add(toDto(rule.rule(), rule.inheritedFromScope(), rule.inheritedFromRuleSetId(),
                    rule.overridden()));
        }
        return new EffectiveKinshipRuleSetDto(view.branchId(), view.leafRuleSetId(), chain, rules);
    }

    static KinshipRuleDto toDto(KinshipRule rule, RuleScope inheritedFromScope,
            UUID inheritedFromRuleSetId, Boolean overridden) {
        return new KinshipRuleDto(
                rule.id(),
                rule.relationCode().value(),
                rule.genDelta(),
                rule.genDeltaMin(),
                rule.genDeltaMax(),
                rule.collateralDegree(),
                rule.collateralDegreeMin(),
                rule.collateralDegreeMax(),
                rule.side(),
                rule.gender(),
                rule.isElder(),
                rule.linkSide(),
                rule.linkGender(),
                rule.inLawDirection(),
                rule.directLink(),
                rule.directLinkSubtype(),
                rule.directLinkReversed(),
                // throughMarriage khong phai cot rieng o domain: no la he qua cua side = IN_LAW.
                rule.side() == null ? null : rule.side() == RelationSide.IN_LAW,
                rule.title(),
                rule.titleShort(),
                rule.egoSelfTerm(),
                rule.priority(),
                rule.active(),
                rule.description(),
                inheritedFromScope,
                inheritedFromRuleSetId,
                overridden);
    }

    // ---------------------------------------------------------------------------------------
    // Ghi de bo luat
    // ---------------------------------------------------------------------------------------

    static RuleSetRepository.RuleSetCommand toCommand(KinshipRuleSetUpdateRequest request) {
        List<KinshipRule> rules = new ArrayList<>();
        int index = 0;
        for (KinshipRuleDto dto : request.rules()) {
            rules.add(toDomain(dto, index++));
        }
        return new RuleSetRepository.RuleSetCommand(
                request.ruleSetId(),
                request.code(),
                request.name(),
                request.scope(),
                request.region(),
                request.branchId(),
                request.parentRuleSetId(),
                // description la mo ta cua bo luat; request.note() la ly do thay doi (audit_log).
                request.description(),
                rules,
                request.expectedVersion());
    }

    /**
     * Một luật gửi lên.
     *
     * <p>{@code relationCode} và {@code title} bắt buộc: mã là khoá ghi đè giữa các cấp bộ luật
     * ({@code NOT NULL} trong CSDL), còn một luật không có danh xưng thì không phải là luật. Báo
     * lỗi kèm chỉ số phần tử để Hội đồng biết dòng nào sai trong một mảng vài trăm luật.</p>
     */
    private static KinshipRule toDomain(KinshipRuleDto dto, int index) {
        if (dto.relationCode() == null || dto.relationCode().isBlank()) {
            throw new DomainException("VALIDATION_FAILED",
                    "rules[" + index + "].relationCode khong duoc bo trong");
        }
        if (dto.title() == null || dto.title().isBlank()) {
            throw new DomainException("VALIDATION_FAILED",
                    "rules[" + index + "].title khong duoc bo trong");
        }
        int priority = dto.priority() == null ? 100 : dto.priority();
        if (priority < 0) {
            throw new DomainException("VALIDATION_FAILED",
                    "rules[" + index + "].priority phai >= 0");
        }
        return new KinshipRule(
                dto.id() == null ? UUID.randomUUID() : dto.id(),
                // ruleSetId do repository gan khi ghi - client khong duoc tu chon.
                null,
                RelationCode.of(dto.relationCode()),
                dto.genDelta(),
                dto.genDeltaMin(),
                dto.genDeltaMax(),
                dto.collateralDegree(),
                dto.collateralDegreeMin(),
                dto.collateralDegreeMax(),
                sideOf(dto),
                dto.gender(),
                dto.isElder(),
                dto.linkSide(),
                dto.linkGender(),
                dto.inLawDirection(),
                dto.directLink(),
                dto.directLinkSubtype(),
                Boolean.TRUE.equals(dto.directLinkReversed()),
                dto.title(),
                dto.titleShort(),
                dto.reciprocalTitle(),
                dto.note(),
                priority,
                dto.active() == null || dto.active(),
                null,
                null);
    }

    /**
     * {@code throughMarriage = true} mà bỏ trống {@code side} thì hiểu là {@code IN_LAW} — hợp đồng
     * cho phép khai bằng cờ, còn CSDL chỉ có một cột {@code side}. Khai cả hai mà mâu thuẫn nhau
     * thì {@code side} thắng, vì đó mới là thứ engine so khớp.
     */
    private static RelationSide sideOf(KinshipRuleDto dto) {
        if (dto.side() != null) {
            return dto.side();
        }
        return Boolean.TRUE.equals(dto.throughMarriage()) ? RelationSide.IN_LAW : null;
    }
}
