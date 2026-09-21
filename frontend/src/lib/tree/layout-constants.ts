/** Shared sizing so every layout algorithm (dagre/d3/matrix) positions nodes
 * the same custom PersonNode component expects.
 *
 * <p><b>PersonNode IMPORTS {@link NODE_WIDTH} / {@link NODE_HEIGHT}</b> thay vì chép tay hai con
 * số. Trước đây nó chép tay và một ca kiểm phải canh cho hai chỗ khỏi lệch nhau; nay chúng không
 * thể lệch.</p>
 */

/**
 * Bề ngang tấm thẻ nhân khẩu — **160px, thu từ 208px**.
 *
 * <h2>Vì sao thu, và thu được nhờ đâu</h2>
 * Thẻ 208px dựng ra để chở BỐN dòng: tên · đời + chi · năm sinh–năm mất · huy hiệu. Nhưng sau mô
 * hình riêng tư V8, dòng ngày của **người còn sống thường trống** (xem
 * `src/lib/tree/life-dates.ts`) — tức tấm thẻ đang giữ chỗ cho thứ không có. Bản mới cho **tên
 * xuống hai dòng** thay vì cắt bằng dấu ba chấm (định hướng 00 §3: "tên người là DỮ LIỆU, không
 * bao giờ cắt bớt nếu tránh được"), nên bề ngang không còn là thứ quyết định đọc được bao nhiêu
 * chữ của một cái tên.
 *
 * <h2>Sàn dưới, đo được</h2>
 * {@link TOGGLE_HIT_SIZE} = 80px phải **hẹp hơn hẳn** tấm thẻ, nếu không vùng chạm của hai anh em
 * kề nhau sẽ ăn vào nhau (ghim ở `tests/unit/tree/toggle-hit-area.test.ts`). 160px để lại 80px
 * dư — gấp đôi sàn.
 *
 * <h2>Hệ quả số học — đây là con số người dùng cảm thấy</h2>
 * ```
 *   một cặp vợ chồng        2×160 + 36 = 356px   (trước: 2×208 + 48 = 464px)
 *   bước sang gia đình kế     356 + 56 = 412px   (trước:      464 + 80 = 544px)  −24%
 *   8 gia đình cùng một đời        3.296px       (trước: 4.352px)
 *   15 gia đình                    6.180px       (trước: 8.160px)
 * ```
 * Thu thẻ một mình KHÔNG giải xong bài toán — 3.296px vẫn là hơn hai màn hình. Phần còn lại do
 * **gập nhánh mặc định** gánh (xem `focus-path.ts` và `useTreeCanvas`).
 */
export const NODE_WIDTH = 160;

/**
 * Chiều cao tối thiểu tấm thẻ — **giữ nguyên 96px, cố ý**.
 *
 * Không thu được: {@link TOGGLE_HIT_CARD_INTRUSION} = 40px ăn lên phía trong thẻ, và bất biến
 * "chạm vào giữa thẻ thì mở hồ sơ" đòi ô chạm không với tới tâm thẻ — tức {@code NODE_HEIGHT} phải
 * **lớn hơn 80px**. Ở 96px còn dư đúng 8px. Thu xuống 88px thì dư 4px, và 4px là khoảng mà sai số
 * làm tròn của trình duyệt ăn hết.
 *
 * Chiều dọc cũng không phải chỗ đau: 14 đời × 208px = 2.912px, so với một đời rộng vài nghìn px.
 */
export const NODE_HEIGHT = 96;

export const HIERARCHICAL_RANK_SEP = 96;
export const HIERARCHICAL_NODE_SEP = 32;

export const MATRIX_COL_GAP = 24;
export const MATRIX_ROW_HEIGHT = NODE_HEIGHT + 80;

/**
 * Bán kính mỗi vòng ở chế độ Toả tròn.
 *
 * Phải ≥ {@code NODE_WIDTH × 0.9} = 144px để hai vòng kề nhau không chồng thẻ theo chiều bán kính
 * (ghim ở `tests/unit/tree/layout-constants.test.ts`).
 */
export const RADIAL_RADIUS_STEP = 150;

/* --------------------------------------------------------------------------
   Bố cục theo ĐƠN VỊ GIA ĐÌNH (phả đồ dựng lại — Hội đồng đã duyệt).
   Các số này quyết định phả đồ có "thở" được hay không; xem bản thiết kế để
   biết vì sao chọn từng số.
--------------------------------------------------------------------------- */

/**
 * Khe giữa hai vợ chồng — **36px, thu từ 48px**.
 *
 * Vừa là chiều dài thanh hôn phối, vừa là HÀNH LANG cho đường rơi xuống con đi qua — nhờ nó mà
 * đường nối không bao giờ gặp một tấm thẻ nào. Cơ chế ấy **không** phụ thuộc vào con số cụ thể,
 * chỉ cần hành lang đủ rộng để đoạn rơi (nét 1,75px) nằm lọt và còn lề hai bên: 36px cho mỗi bên
 * ~17px lề. Bất biến "đoạn dọc đi qua khe giữa hai vợ chồng" được ghim ở
 * `tests/unit/tree/geometry-invariants.test.ts` và tính THẲNG từ hằng số này, nên nó không thể
 * xanh vì một con số cũ. Đừng thu tiếp mà không chạy lại bộ ấy.
 */
export const COUPLE_GAP = 36;

/**
 * Khe giữa hai gia đình khác nhau — **56px, thu từ 80px**.
 *
 * PHẢI lớn hơn hẳn {@link HIERARCHICAL_NODE_SEP} (khe giữa anh em ruột, 32px), nếu không các nhóm
 * con dính vào nhau thành một dải liền và mất hẳn ranh giới gia đình. 56 / 32 = 1,75 lần — vẫn là
 * một bước nhảy nhìn ra được, trong khi 80px thì đang mua ranh giới ấy bằng nửa tấm thẻ.
 */
export const FAMILY_GAP = 56;

/**
 * Khoảng cách giữa hai đời — **112px, thu từ 128px**.
 *
 * Hành lang này chứa ba thứ chồng lên nhau: đoạn rơi từ thanh hôn phối, thanh anh em, rồi đoạn rơi
 * xuống thẻ con. Phần dùng được là {@code FAMILY_RANK_SEP − 2 × CORRIDOR_MARGIN} = 112 − 32 = 80px
 * cho các thanh anh em xếp so le; {@link SIBLING_BAR_STAGGER} tự co lại khi không đủ chỗ. Vẫn lớn
 * hơn {@link TOGGLE_HIT_OVERHANG} (40px) nên ô chạm của đời trên không với tới đời dưới.
 */
export const FAMILY_RANK_SEP = 112;

/** Độ lệch tầng giữa các thanh anh em của những bà vợ khác nhau — để nhìn là biết ai con bà nào. */
export const SIBLING_BAR_STAGGER = 36;

/* --------------------------------------------------------------------------
   VÙNG CHẠM của nút bung/thu nhánh.

   Bài toán: thẻ nhân khẩu rộng 160px và phần lớn bề ngang ấy là TÊN NGƯỜI. Phóng to nút bấm lên
   cho đủ ngưỡng chạm là ăn mất chỗ của tên — thứ duy nhất khiến phả đồ có nghĩa. Nên phần NHÌN
   THẤY giữ nguyên vòng tròn {@link TOGGLE_KNOB_SIZE}, còn vùng NHẬN CÚ CHẠM được nới rộng ra
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
 * canvas vẫn bấm được (xem {@link TOGGLE_HIT_SIZE}). Chọn 0.55 vì đó là mức mà thẻ 160×96 co còn
 * 88×53 — vẫn đọc được tên (tên nay xuống hai dòng), tức người dùng vẫn đang LÀM VIỆC với từng
 * người chứ không phải nhìn cả họ như một bức tranh.
 *
 * **Cố ý không đổi khi thu thẻ.** Nó là mẫu số của phép chia dưới đây; đổi nó là đổi vùng chạm, mà
 * vùng chạm đang vừa khít sàn 44px. Thẻ hẹp đi thì cây gọn lại ⇒ `fitView` tự chọn mức phóng CAO
 * HƠN, tức chữ trên màn hình to hơn — lợi ích đến từ phía ấy, không phải từ việc nới sàn này.
 */
export const OPERABLE_MIN_ZOOM = 0.55;

/**
 * Phần NHÌN THẤY của nút — vòng tròn có dấu `+`/`−`, hoặc **số người con đang ẩn** khi nhánh thu
 * gọn và máy chủ đã nói `childCount`. Cố ý không đổi kích thước: chỗ đâu mà to thêm.
 */
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
 * {@code NODE_WIDTH + HIERARCHICAL_NODE_SEP} = 192px). Một ô cố định 80px thì mọi mức phóng đều an
 * toàn, và không phải theo dõi mức phóng ở từng thẻ — thứ sẽ dựng lại hàng nghìn nút mỗi nấc lăn
 * chuột.
 *
 * Thẻ thu từ 208 xuống 160px **không** đụng tới con số này, và đó là điều phải kiểm: ô chạm vẫn
 * hẹp hơn thẻ (80 &lt; 160) nên hai vùng chạm kề nhau vẫn hở {@code 192 − 80} = 112px.
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
 * 96px (thẻ dùng {@code minHeight}, ô chạm neo theo đáy THẬT). **Đây là lý do {@link NODE_HEIGHT}
 * không được thu xuống dưới 96px**, kể cả khi bỏ bớt một dòng nội dung.
 */
export const TOGGLE_HIT_CARD_INTRUSION = TOGGLE_HIT_SIZE / 2;

/** Phần ô chạm thò xuống dưới đáy thẻ: 40px, nằm gọn trong hành lang {@link FAMILY_RANK_SEP} 112px. */
export const TOGGLE_HIT_OVERHANG = TOGGLE_HIT_SIZE - TOGGLE_HIT_CARD_INTRUSION;

/** Khoảng đệm từ mép trên ô chạm xuống mép trên vòng tròn, để vòng tròn vẫn nằm đúng chỗ cũ. */
export const TOGGLE_KNOB_INSET = TOGGLE_HIT_CARD_INTRUSION - TOGGLE_KNOB_SIZE / 2;
