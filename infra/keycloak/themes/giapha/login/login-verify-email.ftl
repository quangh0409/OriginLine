<#import "template.ftl" as layout>
<#--
  XÁC MINH ĐỊA CHỈ THƯ.

  Màn này chỉ tồn tại được từ khi realm có `smtpServer` thật. Trước đó
  `verifyEmail` phải là false, vì bật lên mà không có máy chủ thư nghĩa là mọi
  người tự đăng ký đều kẹt vĩnh viễn ở đúng trang này — tài khoản tạo xong,
  không ai vào được, và không có lá thư nào để bấm.

  Với luồng TỰ ĐĂNG KÝ thì xác minh thư không phải thủ tục thừa: nó là cách duy
  nhất biết rằng địa chỉ người ta khai là địa chỉ người ta đọc được. Mà địa chỉ
  ấy lại chính là đường "Quên mật khẩu" sau này — người tự đăng ký không có
  Trưởng chi nào đang chờ để đặt lại mật khẩu hộ họ (checklist §6).

  KHÔNG in nút "gửi lại" ở đây trừ khi Keycloak đưa biểu mẫu tới (nhánh
  `isAppInitiatedAction`). Ở nhánh còn lại, cách gửi lại là liên kết trong phần
  `duoi-the` — và nó phải nói rõ hệ quả: thư cũ hết hiệu lực.
-->
<#global tieuDeTab = msg("emailVerifyTitle")>
<@layout.registrationLayout displayInfo=false; section>

  <#if section = "header">
    <h2 class="gp-tieu-de">${msg("emailVerifyTitle")}</h2>

  <#elseif section = "form">
    <p class="gp-dan">
      <#if verifyEmail??>
        ${msg("emailVerifyInstruction1", verifyEmail)}
      <#else>
        ${msg("emailVerifyInstruction4", user.email)}
      </#if>
    </p>

    <div class="gp-bao gp-bao--nhac">
      <p>${msg("giaphaVerifyEmailSpam")}</p>
    </div>

    <#if isAppInitiatedAction??>
      <form id="gp-form-xac-minh" action="${url.loginAction}" method="post">
        <button class="gp-nut gp-nut--chinh" type="submit">
          <#if verifyEmail??>${msg("emailVerifyResend")}<#else>${msg("emailVerifySend")}</#if>
        </button>
        <button class="gp-nut gp-nut--nhat" type="submit" name="cancel-aia" value="true" formnovalidate>${msg("doCancel")}</button>
      </form>
    <#else>
      <p style="margin:0">
        <a class="gp-lien-ket" href="${url.loginAction}">${msg("giaphaVerifyEmailResendLink")}</a>
      </p>
    </#if>
  </#if>

</@layout.registrationLayout>
