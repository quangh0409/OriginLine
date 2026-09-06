/**
 * <b>Shared kernel</b> — value object, kiểu nền tảng của domain, lỗi và tiện ích bảo mật dùng chung
 * cho mọi bounded context.
 *
 * <p>Được khai báo là <i>shared module</i> trong {@code vn.giapha.GiaPhaApplication}: mọi context
 * được phép phụ thuộc vào đây, còn shared <b>không được</b> phụ thuộc ngược vào bất kỳ context nào.
 * Giữ package này nhỏ — thứ gì chỉ một context dùng thì thuộc về context đó.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared kernel")
package vn.giapha.shared;
