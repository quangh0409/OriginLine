<#import "template.ftl" as layout>
<#import "register-commons.ftl" as registerCommons>

<#--
  ══════════════════════════════════════════════════════════════════════════
  MÀN ĐĂNG KÝ CÓ KIỂM MÃ MỜI DÒNG HỌ
  design/07-checklist §1.3 ("Bật tự đăng ký, có kiểm mã") · 06-dang-nhap §5
  ══════════════════════════════════════════════════════════════════════════

  ĐIỂM MẤU CHỐT — MÃ ĐƯỢC KIỂM TRƯỚC KHI TÀI KHOẢN RA ĐỜI.

  Ô "Mã mời dòng họ" KHÔNG phải một ô do trang này nghĩ ra. Nó là một thuộc
  tính User Profile của realm (`maMoiDongHo`, xem
  `infra/keycloak/user-profile-giapha.json`), và điều đó quyết định TẤT CẢ:

    · `registration-user-creation` chạy `validate()` cho toàn bộ thuộc tính
      TRƯỚC, rồi mới chạy `success()` — mà `success()` mới là chỗ tạo người
      dùng. Một mã sai dừng ở `validate()`, nên realm KHÔNG có thêm tài khoản
      nào. Đã đo: gửi biểu mẫu với mã sai, số tài khoản giữ nguyên 6/6.
    · Mã hợp lệ được LƯU LẠI trên chính tài khoản vừa tạo, nên Hội đồng đếm
      được "mã này đã dùng bao nhiêu lượt" và tra được "ai đã dùng mã nào"
      (checklist §1.2, hai chốt chặn) mà không cần bảng nào mới.

  GIỚI HẠN, NÓI THẲNG: Keycloak chỉ so được mã với một MẪU (regex). Nó không
  hỏi được máy chủ, nên không biết mã đã hết hạn hay đã bị thu hồi chưa. Bốn
  chốt chặn của checklist §1.2 sống ở máy chủ, không ở đây. README mục
  "Đăng ký có kiểm mã mời" ghi rõ ranh giới ấy và cái giá của từng đường đi
  tiếp — đừng đọc màn này như thể nó đã giải xong bài toán.

  ── THỨ TỰ CÁC Ô, và vì sao ────────────────────────────────────────────────
    1. Mã mời dòng họ   — cái cổng. Đứng đầu vì người không có mã thì không
                          nên gõ hết năm ô rồi mới biết mình không vào được.
    2. Họ · 3. Tên      — thứ tự TIẾNG VIỆT: họ trước, tên sau. Keycloak gọi
                          chúng là `lastName`/`firstName`; nhãn và thứ tự trên
                          màn là của ta, khai trong user-profile-giapha.json.
    4. Email            — bắt buộc, vì đây là đường DUY NHẤT để tự đặt lại mật
                          khẩu. Người tự đăng ký không có Trưởng chi nào đang
                          chờ để đặt lại hộ (checklist §6).
    5. Số điện thoại    — `username`, tức thứ gõ vào ô đăng nhập sau này.
    6. Mật khẩu ×2      — CHỈ hiện khi Keycloak nói `passwordRequired`. Xem
                          khối dưới đây; hôm nay nó KHÔNG hiện, và đó là cố ý.

  ── VÌ SAO KHÔNG CÓ Ô MẬT KHẨU TRÊN MÀN NÀY ────────────────────────────────

  Realm bật `verifyEmail`. Khi ấy `RegistrationPassword` của Keycloak 26.7 CỐ Ý
  không đặt `passwordRequired`, và javadoc của chính nó giải thích: với
  `verifyEmail` bật, mật khẩu KHÔNG đặt trên biểu mẫu đăng ký mà đặt ở bước sau,
  sau khi địa chỉ thư đã được xác minh — "this is recommended for security
  reasons". Cờ `always_set_password_on_register_form` ép nó quay lại kiểu cũ,
  và Keycloak ghi thẳng rằng cờ ấy ĐÃ LỖI THỜI và có thể bị gỡ.

  Ta giữ hành vi mặc định, vì với sản phẩm này nó tốt hơn thật:
    · tài khoản vừa tạo KHÔNG dùng được cho tới khi có người mở đúng hộp thư ấy
      — tức một mã mời rò ra cũng không tự nó thành một tài khoản sống;
    · màn đăng ký ngắn đi hai ô, mà đây là màn người 70 tuổi gõ một ngón;
    · đúng nhịp mà 06 §5.2 đã vẽ cho luồng mời: xác nhận trước, đặt mật khẩu sau.

  Khối mật khẩu dưới đây VẪN dựng sẵn và bọc `<#if passwordRequired??>`: ngày
  một dòng họ tắt `verifyEmail` (không có máy chủ thư chẳng hạn), hai ô hiện ra
  đúng chỗ đã tính, chứ không rơi về khuôn HTML trần của `base`.

  Thứ tự 1–5 đến từ THỨ TỰ TRONG `user-profile-giapha.json`, không từ tệp này.
  Đổi thứ tự thì sửa ở đó rồi chạy `dong-goi-user-profile.mjs` — sửa ở đây là
  tạo ra một nguồn sự thật thứ hai.
-->

<#global tieuDeTab = msg("registerTitle")>

<@layout.registrationLayout displayMessage=messagesPerField.exists('global'); section>

  <#if section = "header">
    <h2 class="gp-tieu-de">${msg("registerTitle")}</h2>
    <p class="gp-dan">${msg("giaphaRegisterIntro")}</p>

  <#elseif section = "form">

    <#--
      Lỗi cấp trường, gom lên đầu thẻ.

      Macro khuôn được gọi với `displayMessage=messagesPerField.exists('global')`
      — tức khi lỗi nằm ở MỘT TRƯỜNG chứ không phải toàn cục thì khuôn không in
      gì. Trên một biểu mẫu sáu ô, để người dùng tự đi tìm ô nào viền đỏ là bắt
      họ làm việc của phần mềm. Cùng lỗi ấy từng xảy ra ở
      `login-update-password.ftl` và chỉ lộ ra khi chụp màn hình thật.
    -->
    <#if messagesPerField.existsError('maMoiDongHo')>
      <div class="gp-bao gp-bao--loi" role="alert">
        <p class="gp-bao-dau">${kcSanitize(messagesPerField.get('maMoiDongHo'))?no_esc}</p>
        <p>${msg("giaphaInviteCodeHint")}</p>
      </div>
    </#if>

    <form id="gp-form-dang-ky" action="${url.registrationAction}" method="post" novalidate>

      <#list profile.attributes as thuocTinh>

        <#if thuocTinh.name == 'locale'>
          <#-- Ngôn ngữ đi theo biểu mẫu dưới dạng trường ẩn: người vừa bấm
               "English" ở thanh đầu trang phải nhận thư và màn kế tiếp bằng
               tiếng Anh, chứ không bị ném về bản tiếng Việt. -->
          <#if realm.internationalizationEnabled && locale.currentLanguageTag?has_content>
            <input type="hidden" id="locale" name="locale" value="${locale.currentLanguageTag}" />
          </#if>
        <#else>

          <#assign laO = thuocTinh.name == 'username'>
          <div class="gp-nhom<#if laO> gp-nhom--cuoi</#if>">
            <label class="gp-nhan" for="${thuocTinh.name}">
              ${advancedMsg(thuocTinh.displayName!'')}<#if !thuocTinh.required><span class="gp-tuy-chon"> ${msg("giaphaOptional")}</span></#if>
            </label>

            <#--
              `type="text"` cho mọi ô, kể cả email và điện thoại.

              `type="email"` bật bộ kiểm của trình duyệt, và câu lỗi ấy là câu
              của TRÌNH DUYỆT — tiếng máy, không dịch được, không đi qua bộ
              thông điệp của dòng họ. Keycloak đã kiểm email ở máy chủ rồi
              (validator `email`), nên thêm một lớp kiểm thứ hai chỉ để nó nói
              một thứ tiếng khác là lỗ.

              `type="tel"` thì khoá bàn phím số — 06 §9.1 nêu đúng lý do không
              làm thế ở ô định danh, và ở đây ô số điện thoại cũng phải gõ được
              mã định danh giấy (`giapha.giap.017`) cho cụ không có số riêng.
            -->
            <input class="gp-o" id="${thuocTinh.name}" name="${thuocTinh.name}"
                   type="text"
                   value="${(thuocTinh.value!'')}"
                   <#if thuocTinh.autocomplete??>autocomplete="${thuocTinh.autocomplete}"</#if>
                   <#if thuocTinh.name == 'maMoiDongHo' || thuocTinh.name == 'email' || thuocTinh.name == 'username'>
                     autocapitalize="off" autocorrect="off" spellcheck="false" dir="ltr"
                   </#if>
                   <#if thuocTinh.annotations.inputTypeMaxlength??>maxlength="${thuocTinh.annotations.inputTypeMaxlength}"</#if>
                   <#if messagesPerField.existsError('${thuocTinh.name}')>aria-invalid="true"</#if>
                   <#if thuocTinh.annotations.inputHelperTextAfter??>aria-describedby="gp-goi-y-${thuocTinh.name}"</#if>
                   <#-- Tiêu điểm vào ô MÃ MỜI, không vào "ô đầu danh sách".
                        Keycloak chèn thêm `locale` vào `profile.attributes` khi
                        realm bật đa ngôn ngữ, và nó có thể đứng ở bất kỳ đâu
                        trong danh sách — bám theo chỉ số là bám vào một thứ ta
                        không kiểm soát. -->
                   <#if thuocTinh.name == 'maMoiDongHo'>autofocus</#if> />

            <#if messagesPerField.existsError('${thuocTinh.name}')>
              <p class="gp-loi-o" id="gp-loi-${thuocTinh.name}" aria-live="polite">${kcSanitize(messagesPerField.get('${thuocTinh.name}'))?no_esc}</p>
            </#if>

            <#if thuocTinh.annotations.inputHelperTextAfter??>
              <p class="gp-goi-y" id="gp-goi-y-${thuocTinh.name}">${kcSanitize(advancedMsg(thuocTinh.annotations.inputHelperTextAfter))?no_esc}</p>
            </#if>
          </div>

          <#-- ── Mật khẩu, chèn ngay sau ô định danh ───────────────────────── -->
          <#if passwordRequired?? && laO>
            <div class="gp-nhom">
              <label class="gp-nhan" for="password">${msg("password")}</label>
              <div class="gp-o-boc">
                <input class="gp-o" id="password" name="password" type="password"
                       autocomplete="new-password"
                       aria-describedby="gp-goi-y-mat-khau"
                       <#if messagesPerField.existsError('password','password-confirm')>aria-invalid="true"</#if> />
                <button class="gp-hien" type="button" hidden
                        data-hien-mat-khau aria-controls="password"
                        data-hien="${msg('giaphaShowPassword')}"
                        data-an="${msg('giaphaHidePassword')}">${msg("giaphaShowPassword")}</button>
              </div>
              <#if messagesPerField.existsError('password')>
                <p class="gp-loi-o" id="gp-loi-password" aria-live="polite">${kcSanitize(messagesPerField.get('password'))?no_esc}</p>
              </#if>
              <p class="gp-goi-y" id="gp-goi-y-mat-khau">${msg("giaphaPasswordHint")}</p>
            </div>

            <div class="gp-nhom gp-nhom--cuoi">
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
              <#if messagesPerField.existsError('password-confirm')>
                <p class="gp-loi-o" id="gp-loi-password-confirm" aria-live="polite">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</p>
              </#if>
            </div>
          </#if>

        </#if>
      </#list>

      <@registerCommons.dieuKhoan/>

      <#--
        CHỖ CẮM reCAPTCHA — cùng kỷ luật với chỗ cắm Zalo ở `login.ftl`: chừa
        THỨ TỰ, không chừa KHOẢNG TRỐNG. `registration-recaptcha-action` đang
        DISABLED trong luồng đăng ký, nên khối này cao đúng 0px.

        Đây là chốt chặn "giới hạn tần suất" của checklist §1.2 mà Keycloak làm
        được sẵn — chống dò mã bằng máy. Cái giá: phải có khoá Google và mỗi
        lượt đăng ký gọi ra một máy chủ của bên thứ ba. Hội đồng quyết, README
        ghi cách bật.
      -->
      <#if recaptchaRequired?? && (recaptchaVisible!false)>
        <div class="gp-nhom">
          <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}" data-action="${recaptchaAction}"></div>
        </div>
      </#if>

      <#if recaptchaRequired?? && !(recaptchaVisible!false)>
        <script>
          function onSubmitRecaptcha(token) {
            document.getElementById("gp-form-dang-ky").requestSubmit();
          }
        </script>
        <button class="gp-nut gp-nut--chinh g-recaptcha" id="gp-nut-dang-ky" type="submit"
                data-sitekey="${recaptchaSiteKey}" data-callback="onSubmitRecaptcha" data-action="${recaptchaAction}"
                data-dang-gui="${msg('giaphaRegistering')}">${msg("doRegister")}</button>
      <#else>
        <button class="gp-nut gp-nut--chinh" id="gp-nut-dang-ky" name="register" type="submit"
                data-dang-gui="${msg('giaphaRegistering')}">${msg("doRegister")}</button>
      </#if>
    </form>

    <a class="gp-nut gp-nut--nhat" href="${url.loginUrl}">${msg("backToLogin")}</a>

  <#elseif section = "duoi-the">

    <#-- Nói TRƯỚC điều sẽ xảy ra SAU khi bấm nút, chứ không để người dùng phát
         hiện ra sau. Đăng ký xong là xem được phả đồ ngay (checklist §1.1 bước
         3, đã chốt) nhưng CHƯA được gắn vào một nhân khẩu nào — và "chưa gắn"
         mới là thứ quyết định họ sửa được gì. Giấu nhịp ấy đi thì người dùng
         tưởng mình đã xong, rồi tự hỏi vì sao không sửa được hồ sơ của chính
         mình. 00 §2.4: không để người dùng ở trong im lặng. -->
    <section class="gp-khach" aria-labelledby="gp-sau-dang-ky-dau">
      <h2 class="gp-khach-dau" id="gp-sau-dang-ky-dau">${msg("giaphaAfterRegisterTitle")}</h2>
      <ol class="gp-buoc">
        <li>${msg("giaphaAfterRegister1")}</li>
        <li>${msg("giaphaAfterRegister2")}</li>
        <li>${msg("giaphaAfterRegister3")}</li>
      </ol>
      <p class="gp-khach-luat">${msg("giaphaAfterRegisterPrivacy")}</p>
    </section>

  </#if>

</@layout.registrationLayout>
