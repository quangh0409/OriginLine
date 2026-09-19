<#import "template.ftl" as layout>
<#--
  TRANG ĐÃ HẾT HẠN — phần mà Keycloak sở hữu của trạng thái lỗi số 5 (06 §7).

  Phân biệt cho rõ: đây KHÔNG phải ca "phiên hết hạn giữa chừng" mà §7.3 bàn.
  Ca ấy xảy ra BÊN TRONG ứng dụng Next.js và phải xử lý TẠI CHỖ bằng một hộp
  thoại giữ nguyên trang đang đọc — đá người dùng về đây là làm mất việc đang
  làm. Trang này chỉ nổ khi bản thân LƯỢT ĐĂNG NHẬP đang dở quá hạn: mở tab đăng
  nhập rồi bỏ đó, bấm lùi sau khi đã đăng nhập xong, mở lại một tab cũ.

  Bản gốc của Keycloak in hai câu cụt kèm hai liên kết cùng tên "Nhấp vào đây" —
  hai liên kết giống hệt nhau dẫn tới hai chỗ khác nhau là một câu đố. Ở đây
  chúng là hai nút, mỗi nút nói việc của nó.
-->
<#global tieuDeTab = msg("pageExpiredTitle")>
<@layout.registrationLayout; section>

  <#if section = "header">
    <h2 class="gp-tieu-de">${msg("pageExpiredTitle")}</h2>

  <#elseif section = "form">
    <p class="gp-dan">${msg("giaphaPageExpiredBody")}</p>

    <a class="gp-nut gp-nut--chinh" id="loginContinueLink" href="${url.loginAction}">${msg("giaphaPageExpiredContinue")}</a>
    <a class="gp-nut gp-nut--phu" id="loginRestartLink" href="${url.loginRestartFlowUrl}">${msg("giaphaPageExpiredRestart")}</a>
  </#if>

</@layout.registrationLayout>
