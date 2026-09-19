<#import "template.ftl" as layout>
<#--
  QUÊN MẬT KHẨU — design/06-dang-nhap §6, Hình 7 khung 1.

  Trang này KHÔNG VỚI TỚI ĐƯỢC hôm nay: realm đặt `resetPasswordAllowed: false`
  vì chưa có `smtpServer`, nên Keycloak không in liên kết dẫn tới đây và cũng
  không phục vụ tuyến này. Dựng sẵn là cố ý — ngày bật SMTP xong và lật cờ lên,
  trang hiện ra đúng theme chứ không rơi về khuôn HTML trần của `base`.
  README.md mục Keycloak ghi đủ điều kiện bật lại.
-->
<#global tieuDeTab = msg("emailForgotTitle")>
<@layout.registrationLayout displayInfo=false displayMessage=!messagesPerField.existsError('username'); section>

  <#if section = "header">
    <h2 class="gp-tieu-de">${msg("emailForgotTitle")}</h2>

  <#elseif section = "form">
    <form id="gp-form-quen-mat-khau" action="${url.loginAction}" method="post" novalidate>
      <div class="gp-nhom">
        <label class="gp-nhan" for="username">${msg("usernameOrEmail")}</label>
        <input class="gp-o" id="username" name="username" type="text"
               value="${(auth.attemptedUsername!'')}"
               autocomplete="username"
               autocapitalize="off" autocorrect="off" spellcheck="false"
               dir="ltr" autofocus
               <#if messagesPerField.existsError('username')>aria-invalid="true"</#if>
               aria-describedby="gp-goi-y-dinh-danh" />
        <p class="gp-goi-y" id="gp-goi-y-dinh-danh">${msg("emailInstruction")}</p>
      </div>

      <button class="gp-nut gp-nut--chinh" type="submit">${msg("doSendResetInstructions")}</button>
    </form>

    <a class="gp-nut gp-nut--nhat" href="${url.loginUrl}">${msg("backToLogin")}</a>
  </#if>

</@layout.registrationLayout>
