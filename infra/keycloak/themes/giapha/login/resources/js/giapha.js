/* ═══════════════════════════════════════════════════════════════════════════
   Hai việc, và đúng hai việc. Trang này là màn hình đầu tiên người trong họ
   nhìn thấy; mỗi kilobyte JavaScript ở đây là một kilobyte trên mạng ở quê.

     1. Bật nút "Hiện / Ẩn" mật khẩu.
     2. Đổi nhãn nút chính khi biểu mẫu đang gửi, KHÔNG đổi bề rộng nút.

   Không thư viện, không theo dõi, không tự động điền gì.

   LUẬT: trang phải dùng được ĐẦY ĐỦ khi JavaScript không chạy. Nút "Hiện" in ra
   với thuộc tính `hidden` và chỉ tệp này gỡ nó — nên không-JS nghĩa là không có
   nút, chứ không phải có một nút bấm không ăn gì.
   ═══════════════════════════════════════════════════════════════════════════ */
(function () {
  "use strict";

  // ── 1. Hiện / Ẩn mật khẩu ────────────────────────────────────────────────
  // Tìm theo thuộc tính chứ không theo id: trang "Đặt mật khẩu mới" có HAI ô
  // mật khẩu, và mỗi ô có nút riêng. `aria-controls` nói nút nào điều khiển ô
  // nào — cùng một dữ kiện phục vụ cả trình đọc màn hình lẫn đoạn mã này.
  var danhSachNut = document.querySelectorAll("[data-hien-mat-khau]");

  Array.prototype.forEach.call(danhSachNut, function (nut) {
    var o = document.getElementById(nut.getAttribute("aria-controls"));
    if (!o) return;

    nut.hidden = false;
    nut.setAttribute("aria-pressed", "false");

    var nhanHien = nut.getAttribute("data-hien") || "Hiện";
    var nhanAn = nut.getAttribute("data-an") || "Ẩn";

    nut.addEventListener("click", function () {
      var dangAn = o.type === "password";
      o.type = dangAn ? "text" : "password";
      // Nhãn CHỮ đổi theo trạng thái — không phải một biểu tượng con mắt đổi
      // hình (00 §2.3). `aria-pressed` cho trình đọc màn hình biết đây là công
      // tắc hai trạng thái chứ không phải một nút gây hành động.
      nut.textContent = dangAn ? nhanAn : nhanHien;
      nut.setAttribute("aria-pressed", dangAn ? "true" : "false");
      // Con trỏ ở lại trong ô: người vừa bấm "Hiện" là để đọc tiếp chỗ đang gõ
      // dở, không phải để bắt đầu lại.
      o.focus();
    });
  });

  // ── 2. Nhãn nút chính khi đang gửi ───────────────────────────────────────
  var form = document.getElementById("gp-form-dang-nhap");
  var nutGui = document.getElementById("gp-nut-dang-nhap");

  if (form && nutGui) {
    form.addEventListener("submit", function () {
      var nhanDangGui = nutGui.getAttribute("data-dang-gui");
      if (nhanDangGui) {
        // Khoá chiều cao và bề rộng TRƯỚC khi đổi chữ: danh mục kiểm 06 §11 #8
        // đòi nút giữ nguyên bề rộng. Nút đã `width:100%` nên bề rộng an toàn;
        // khoá chiều cao để nhãn dài hơn không làm nút cao thêm một dòng và đẩy
        // cả phần dưới nhảy xuống.
        nutGui.style.minHeight = nutGui.offsetHeight + "px";
        nutGui.textContent = nhanDangGui;
      }
      // KHÔNG `disabled`: một nút bị vô hiệu ngay lúc gửi sẽ không gửi được gì
      // nếu trình duyệt bỏ qua sự kiện, và nó cũng cướp tiêu điểm bàn phím.
      // `aria-disabled` nói đúng điều cần nói mà không đụng vào hành vi.
      nutGui.setAttribute("aria-disabled", "true");
    });
  }
})();
