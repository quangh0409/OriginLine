<#--
  Bản của dòng họ, thay cho `base/login/register-commons.ftl`.

  Chỉ có MỘT macro, và nó chỉ in ra khi `registration-terms-and-conditions`
  được bật trong luồng đăng ký. Hôm nay hành động ấy DISABLED nên khối này cao
  đúng 0px — cùng kỷ luật với chỗ cắm Zalo: chừa thứ tự, không chừa khoảng trống.

  Vì sao vẫn dựng sẵn: "điều khoản" ở đây sẽ không phải một trang pháp lý mà là
  lời dòng họ nói với người mới về việc dữ liệu người đang sống được giữ thế nào
  (Nghị định 13/2023). Ngày Hội đồng chốt câu chữ ấy, bật một cờ là nó hiện ra
  đúng khuôn — chứ không rơi về HTML trần của `base`, vì theme này đặt
  `parent=base` và không thừa hưởng một điểm ảnh CSS nào.

  Tên macro là `dieuKhoan`, KHÔNG phải `termsAcceptance`: tệp này thay thế bản
  của `base` hoàn toàn, nên không có ai gọi tên cũ. Đổi tên ở đây thì phải đổi ở
  `register.ftl` — đó là lời gọi duy nhất.
-->
<#macro dieuKhoan>
  <#if termsAcceptanceRequired??>
    <div class="gp-bao gp-bao--nhac">
      <p class="gp-bao-dau">${msg("termsTitle")}</p>
      <div id="gp-dieu-khoan">${kcSanitize(msg("termsText"))?no_esc}</div>
    </div>

    <div class="gp-nhom">
      <label class="gp-chon" for="termsAccepted">
        <input type="checkbox" id="termsAccepted" name="termsAccepted"
               <#if messagesPerField.existsError('termsAccepted')>aria-invalid="true"</#if> />
        <span>${msg("acceptTerms")}</span>
      </label>
      <#if messagesPerField.existsError('termsAccepted')>
        <p class="gp-loi-o" id="gp-loi-termsAccepted" aria-live="polite">${kcSanitize(messagesPerField.get('termsAccepted'))?no_esc}</p>
      </#if>
    </div>
  </#if>
</#macro>
