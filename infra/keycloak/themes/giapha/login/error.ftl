<#import "template.ftl" as layout>
<#--
  NGÕ CỤT CHUNG của Keycloak.

  06 §7.2 luật 5: "Mọi màn lỗi có ít nhất một nút đi tiếp được. Một màn lỗi
  không có lối ra là một cái ngõ cụt có màu." Nên trang này luôn có nút quay về
  ứng dụng, và khối "gọi một người thật" ở cuối trang do template.ftl in ra cho
  MỌI trang, không riêng trang này.

  Mã theo dõi (`traceId`) giấu sau <details>: nó vô dụng với người dùng (luật 1:
  không in mã lỗi) nhưng có ích khi họ gọi điện đọc cho trưởng chi nghe.
-->
<#global tieuDeTab = msg("errorTitle")>
<@layout.registrationLayout displayMessage=false; section>

  <#if section = "header">
    <h2 class="gp-tieu-de">${kcSanitize(msg("errorTitle"))?no_esc}</h2>

  <#elseif section = "form">
    <div class="gp-bao gp-bao--loi" role="alert">
      <p class="gp-bao-dau">${kcSanitize(message.summary)?no_esc}</p>
    </div>

    <#if client?? && client.baseUrl?has_content>
      <a class="gp-nut gp-nut--chinh" id="backToApplication" href="${client.baseUrl}">${msg("giaphaBackToApp")}</a>
    </#if>

    <#if traceId??>
      <details style="margin-top:16px">
        <summary class="gp-lien-ket">${msg("giaphaTechnicalDetails")}</summary>
        <p class="gp-goi-y" id="traceId">${msg("traceIdSupportMessage", traceId)}</p>
      </details>
    </#if>
  </#if>

</@layout.registrationLayout>
