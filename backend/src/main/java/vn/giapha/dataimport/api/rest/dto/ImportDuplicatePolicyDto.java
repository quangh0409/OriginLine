package vn.giapha.dataimport.api.rest.dto;

/**
 * Chính sách nghi trùng do <b>máy chủ</b> công bố — giao diện không ghi cứng con số nào.
 *
 * @param suspectThreshold điểm tối thiểu để sinh cảnh báo {@code IMP_SUSPECT_DUPLICATE}
 * @param preselectMergeThreshold điểm từ đó giao diện tick sẵn "hợp nhất"; vẫn phải người xác nhận
 * @param autoMerge luôn {@code false}, và trường này tồn tại để điều đó là một <b>lời hứa của hợp
 *        đồng</b> chứ không phải một câu trong tài liệu
 */
public record ImportDuplicatePolicyDto(int suspectThreshold,
                                       int preselectMergeThreshold,
                                       boolean autoMerge) {
}
