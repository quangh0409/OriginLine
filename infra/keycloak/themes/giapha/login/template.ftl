<#--
  Khuôn chung cho mọi trang của theme `giapha`.

  Giữ nguyên chữ ký macro của `base/login/template.ftl` (tên macro, tên tham số,
  tên các `section`) để các trang chưa dựng lại vẫn gọi được. Đổi chữ ký ở đây
  là làm hỏng im lặng mọi trang Keycloak mà ta chưa đụng tới.

  Bố cục theo phương án A của `design/06-dang-nhap` §4.1: một cột, bảng tên ở
  trên biểu mẫu, không ảnh nền.
-->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<#-- `lang` là biến toàn cục Keycloak luôn đặt sẵn; `locale.currentLanguageTag`
     chỉ có khi realm bật đa ngôn ngữ. Dùng `lang` làm gốc để trang không vỡ nếu
     ai đó tắt cờ ấy. -->
<#assign ngonNguHienTai = (locale.currentLanguageTag)!lang>
<!DOCTYPE html>
<html lang="${lang}" dir="ltr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <#-- Trang đăng nhập của một dòng họ không có việc gì trong kết quả tìm kiếm. -->
  <meta name="robots" content="noindex, nofollow">
  <meta name="color-scheme" content="light dark">
  <meta name="theme-color" content="#8c2d19" media="(prefers-color-scheme: light)">
  <meta name="theme-color" content="#1a1614" media="(prefers-color-scheme: dark)">
  <#--
    Tiêu đề tab. Mỗi trang tự đặt `<#global tieuDeTab = ...>` TRƯỚC khi gọi macro
    này; trang nào không đặt thì tiêu đề còn lại tên dòng họ.

    `<#global>` chứ KHÔNG `<#assign>`: template.ftl được nạp bằng `<#import>` nên
    nó có KHÔNG GIAN TÊN RIÊNG. Một `<#assign>` ở trang gọi rơi vào không gian
    tên của TRANG, mà macro thì tra theo thứ tự cục bộ → không gian tên của
    CHÍNH NÓ → biến toàn cục → mô hình dữ liệu. Không gian tên của trang gọi
    không nằm trong chuỗi ấy, nên `<#assign>` im lặng không có tác dụng: tiêu đề
    vẫn ra, chỉ là thiếu nửa đầu, và không có lỗi nào in ra.

    Vì sao không dùng một `section "title"` rồi `<#assign x><#nested "title"></#assign>`:
    phép gán bọc quanh `<#nested>` trả về một TemplateMarkupOutputModel chứ không
    phải chuỗi, nên `?trim` ném lỗi và cả trang thành HTTP 500. Nhật ký chỉ đúng
    dòng, nhưng thông điệp ("coerceModelToTextualCommon") không nói gì về nguyên
    nhân — ghi lại đây để người sau khỏi mất buổi chiều.

    Vì sao phải có tiêu đề: nó là thứ duy nhất phân biệt bốn tab Keycloak đang mở
    cùng lúc, và là câu đầu tiên trình đọc màn hình đọc lên khi trang tải. Bỏ
    trống thì trình duyệt in đường dẫn — "localhost:8081/realms/giapha/..." là
    thứ không nên xuất hiện trước mặt người trong họ.
  -->
  <#assign tenDongHo = (realm.displayName!'')?has_content?then(realm.displayName, msg("giaphaClanFallback"))>
  <title><#if (tieuDeTab!'')?has_content>${tieuDeTab} — </#if>${tenDongHo}</title>
  <#list properties.styles?split(' ') as style>
    <#if style?has_content>
      <link rel="stylesheet" href="${url.resourcesPath}/${style}">
    </#if>
  </#list>
</head>
<body class="gp-trang ${bodyClass}" data-page-id="${pageId!''}">

  <#-- Điểm dừng Tab ĐẦU TIÊN (danh mục kiểm 06 §11 #4). -->
  <a class="gp-bo-qua" href="#gp-chinh">${msg("giaphaSkipToMain")}</a>

  <header class="gp-thanh">
    <p class="gp-thanh-ten">${tenDongHo}</p>

    <#-- Công tắc ngôn ngữ: CHỮ, không cờ. Cờ là quốc gia chứ không phải ngôn
         ngữ, và một biểu tượng đứng một mình vi phạm 00 §2.3. Hai liên kết cạnh
         nhau chứ không một menu xổ xuống: với hai lựa chọn, một menu bắt người
         dùng bấm hai lần để làm một việc, và bấm đầu tiên không cho biết gì. -->
    <#if realm.internationalizationEnabled && locale.supported?size gt 1>
      <#--
        THỨ TỰ: ngôn ngữ gốc đứng TRƯỚC. 00 §2.6 — "tiếng Việt là ngôn ngữ gốc,
        tiếng Anh phục vụ kiều bào". Keycloak trả `locale.supported` sắp theo
        nhãn, nên để nguyên thì "English" đứng trước "Tiếng Việt" trên chính
        trang tiếng Việt của một dòng họ Việt Nam.

        Không đọc được `realm.defaultLocale` từ theme — `RealmBean` không phơi ra
        thuộc tính ấy (đã thử: trả về rỗng). Nên ngôn ngữ gốc khai trong
        theme.properties, và README dặn giữ nó trùng `defaultLocale` của realm.
      -->
      <#assign ngonNguChinh = properties.giaphaPrimaryLocale!'vi'>
      <#assign dsNgonNgu = []>
      <#list locale.supported as l>
        <#if l.languageTag == ngonNguChinh><#assign dsNgonNgu = dsNgonNgu + [l]></#if>
      </#list>
      <#list locale.supported as l>
        <#if l.languageTag != ngonNguChinh><#assign dsNgonNgu = dsNgonNgu + [l]></#if>
      </#list>

      <nav class="gp-ngonngu" aria-label="${msg("giaphaLangNav")}">
        <#list dsNgonNgu as l>
          <#if l?index gt 0><span class="gp-ngonngu-ngan" aria-hidden="true">·</span></#if>
          <#if l.languageTag == ngonNguHienTai>
            <span class="gp-ngonngu-nut gp-ngonngu-nut--dang" aria-current="true">${l.label}</span>
          <#else>
            <a class="gp-ngonngu-nut" href="${l.url}" hreflang="${l.languageTag}" lang="${l.languageTag}">${l.label}</a>
          </#if>
        </#list>
      </nav>
    </#if>
  </header>

  <main class="gp-than" id="gp-chinh">
    <div class="gp-the">

      <#-- ── Bảng tên: ba dòng chữ, không ảnh (06 §2.2) ─────────────────────
           Dòng 2 và dòng 3 chỉ in khi theme.properties có chữ thật. -->
      <div class="gp-bien">
        <h1 class="gp-bien-ten">${tenDongHo}</h1>
        <#if properties.giaphaClanSubtitle?has_content>
          <p class="gp-bien-phu">${properties.giaphaClanSubtitle}</p>
        </#if>
        <#if properties.giaphaClanScale?has_content>
          <p class="gp-bien-phu">${properties.giaphaClanScale}</p>
        </#if>
      </div>

      <#-- ── Tiêu đề màn. Trang đăng nhập KHÔNG in tiêu đề này (bảng tên đã
           làm việc ấy); các trang khác thì có. -->
      <#nested "header">

      <#-- ── Thông báo toàn trang ───────────────────────────────────────────
           `role="alert"` chứ không chỉ chữ đỏ: danh mục kiểm 06 §11 #6 đòi câu
           lỗi phải được TRÌNH ĐỌC MÀN HÌNH đọc lên khi nó xuất hiện. -->
      <#if displayMessage && message?? && message.summary?has_content>
        <#--
          06 §7.2 luật 4: KHÔNG dùng màu đỏ lỗi cho luật chạy đúng. `warning` và
          `info` là hệ thống đang chạy ĐÚNG, không phải sự cố — chúng lấy nền hổ
          phách / nền giấy. Chỉ `error` mới đỏ.
        -->
        <#assign kieuBao = "xong">
        <#if message.type == 'error'><#assign kieuBao = "loi">
        <#elseif message.type == 'warning'><#assign kieuBao = "nhac">
        <#elseif message.type == 'info'><#assign kieuBao = "">
        </#if>
        <div class="gp-bao<#if kieuBao?has_content> gp-bao--${kieuBao}</#if>" role="alert">
          <p class="gp-bao-dau">${kcSanitize(message.summary)?no_esc}</p>
        </div>
      </#if>

      <#nested "form">

    </div><#-- .gp-the -->

    <#nested "duoi-the">

    <#-- ── Khối "gọi một người thật" ───────────────────────────────────────
         06 §2.3 điểm 3: mọi ngõ cụt phải in tên và số máy của một CON NGƯỜI.
         Chưa cấu hình thì in câu chung chung — không bịa số (00 §5). -->
    <section class="gp-goi" aria-labelledby="gp-goi-dau">
      <h2 class="gp-goi-dau" id="gp-goi-dau">${msg("giaphaHelpTitle")}</h2>
      <#if properties.giaphaHelpName?has_content>
        <p class="gp-goi-ten">${properties.giaphaHelpName}</p>
        <#if properties.giaphaHelpPhone?has_content>
          <a class="gp-goi-so" href="tel:${properties.giaphaHelpPhone}">${properties.giaphaHelpPhone}</a>
        </#if>
      <#else>
        <p>${msg("giaphaHelpNoContact")}</p>
      </#if>
    </section>

  </main>

  <footer class="gp-chan">
    <p>${msg("giaphaPrivacyFooter")}</p>
  </footer>

  <script src="${url.resourcesPath}/js/giapha.js" defer></script>
</body>
</html>
</#macro>
