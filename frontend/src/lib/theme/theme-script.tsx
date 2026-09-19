/**
 * Đoạn script chặn, chạy TRƯỚC khi trình duyệt vẽ khung hình đầu tiên.
 *
 * <p>Không có nó thì người chọn chế độ Tối sẽ thấy trang <b>loé sáng trắng một nhịp</b> rồi mới
 * tối lại: HTML từ máy chủ không mang lớp nào (máy chủ không đọc được {@code localStorage}), nên
 * mãi tới khi React gắn kết xong lớp {@code .dark} mới được thêm vào. Với người lão thị, một cú
 * loé trắng toàn màn hình không chỉ khó chịu — nó chói.</p>
 *
 * <p>Vì vậy đoạn này phải là script <b>đồng bộ</b> đặt trong {@code <head>}, không phải effect.
 * Nó cố ý viết bằng JavaScript thuần, không phụ thuộc bundle, và tự nuốt mọi lỗi: chế độ riêng tư
 * có thể ném ngay ở {@code localStorage}, mà một trang trắng vì lỗi script còn tệ hơn nhiều so với
 * một cú loé sáng.</p>
 */
const SCRIPT = `(function(){try{
var l=localStorage.getItem('giapha.chu-de');
var r=document.documentElement;
if(l==='toi'){r.classList.add('dark');r.style.colorScheme='dark';}
else if(l==='sang'){r.classList.add('light');r.style.colorScheme='light';}
else{r.style.colorScheme=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';}
}catch(e){}})();`;

export function ThemeScript() {
  // eslint-disable-next-line react/no-danger -- chuỗi hằng do chính tệp này định nghĩa, không có
  // dữ liệu người dùng nào đi vào đây; đây là cách duy nhất chạy được trước khung hình đầu tiên.
  return <script dangerouslySetInnerHTML={{ __html: SCRIPT }} />;
}
