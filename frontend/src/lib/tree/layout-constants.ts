/** Shared sizing so every layout algorithm (dagre/d3/matrix) positions nodes
 * the same custom PersonNode component expects. Keep in sync with the actual
 * rendered card size in components/tree/person-node.tsx. */
export const NODE_WIDTH = 208;
export const NODE_HEIGHT = 96;

export const HIERARCHICAL_RANK_SEP = 96;
export const HIERARCHICAL_NODE_SEP = 32;

export const MATRIX_COL_GAP = 32;
export const MATRIX_ROW_HEIGHT = NODE_HEIGHT + 96;

export const RADIAL_RADIUS_STEP = 190;

/* --------------------------------------------------------------------------
   Bố cục theo ĐƠN VỊ GIA ĐÌNH (phả đồ dựng lại — Hội đồng đã duyệt).
   Các số này quyết định phả đồ có "thở" được hay không; xem bản thiết kế để
   biết vì sao chọn từng số.
--------------------------------------------------------------------------- */

/**
 * Khe giữa hai vợ chồng. Vừa là chiều dài thanh hôn phối, vừa là HÀNH LANG cho đường rơi xuống
 * con đi qua — nhờ nó mà đường nối không bao giờ gặp một tấm thẻ nào. Thu hẹp số này là làm hỏng
 * chính cơ chế đã sửa được lỗi "đường nối chui dưới thẻ".
 */
export const COUPLE_GAP = 48;

/**
 * Khe giữa hai gia đình khác nhau. PHẢI lớn hơn hẳn {@link HIERARCHICAL_NODE_SEP} (khe giữa anh em
 * ruột), nếu không các nhóm con dính vào nhau thành một dải liền và mất hẳn ranh giới gia đình.
 */
export const FAMILY_GAP = 80;

/**
 * Khoảng cách giữa hai đời. Tăng từ 96 vì nay phải chứa ba thứ chồng lên nhau: đoạn rơi từ thanh
 * hôn phối, thanh anh em, rồi đoạn rơi xuống thẻ con.
 */
export const FAMILY_RANK_SEP = 128;

/** Độ lệch tầng giữa các thanh anh em của những bà vợ khác nhau — để nhìn là biết ai con bà nào. */
export const SIBLING_BAR_STAGGER = 36;

/* --------------------------------------------------------------------------
   VÙNG CHẠM của nút bung/thu nhánh.

   Bài toán: thẻ nhân khẩu rộng đúng 208px và phần lớn bề ngang ấy là TÊN NGƯỜI. Phóng to nút bấm
   lên cho đủ ngưỡng chạm là ăn mất chỗ của tên — thứ duy nhất khiến phả đồ có nghĩa. Nên phần
   NHÌN THẤY giữ nguyên vòng tròn {@link TOGGLE_KNOB_SIZE}, còn vùng NHẬN CÚ CHẠM được nới rộng ra
   trong suốt quanh nó.
--------------------------------------------------------------------------- */

/**
 * Ngưỡng chạm tính bằng px THẬT trên màn hình (không phải px trong hệ toạ độ cây).
 *
 * WCAG 2.5.8 mức AA chỉ đòi 24px; 44px là sàn của cả Apple HIG lẫn Material, và là con số Hội đồng
 * chốt cho màn hình chính — người dùng phả đồ phần lớn là kiều bào bấm bằng ngón cái trên điện
 * thoại, và không ít cụ cao tuổi trong họ.
 */
export const MIN_TOUCH_TARGET_PX = 44;

/**
 * Mức phóng THẤP NHẤT còn coi là "đang thao tác" chứ không phải "đang ngắm toàn cảnh".
 *
 * Đây là tham số dùng để SUY RA kích thước vùng chạm, không phải một cái chốt chặn: dưới mức này
 * canvas vẫn bấm được (xem {@link TOGGLE_HIT_SIZE}). Chọn 0.55 vì đó là mức mà thẻ 208×96 co còn
 * 114×53 — vẫn đọc được tên, tức người dùng vẫn đang LÀM VIỆC với từng người chứ không phải nhìn
 * cả họ như một bức tranh.
 */
export const OPERABLE_MIN_ZOOM = 0.55;

/** Phần NHÌN THẤY của nút — vòng tròn có dấu +/−. Cố ý không đổi: chỗ đâu mà to thêm. */
export const TOGGLE_KNOB_SIZE = 24;

/**
 * Cạnh của vùng chạm, tính trong HỆ TOẠ ĐỘ CÂY (React Flow sẽ nhân nó với mức phóng).
 *
 * 44 / 0.55 = 80. Hệ quả — đây là những con số đã đo được trên trình duyệt:
 *
 * | mức phóng | vùng chạm thật |
 * |---|---|
 * | 0.75 ({@code MIN_INITIAL_ZOOM}, mức cây MỞ RA) | 60px — vượt sàn 44px |
 * | 0.55 ({@link OPERABLE_MIN_ZOOM})               | 44px — vừa đúng sàn |
 * | 0.30                                           | 24px — vẫn đạt WCAG 2.5.8 AA |
 *
 * KHÔNG chọn cách phản-tỉ-lệ (giữ 44px thật ở mọi mức phóng): ở mức 0.1 thì ô chạm phải rộng 440px
 * trong hệ toạ độ cây, tức nuốt trọn cả hai người bên cạnh (bước ngang giữa hai anh em chỉ
 * {@code NODE_WIDTH + HIERARCHICAL_NODE_SEP} = 240px). Một ô cố định 80px thì mọi mức phóng đều an
 * toàn, và không phải theo dõi mức phóng ở từng thẻ — thứ sẽ dựng lại hàng nghìn nút mỗi nấc lăn
 * chuột.
 */
export const TOGGLE_HIT_SIZE = Math.ceil(MIN_TOUCH_TARGET_PX / OPERABLE_MIN_ZOOM);

/**
 * Ô chạm ăn lên phía TRONG thẻ bao nhiêu px.
 *
 * Ô chạm ĐẶT CÂN ĐỐI quanh vòng tròn, tức tâm nó trùng đúng tâm vòng tròn cũ — giữa mép đáy thẻ.
 * Đây không phải chuyện thẩm mỹ mà là chuyện đo được: mọi phép thử "chạm vào giữa nút có trúng nút
 * không" đều lấy mẫu tại TÂM nút, nên hễ dịch tâm đi là kết quả đổi. Bản thử đầu tiên dồn ô chạm
 * xuống hành lang (ăn lên 24, thò xuống 56) đã dịch tâm xuống 16px và rơi đúng vào nút "Xem thêm"
 * của thẻ chú giải ở góc dưới-trái trên Pixel 5 — một nút bung nhánh lập tức bấm không được. Giữ
 * tâm đứng yên thì cả lớp lỗi ấy không bao giờ phát sinh.
 *
 * Giới hạn trên của con số này là TÂM TẤM THẺ: chạm vào giữa thẻ phải mở hồ sơ người ấy, đó là bất
 * biến vừa giành được (xem e2e/tree-legibility.spec.ts). Tâm thẻ cách đáy {@code NODE_HEIGHT / 2} =
 * 48px, ô chạm với tới 40px — còn 8px, và khoảng ấy chỉ NỚI RA chứ không hẹp lại khi thẻ cao hơn
 * 96px (thẻ dùng {@code minHeight}, ô chạm neo theo đáy THẬT).
 */
export const TOGGLE_HIT_CARD_INTRUSION = TOGGLE_HIT_SIZE / 2;

/** Phần ô chạm thò xuống dưới đáy thẻ: 40px, nằm gọn trong hành lang {@link FAMILY_RANK_SEP} 128px. */
export const TOGGLE_HIT_OVERHANG = TOGGLE_HIT_SIZE - TOGGLE_HIT_CARD_INTRUSION;

/** Khoảng đệm từ mép trên ô chạm xuống mép trên vòng tròn, để vòng tròn vẫn nằm đúng chỗ cũ. */
export const TOGGLE_KNOB_INSET = TOGGLE_HIT_CARD_INTRUSION - TOGGLE_KNOB_SIZE / 2;
