<#import "template.ftl" as layout>
<#--
  ĐẶT MẬT KHẨU MỚI — design/06-dang-nhap §5.2 khung 2 và §6.1 bước 3.

  Đây là trang của nghi thức đặt lại NGOẠI TUYẾN: trưởng chi cấp một mật khẩu
  tạm qua điện thoại, Keycloak bắt đổi ngay ở lần đăng nhập ấy bằng hành động
  bắt buộc UPDATE_PASSWORD.

  Theo 06 §5.2 khung 2, màn này KHÔNG hỏi thêm gì: không "nhớ mật khẩu?", không
  câu hỏi bảo mật, không xin quyền thông báo. Mỗi câu hỏi thêm ở đây là một chỗ
  người dùng dừng lại giữa một việc họ chưa hiểu.
-->
<#global tieuDeTab = msg("updatePasswordTitle")>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>

  <#if section = "header">
    <h2 class="gp-tieu-de">${msg("updatePasswordTitle")}</h2>

  <#elseif section = "form">

    <#-- Lỗi cấp biểu mẫu. PHẢI in ở đây: macro `registrationLayout` được gọi với
         `displayMessage=!messagesPerField.existsError(...)`, nên khi CÓ lỗi
         trường thì khuôn KHÔNG in gì — và nếu trang cũng không in thì người dùng
         chỉ thấy hai ô viền đỏ mà không một chữ nào nói vì sao. Đúng lỗi ấy đã
         xảy ra ở bản đầu và chỉ lộ ra khi chụp màn hình thật. -->
    <#if messagesPerField.existsError('password','password-confirm')>
      <div class="gp-bao gp-bao--loi" role="alert">
        <p class="gp-bao-dau">${kcSanitize(messagesPerField.getFirstError('password','password-confirm'))?no_esc}</p>
      </div>
    </#if>

    <form id="gp-form-doi-mat-khau" action="${url.loginAction}" method="post" novalidate>

      <#--
        Tên đăng nhập, HIỆN RA chứ không giấu (06 §5.2 khung 2: "Số để đăng nhập
        từ nay", đã điền sẵn, không sửa được). Hai việc cùng lúc:
          · người dùng thấy mình đang đặt mật khẩu cho TÀI KHOẢN NÀO — với người
            dùng chung máy với con cháu thì đây không phải chi tiết thừa;
          · trình quản lý mật khẩu biết lưu mật khẩu mới vào đâu, nếu không nó
            lưu một mẩu mồ côi.
        `readonly` chứ không `disabled`: trường `disabled` KHÔNG được gửi lên.
      -->
      <#if (auth.attemptedUsername)??>
        <div class="gp-nhom">
          <label class="gp-nhan" for="username">${msg("giaphaAccountLabel")}</label>
          <input class="gp-o gp-o--khoa" type="text" id="username" name="username"
                 value="${auth.attemptedUsername}" autocomplete="username" readonly />
          <p class="gp-goi-y">${msg("giaphaAccountLocked")}</p>
        </div>
      </#if>

      <div class="gp-nhom">
        <label class="gp-nhan" for="password-new">${msg("passwordNew")}</label>
        <div class="gp-o-boc">
          <input class="gp-o" id="password-new" name="password-new" type="password"
                 autocomplete="new-password" autofocus
                 <#if messagesPerField.existsError('password','password-confirm')>aria-invalid="true"</#if>
                 aria-describedby="gp-goi-y-mat-khau" />
          <button class="gp-hien" type="button" hidden
                  data-hien-mat-khau aria-controls="password-new"
                  data-hien="${msg('giaphaShowPassword')}"
                  data-an="${msg('giaphaHidePassword')}">${msg("giaphaShowPassword")}</button>
        </div>
        <p class="gp-goi-y" id="gp-goi-y-mat-khau">${msg("giaphaPasswordHint")}</p>
      </div>

      <div class="gp-nhom">
        <label class="gp-nhan" for="password-confirm">${msg("passwordConfirm")}</label>
        <div class="gp-o-boc">
          <input class="gp-o" id="password-confirm" name="password-confirm" type="password"
                 autocomplete="new-password"
                 <#if messagesPerField.existsError('password-confirm')>aria-invalid="true"</#if> />
          <button class="gp-hien" type="button" hidden
                  data-hien-mat-khau aria-controls="password-confirm"
                  data-hien="${msg('giaphaShowPassword')}"
                  data-an="${msg('giaphaHidePassword')}">${msg("giaphaShowPassword")}</button>
        </div>
      </div>

      <#-- "Đăng xuất khỏi các máy khác" là một thao tác KHÓ THU HỒI (00 §2.4):
           nó cắt phiên của chính người dùng trên máy tính bảng ở nhà từ đường.
           Vì vậy nó có nhãn chữ đầy đủ, không phải một ô đánh dấu trơ trọi. -->
      <label class="gp-chon" for="logout-sessions">
        <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" />
        <span>${msg("logoutOtherSessions")}</span>
      </label>

      <button class="gp-nut gp-nut--chinh" name="login" type="submit">${msg("doSavePassword")}</button>

      <#if isAppInitiatedAction??>
        <button class="gp-nut gp-nut--nhat" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
      </#if>
    </form>
  </#if>

</@layout.registrationLayout>
