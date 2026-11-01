package vn.giapha.kinship.application;

import java.util.UUID;

/**
 * Bộ luật danh xưng tầng {@code CLAN} nhìn từ ngoài context — named interface
 * {@code "bo-luat-dong-ho"}.
 *
 * <p>Chỉ kiểu nguyên thuỷ: không {@code KinshipRuleSet}, không {@code Region} của domain — mở
 * {@code kinship.domain} ra ngoài là để luật danh xưng bị đọc/diễn giải ở nơi thứ hai.</p>
 *
 * @param ruleSetId   id của bộ luật tầng {@code CLAN}
 * @param code        mã ổn định, ví dụ {@code CLAN_HO_NGUYEN_DINH}
 * @param region      vùng đang gắn với bộ ({@code BAC/TRUNG/NAM}) — vùng mà nó kế thừa
 * @param parentCode  mã bộ cha thật sự: {@code REGION_*} nếu vùng ấy có bộ đang bật, không thì
 *                    {@code DEFAULT_BAC}
 * @param customized  Hội đồng đã tự khai luật ở tầng này chưa (bộ có luật riêng)
 * @param ownRuleCount số luật riêng của tầng {@code CLAN}
 */
@org.springframework.modulith.NamedInterface("bo-luat-dong-ho")
public record ClanRuleSetView(UUID ruleSetId, String code, String region, String parentCode,
                              boolean customized, int ownRuleCount) {
}
