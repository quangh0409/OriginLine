import type { NodePosition } from "./layout-hierarchical";

/**
 * Hợp đồng dùng chung cho phả đồ dựng theo <b>đơn vị gia đình</b>.
 *
 * <p>Tệp này <b>chỉ chứa kiểu</b>, không chứa cài đặt. Ba phần việc — suy ra đơn vị gia đình, tính
 * bố cục, và vẽ ra React Flow — được viết song song và ghép vào nhau qua đúng các kiểu ở đây. Sửa
 * tệp này là sửa giao kèo giữa ba phần, nên đừng đổi nếu chưa cân nhắc cả ba.</p>
 *
 * <h2>Vì sao phải có khái niệm "đơn vị gia đình"</h2>
 *
 * <p>Bản dựng cũ nối <b>người với người</b>: mỗi người cha và mỗi người mẹ tự kẻ một đường riêng
 * tới từng đứa con, nên một cặp 5 con sinh ra 10 đường chéo. Tệ hơn, đường vợ chồng đi từ neo TRÁI
 * của người này sang neo PHẢI của người kia, nên với hai thẻ rộng 208px đứng cạnh nhau nó dài 464px
 * mà <b>416px nằm dưới hai tấm thẻ</b> — người dùng chỉ thấy một mẩu 48px ở khe giữa.</p>
 *
 * <p>Bản mới nối <b>người với đơn vị gia đình</b>: mỗi cuộc hôn phối sinh ra một điểm nối, con cái
 * treo xuống từ đó. Mọi đoạn đều thẳng đứng hoặc nằm ngang và nằm gọn trong khe dọc giữa hai vợ
 * chồng hoặc dải ngang giữa hai đời, nên <b>không đoạn nào chạm vào vùng có thẻ</b>.</p>
 */

/**
 * Một cuộc hôn phối cùng toàn bộ con cái của nó — đơn vị nhỏ nhất mà bố cục thao tác.
 *
 * <p>Cha/mẹ đơn thân cũng là một đơn vị hợp lệ ({@code partnerIds} chỉ có một người): gia phả cổ
 * rất hay ghi nhận một người con mà không rõ mẹ.</p>
 */
export interface FamilyUnit {
    /** Ổn định giữa các lần dựng lại — suy từ id các thành viên, không dùng số đếm. */
    readonly id: string;

    /**
     * Một hoặc hai người. Khi có hai, thứ tự trong mảng KHÔNG mang nghĩa trái–phải trên màn hình;
     * việc xếp chỗ là của tầng bố cục.
     */
    readonly partnerIds: readonly string[];

    /**
     * Người "trục" của đơn vị — khi đa thê thì đây là người có nhiều bạn đời, và Hội đồng đã chốt
     * là người này <b>đứng giữa</b>, các bà toả ra hai bên theo thứ tự.
     */
    readonly anchorId: string;

    /** {@code spouse_order} của bạn đời so với người trục; null khi không áp dụng. */
    readonly spouseOrder: number | null;

    /** Con cái của riêng cuộc hôn phối này, đã sắp theo thứ tự sinh khi biết. */
    readonly childIds: readonly string[];

    /** Con nuôi — vẽ nét đứt ở đoạn rơi xuống người con, KHÔNG đứt cả thanh anh em. */
    readonly adoptedChildIds: ReadonlySet<string>;

    /** Hôn phối đã kết thúc (ly hôn/goá) — thanh hôn phối vẽ nét đứt, vẫn giữ trên cây. */
    readonly ended: boolean;
}

/** Đoạn thẳng ngang. Mọi toạ độ tính theo hệ toạ độ canvas, cùng gốc với vị trí thẻ. */
export interface HorizontalBar {
    readonly x1: number;
    readonly x2: number;
    readonly y: number;
}

/** Đoạn rơi thẳng đứng xuống một người con. */
export interface ChildDrop {
    readonly childId: string;
    readonly x: number;
    readonly yFrom: number;
    readonly yTo: number;
    /** Con nuôi ⇒ nét đứt. */
    readonly dashed: boolean;
}

/**
 * Toàn bộ hình học cần để vẽ một đơn vị gia đình. Tầng vẽ chỉ việc đọc, không tính toán gì thêm —
 * mọi quyết định hình học nằm ở tầng bố cục để test được mà không cần trình duyệt.
 */
export interface FamilyJunction {
    readonly unitId: string;

    /** Điểm nối: giữa thanh hôn phối, hoặc ngay dưới thẻ khi cha/mẹ đơn thân. */
    readonly x: number;
    readonly y: number;

    /** Thanh hôn phối nằm gọn trong khe giữa hai vợ chồng; null khi đơn thân. */
    readonly marriageBar: HorizontalBar | null;

    /** Thanh anh em; null khi đơn vị chỉ có một con (lúc đó rơi thẳng, không cần thanh). */
    readonly siblingBar: HorizontalBar | null;

    /** Đoạn dọc từ điểm nối xuống thanh anh em; null khi không có con. */
    readonly stem: { readonly x: number; readonly yFrom: number; readonly yTo: number } | null;

    readonly childDrops: readonly ChildDrop[];

    /** Hôn phối đã kết thúc ⇒ thanh hôn phối nét đứt. */
    readonly ended: boolean;
}

/**
 * Cạnh KHÔNG vừa khuôn cây gia đình, phải vẽ như đường phụ.
 *
 * <p>Đây là chỗ mà một thư viện vẽ cây có sẵn sẽ làm hỏng gia phả Việt. Cây gia đình thuần giả định
 * <b>mỗi người có đúng một chỗ đứng</b>, nhưng người tái hôn thuộc hai đơn vị cùng lúc, và con nuôi
 * có thể đến từ một chi khác hẳn. Những cạnh đó không được phép biến mất khỏi cây — chúng được vẽ
 * riêng, đi vòng theo hành lang giữa hai đời thay vì cắt thẳng qua các thẻ.</p>
 */
export interface AuxiliaryLink {
    readonly id: string;
    readonly sourceId: string;
    readonly targetId: string;
    readonly kind: "REMARRIAGE" | "CROSS_BRANCH_PARENT" | "HEIR";
    /** Các điểm gãy đã tính sẵn, chỉ gồm đoạn ngang và đoạn dọc. */
    readonly waypoints: readonly { readonly x: number; readonly y: number }[];
}

/** Kết quả của tầng bố cục. */
export interface FamilyLayout {
    /** id nhân khẩu → vị trí góc trên-trái của thẻ. */
    readonly positions: Map<string, NodePosition>;
    readonly junctions: readonly FamilyJunction[];
    readonly auxiliaryLinks: readonly AuxiliaryLink[];
}
