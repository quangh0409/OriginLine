package vn.giapha.genealogy.domain;

/**
 * Mức chính xác thật sự của một mốc thời gian trong gia phả.
 *
 * <p>Gia phả giấy thường chỉ còn năm, hoặc chỉ còn ngày–tháng âm mà mất năm. Ghi lại mức chính xác
 * để tầng hiển thị không bịa ra ngày {@code 01} rồi trình bày như thể đã biết chắc.</p>
 *
 * <p>Cũng là công cụ của phân tầng riêng tư: với người còn sống, Tầng 2 chỉ được thấy phần
 * <b>năm</b> — tức là cùng một giá trị nhưng bị hạ mức xuống {@link #YEAR}.</p>
 */
public enum DatePrecision {
    DAY,
    MONTH,
    YEAR,
    UNKNOWN
}
