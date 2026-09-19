<#import "template.ftl" as layout>

<#--
  CHỖ CẮM ZALO, viết thành macro chứ không thành `section`.

  Lý do kỹ thuật, đáng ghi lại vì nó tốn thời gian của người sau: `<#nested>`
  chỉ dùng được BÊN TRONG định nghĩa macro. Thân của lời gọi
  `<@layout.registrationLayout>` không phải là một macro, nên gọi
  `<#nested "socialProviders">` ở đó là lỗi biên dịch FreeMarker, không phải một
  khe cắm.
-->
<#macro choCamZalo>
  <#if realm.password && social?? && social.providers?has_content>
    <div class="gp-mang-xa-hoi">
      <ul>
        <#list social.providers as p>
          <li>
            <#-- Nhãn CHỮ bắt buộc; biểu tượng (nếu có) chỉ đi kèm, không thay. -->
            <a class="gp-nut gp-nut--phu" id="social-${p.alias}" href="${p.loginUrl}">${p.displayName!p.alias}</a>
          </li>
        </#list>
      </ul>
    </div>
  </#if>
</#macro>

<#--
  MÀN ĐĂNG NHẬP — phương án A của design/06-dang-nhap §4.1.

  Thứ tự trên màn hình, và không được đổi:
    1. bảng tên dòng họ            (template.ftl dựng)
    2. hộp lỗi, nếu có             (06 §7, trạng thái 1 và 2)
    3. ô "Số điện thoại hoặc email"
    4. ô mật khẩu + nút "Hiện"
    5. "Quên mật khẩu?" — CHỈ khi realm bật cờ (xem README)
    6. ghi nhớ đăng nhập
    7. nút "Đăng nhập"
    8. vạch "hoặc"
    9. CHỖ CẮM ZALO — `socialProviders`, hôm nay rỗng nên không in gì
   10. nút "Xem phần công khai của dòng họ"
   11. "Tôi có mã mời" — chỉ khi giaphaInviteUrl được cấu hình

  Vì sao Zalo ở nấc 9 chứ không nấc 3: 06 §4.5 luật 3 — đặt nó trên ô mật khẩu
  là ngụ ý dòng họ tiến cử một nền tảng bên thứ ba làm lối vào chính, trong khi
  hợp đồng OA còn chưa ký. Và luật 1: chừa THỨ TỰ, không chừa KHOẢNG TRỐNG — khe
  ấy hôm nay cao đúng 0px.
-->
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password'); section>

  <#if section = "header">
    <#-- Trang này KHÔNG in tiêu đề màn: bảng tên dòng họ đã trả lời câu "đây là
         phả của ai", và một dòng "Đăng nhập vào tài khoản của bạn" nữa chỉ ăn
         mất ngân sách chiều cao mà §9 đã đếm từng chục pixel. -->

  <#elseif section = "form">

    <#-- ── Lỗi cấp biểu mẫu ─────────────────────────────────────────────────
         Keycloak trả CÙNG MỘT câu cho "sai mật khẩu" và cho "bị khoá tạm vì sai
         quá nhiều lần" (`accountTemporarilyDisabledMessage` = `invalidUserMessage`
         trong bộ thông điệp gốc). Đó là cố ý và ta GIỮ NGUYÊN: câu khác nhau
         biến trang đăng nhập thành công cụ dò xem số nào là người trong họ —
         06 §7.2 luật 6 cấm đúng điều đó.

         Cái mất là người bị khoá không hiểu vì sao "gõ đúng" mà vẫn không vào
         được. Bù lại bằng khối hổ phách bên dưới: nó nói luật khoá tạm cho MỌI
         ca lỗi, nên không tiết lộ ca nào đang xảy ra. Khung dây 06 Hình 8 vẽ
         đúng khối này cạnh trạng thái 1.
    -->
    <#if messagesPerField.existsError('username','password')>
      <div class="gp-bao gp-bao--loi" role="alert">
        <p class="gp-bao-dau">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</p>
        <p>${msg("giaphaLoginErrorHint")}</p>
      </div>
      <div class="gp-bao gp-bao--nhac">
        <p>${msg("giaphaLockoutNote")}</p>
      </div>
    </#if>

    <#if realm.password>
      <form id="gp-form-dang-nhap" action="${url.loginAction}" method="post" novalidate>

        <#if !usernameHidden??>
          <div class="gp-nhom">
            <label class="gp-nhan" for="username">${msg("usernameOrEmail")}</label>
            <#--
              MỘT ô nhận CẢ số điện thoại LẪN email (quyết định đã chốt, và
              06 §2.1 phương án B). Realm giữ `loginWithEmailAllowed: true` nên
              Keycloak tự thử khớp cả `username` lẫn `email`.

              `type="text"` chứ không `type="tel"`: khoá bàn phím số là khoá luôn
              người dùng email (06 §9.1). `autocapitalize`/`autocorrect` tắt vì
              điện thoại tự viết hoa chữ đầu email mà người dùng không nhìn thấy
              điều đó xảy ra.

              `value="${(login.username!'')}"`: khi sai mật khẩu, ô này GIỮ
              NGUYÊN thứ vừa gõ. Bắt người gõ một ngón nhập lại số điện thoại là
              một lý do bỏ cuộc có thật (06 Hình 8, khác biệt số 1).
            -->
            <input class="gp-o" id="username" name="username" type="text"
                   value="${(login.username!'')}"
                   autocomplete="username"
                   autocapitalize="off" autocorrect="off" spellcheck="false"
                   dir="ltr"
                   <#if messagesPerField.existsError('username','password')>aria-invalid="true"</#if>
                   aria-describedby="gp-goi-y-dinh-danh"
                   <#if !(login.username?has_content)>autofocus</#if> />
            <p class="gp-goi-y" id="gp-goi-y-dinh-danh">${msg("giaphaIdentifierHint")}</p>
          </div>
        </#if>

        <div class="gp-nhom gp-nhom--cuoi">
          <label class="gp-nhan" for="password">${msg("password")}</label>
          <div class="gp-o-boc">
            <input class="gp-o" id="password" name="password" type="password"
                   autocomplete="current-password"
                   <#if messagesPerField.existsError('username','password')>aria-invalid="true"</#if>
                   <#if login.username?has_content>autofocus</#if> />
            <#--
              Nút "Hiện" 68×44, CÓ CHỮ (06 §9.1). Con mắt gạch chéo là một câu đố
              (00 §2.3). Nút in ra với `hidden`; `giapha.js` gỡ `hidden` ra. Nếu
              JavaScript không chạy thì nút không bao giờ xuất hiện — chứ không
              phải xuất hiện rồi bấm không ăn gì.
            -->
            <button class="gp-hien" type="button" hidden
                    data-hien-mat-khau
                    aria-controls="password"
                    data-hien="${msg('giaphaShowPassword')}"
                    data-an="${msg('giaphaHidePassword')}">${msg("giaphaShowPassword")}</button>
          </div>
        </div>

        <#--
          "Quên mật khẩu?" bị ẩn vì `resetPasswordAllowed` đang là FALSE trong
          realm — và cờ ấy tắt là có lý do: realm KHÔNG có `smtpServer`, nên bật
          cờ lên thì Keycloak in liên kết, người dùng bấm, nhập email, nhận màn
          "đã gửi", rồi ngồi đợi một lá thư không tồn tại. 06 §1.2 #1 gọi đó là
          "tệ hơn là không có liên kết ấy".

          Điều kiện bật lại: cấu hình SMTP xong và gửi thử thành công. README.md
          mục Keycloak ghi đủ các bước.

          Khối này CÓ ĐIỀU KIỆN chứ không bị xoá: bật cờ trong realm là liên kết
          hiện lại đúng chỗ §9 tính toán (GIỮA ô mật khẩu và nút, không nằm dưới
          nút, để đáy nút chính vẫn cách đáy ô mật khẩu ≤ 120px khi bàn phím mở).
        -->
        <#if realm.resetPasswordAllowed>
          <p style="margin:0 0 8px">
            <a class="gp-lien-ket" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
          </p>
        </#if>

        <#if realm.rememberMe && !usernameHidden??>
          <label class="gp-chon" for="rememberMe">
            <input id="rememberMe" name="rememberMe" type="checkbox"
                   <#if login.rememberMe??>checked</#if> />
            <span>${msg("rememberMe")}</span>
          </label>
        </#if>

        <input type="hidden" id="id-hidden-input" name="credentialId"
               <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if> />

        <#--
          Nút chính. `<button>` chứ không `<input type="submit">`: nhãn nằm trong
          nội dung thẻ nên nút KHÔNG đổi bề rộng khi `giapha.js` chuyển sang
          "Đang đăng nhập…" (danh mục kiểm 06 §11 #8) — bề rộng đã khoá bằng
          `width:100%`, và nhãn dài hơn cũng không đẩy nút to ra.
        -->
        <button class="gp-nut gp-nut--chinh" id="gp-nut-dang-nhap" name="login" type="submit"
                data-dang-gui="${msg('giaphaSigningIn')}">${msg("doLogIn")}</button>
      </form>
    </#if>

    <div class="gp-vach"><span>${msg("giaphaOr")}</span></div>

    <#-- ══════ CHỖ CẮM ZALO ══════════════════════════════════════════════════
         `realm.identityProviders` hôm nay rỗng (hồ sơ Zalo OA cần một tư cách
         pháp nhân dòng họ chưa có), nên khối dưới đây in RA ĐÚNG 0 ĐIỂM ẢNH và
         thẻ tự khít lại. Không có nút xám "sắp có" — 06 §4.5 luật 2.

         Khi có OA: thêm một mục vào `identityProviders` của realm-giapha.json
         (KHÔNG phải nhúng SDK Zalo vào trang — 06 §4.5 luật 4), và nút hiện ra
         đúng chỗ này: sau "Đăng nhập", trước "Xem phần công khai".
    -->
    <@choCamZalo/>

    <#--
      Nút phụ CÙNG chiều cao và CÙNG cỡ chữ với nút chính (06 §4.1 ③): đây là
      một lựa chọn thật, không phải một liên kết nhỏ nép ở góc. Khác biệt duy
      nhất là không tô nền.
    -->
    <#--
      Đường dẫn chế độ khách. Thứ tự ưu tiên:
        1. `giaphaGuestUrl` — ghi đè tuyệt đối (cổng công khai ở tên miền khác)
        2. `client.baseUrl` + `giaphaGuestPath` với `{lang}` thay bằng ngôn ngữ
           người dùng đang chọn TRÊN CHÍNH TRANG NÀY
        3. `client.baseUrl` trần

      Dẫn thẳng vào PHẢ ĐỒ chứ không về trang chủ: `GET /api/v1/public/tree` trả
      cây thật từ Thuỷ tổ mà không cần tham số lẫn token, nên nút này giữ đúng
      lời nó hứa ngay ở lượt bấm đầu. Về trang chủ là trả một nửa.
    -->
    <#assign duongKhach = "">
    <#if properties.giaphaGuestUrl?has_content>
      <#assign duongKhach = properties.giaphaGuestUrl>
    <#elseif (client.baseUrl)?has_content>
      <#assign goc = client.baseUrl?ends_with("/")?then(client.baseUrl, client.baseUrl + "/")>
      <#assign duongKhach = goc + (properties.giaphaGuestPath!"")?replace("{lang}", lang)>
    </#if>
    <#if duongKhach?has_content>
      <a class="gp-nut gp-nut--phu" href="${duongKhach}">${msg("giaphaGuestButton")}</a>
    </#if>

    <#-- Tuyến /moi/[token] chưa tồn tại trong frontend; `giaphaInviteUrl` để
         rỗng thì khối này không in ra. Một nút dẫn tới 404 là một ngõ cụt. -->
    <#if properties.giaphaInviteUrl?has_content>
      <p style="margin:16px 0 0">
        ${msg("giaphaNoAccount")}
        <a class="gp-lien-ket" href="${properties.giaphaInviteUrl}">${msg("giaphaHaveInviteCode")}</a>
      </p>
    </#if>

  <#elseif section = "duoi-the">

    <#-- ══════ KHỐI "KHÁCH XEM ĐƯỢC GÌ" (06 §8) ════════════════════════════
         Đặt DƯỚI thẻ đăng nhập, tức SAU nút "Xem phần công khai" trên điện
         thoại — thông tin vẫn có, không tranh chỗ với việc chính.
    -->
    <section class="gp-khach" aria-labelledby="gp-khach-dau">
      <h2 class="gp-khach-dau" id="gp-khach-dau">${msg("giaphaGuestTitle")}</h2>

      <#if properties.giaphaPublicBrowsingReady == "true">
        <#--
          Hai cột CÂN NHAU — không cột nào là lời xin lỗi cho cột kia (06 §8).
          Bốn mục bên trái không phải khẩu hiệu: mỗi mục đối chiếu với một lượt
          gọi thật vào `/api/v1/public/**`. Thêm mục thứ năm thì phải gọi thử
          trước, rồi mới viết.
        -->
        <div class="gp-khach-cot-boc">
          <div class="gp-khach-cot">
            <p class="gp-khach-nhan gp-khach-nhan--duoc">${msg("giaphaGuestCanTitle")}</p>
            <ul>
              <li>${msg("giaphaGuestCan1")}</li>
              <li>${msg("giaphaGuestCan2")}</li>
              <li>${msg("giaphaGuestCan3")}</li>
              <li>${msg("giaphaGuestCan4")}</li>
            </ul>
          </div>
          <div class="gp-khach-cot">
            <p class="gp-khach-nhan gp-khach-nhan--khong">${msg("giaphaGuestCannotTitle")}</p>
            <ul>
              <li>${msg("giaphaGuestCannot1")}</li>
              <li>${msg("giaphaGuestCannot2")}</li>
              <li>${msg("giaphaGuestCannot3")}</li>
            </ul>
          </div>
        </div>
      <#else>
        <#-- Backend chưa mở điểm cuối công khai (xem chú thích ở
             theme.properties). Liệt kê "xem được gì" lúc này là hứa hàng chưa
             có — nên ở đây ta TRẢ LỜI THẲNG là chưa mở được, rồi mới nói phần
             luôn đúng. Bỏ câu này đi thì tiêu đề hỏi "xem được gì?" mà cả khối
             chỉ liệt kê những thứ KHÔNG xem được: một câu hỏi không có câu trả
             lời, đúng kiểu mơ hồ mà 00 §1 nói người lớn tuổi không tha thứ. -->
        <p class="gp-dan">${msg("giaphaGuestNotReady")}</p>
        <p class="gp-khach-nhan gp-khach-nhan--khong">${msg("giaphaGuestCannotTitle")}</p>
        <ul>
          <li>${msg("giaphaGuestCannot1")}</li>
          <li>${msg("giaphaGuestCannot2")}</li>
          <li>${msg("giaphaGuestCannot3")}</li>
        </ul>
      </#if>

      <#-- KHÔNG BAO GIỜ viết "có N người đang sống bị ẩn": con số ấy tự nó là
           một rò rỉ (02 §4.1, và 06 §8). -->
      <p class="gp-khach-luat">${msg("giaphaGuestLaw")}</p>
    </section>

  </#if>

</@layout.registrationLayout>
