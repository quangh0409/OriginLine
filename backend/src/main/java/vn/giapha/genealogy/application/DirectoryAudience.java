package vn.giapha.genealogy.application;

/**
 * Dựng <b>người xem danh bạ</b> từ người gọi thật: giữ nguyên phạm vi chi/ngành, <b>bỏ</b> hai đặc
 * quyền vốn đi tắt qua mô hình đồng thuận.
 *
 * <h2>Vì sao Hội đồng Tộc biểu và Admin không được đặc quyền ở màn này</h2>
 * {@code PersonVisibility.allows()} cho {@code clanWide} và {@code self} thấy mọi nhóm trường bất
 * kể chủ thể chọn gì — đúng và cần thiết ở màn hồ sơ, nơi Hội đồng phải sửa được dữ liệu phả hệ.
 * Ở danh bạ thì nó hỏng theo hai cách, cả hai đều không sửa được bằng cách giải thích:
 * <ol>
 *   <li><b>Người chịu trách nhiệm cải thiện lại là người duy nhất không nhìn thấy vấn đề.</b> Danh
 *       bạ của quản trị viên luôn đầy 100%, danh bạ của mọi người thì thưa. Ai đi vận động dòng họ
 *       điền thông tin, nếu màn hình của chính họ nói rằng ai cũng đã điền rồi?</li>
 *   <li><b>"218 / 627" mất nghĩa.</b> Hai người đọc cùng một màn hình mà thấy hai con số khác hẳn
 *       nhau thì con số ấy không còn là thước đo của dòng họ nữa. Coverage chỉ có nghĩa khi nó nói
 *       về <i>mức độ tham gia</i>, không phải về <i>quyền của người đang xem</i>.</li>
 * </ol>
 *
 * <h2>Và chính chủ cũng không được thấy mình vì là mình</h2>
 * {@code self} bị bỏ cùng lý do: người vừa đóng hết năm công tắc mà vẫn thấy mình trong danh bạ sẽ
 * tin rằng cả họ cũng thấy mình. Sau khi bỏ, họ xuất hiện <b>khi và chỉ khi</b> bản đồng thuận của
 * họ cho phép — tức màn hình nói đúng sự thật về thứ người khác nhìn thấy.
 *
 * <p><b>Phạm vi chi/ngành thì giữ nguyên.</b> Mức {@code BRANCH} có nghĩa là "người cùng chi tôi
 * xem được"; một Trưởng chi của đúng chi ấy nằm trong vòng tròn đó theo đúng định nghĩa mà cả hệ
 * thống đang dùng. Cắt nốt phạm vi sẽ không phải là bỏ đặc quyền nữa mà là bịa ra một luật riêng
 * cho danh bạ.</p>
 *
 * <h2>Đây không phải bản chép lại luật riêng tư</h2>
 * Lớp này <b>không</b> quyết định trường nào hiện ra; nó chỉ trả lời "người xem là ai". Toàn bộ
 * luật vẫn nằm một chỗ duy nhất trong {@code PrivacyTierService}/{@code PersonVisibility}, và
 * danh bạ gọi thẳng vào đó với ngữ cảnh mà lớp này dựng.
 */
final class DirectoryAudience {

    private DirectoryAudience() {
    }

    /**
     * Hạ người gọi về mức "một thành viên bất kỳ cùng phạm vi chi/ngành".
     *
     * <p>Không bao giờ dựng được ngữ cảnh <b>rộng hơn</b> ngữ cảnh gốc: vai luôn là {@code MEMBER},
     * {@code personId} luôn {@code null}, còn hai trường phạm vi thì sao chép nguyên văn. Với Khách
     * thì hàm này không bao giờ được gọi tới — {@code DirectoryService} đã chặn từ trước bằng
     * {@code 401}.</p>
     */
    static CallerContext of(CallerContext caller) {
        return new CallerContext(CallerRole.MEMBER, null, caller.managedBranches(),
                caller.homeBranch());
    }
}
