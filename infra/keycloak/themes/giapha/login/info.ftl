<#import "template.ftl" as layout>
<#--
  MÀN BÁO TIN — "đã gửi hướng dẫn", "đã đăng xuất", "cần làm thêm việc này".

  06 §7.2 luật 4: đây KHÔNG phải sự cố, nên không dùng màu đỏ lỗi. Nền giấy cũ.
  `role="status"` chứ không `role="alert"`: tin báo không được cắt ngang lời
  trình đọc màn hình đang đọc dở.
-->
<@layout.registrationLayout displayMessage=false; section>

  <#if section = "header">
    <h2 class="gp-tieu-de"><#if messageHeader??>${kcSanitize(msg("${messageHeader}"))?no_esc}<#else>${kcSanitize(message.summary)?no_esc}</#if></h2>

  <#elseif section = "form">
    <div class="gp-bao" role="status">
      <p>${kcSanitize(message.summary)?no_esc}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></b></#list></#if></p>
    </div>

    <#if pageRedirectUri?has_content>
      <a class="gp-nut gp-nut--chinh" href="${pageRedirectUri}">${msg("giaphaBackToApp")}</a>
    <#elseif actionUri?has_content>
      <a class="gp-nut gp-nut--chinh" href="${actionUri}">${msg("proceedWithAction")}</a>
    <#elseif (client.baseUrl)?has_content>
      <a class="gp-nut gp-nut--chinh" href="${client.baseUrl}">${msg("giaphaBackToApp")}</a>
    </#if>
  </#if>

</@layout.registrationLayout>
