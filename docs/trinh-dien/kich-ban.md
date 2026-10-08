# Kịch bản trình diễn và bảo vệ

Ngày chuẩn bị 08/10/2026. APK `deliverables/bloomy-beauty-demo.apk`, package `.demo`. Admin `admin@bloomy.demo` / `BloomyDemo123!`; khách đăng ký `khach@bloomy.demo` / `KhachDemo123!`. Dữ liệu demo độc lập với gói gốc và release QA.

## Video 3–5 phút

| Thời gian dự kiến | Hình ảnh/thao tác | Lời trình bày |
|---|---|---|
| 00:00–00:20 | Trang chủ/title | “Bloomy Beauty là ứng dụng bán mỹ phẩm Android. Bản này dùng SQLite trên một thiết bị; đơn COD và tồn là dữ liệu demo.” |
| 00:20–00:45 | Đăng ký/đăng nhập khách | “Tài khoản có kiểm tra dữ liệu, mật khẩu băm và phiên riêng. Đăng ký không tự chọn quyền admin.” |
| 00:45–01:10 | Tìm/mở sản phẩm, yêu thích | “Có tám sản phẩm/ảnh tham khảo Cocoon. Tìm kiếm bỏ dấu tiếng Việt, lọc và sắp xếp giá. Ảnh đóng gói dùng ngoại tuyến.” |
| 01:10–01:45 | Giỏ/giao hàng/kiểm tra | “Giỏ lưu theo tài khoản. Với một gel 192.000 đồng, phí demo 30.000 đồng, tổng COD 222.000 đồng. Khách kiểm tra trước khi xác nhận.” |
| 01:45–02:10 | Xác nhận/kết quả/lịch sử | “Một request tạo một đơn. Transaction bảo đảm tiền, tồn và snapshot nhất quán. Thử lại cùng mã trả đơn đã có.” |
| 02:10–02:50 | Admin xử lý đơn | “Admin đọc quyền từ database hiện hành, chuyển đúng PENDING → CONFIRMED → SHIPPING → DELIVERED và lưu lịch sử.” |
| 02:50–03:15 | Khách đăng nhập lại/xem Đã giao | “Khách chỉ thấy đơn của mình. Đơn đã giao không có nút hủy. Tồn đã giảm một lần khi đặt, không giảm lại khi chuyển trạng thái.” |
| 03:15–03:40 | Slide kiểm thử/hạn chế | “105 ca đạt trên API 36. Đã thử tái tạo/cold start và ngang/chữ lớn. API 28, thiết bị nộp, khóa ký phát hành và backend còn chờ.” |

Video quay thực tế có thể có nhịp thao tác khác bảng dự kiến; phụ đề của video cần khớp những gì được ghi. Không đọc mật khẩu tài khoản riêng lên video. Tài khoản demo công khai chỉ dùng trong gói `.demo`.

## Video đã ghi

[Video MP4](bloomy-beauty-demo.mp4): 4 phút 10 giây, H.264 540×1200, phụ đề tiếng Việt được gắn vào video và có [SRT riêng](demo.srt). Không có thuyết minh âm thanh; lời đọc ở bảng trên dùng cho trình bày trực tiếp hoặc ghi âm thêm. Bản ghi thật trên Pixel 6/API 36, APK `.demo`, ngày 08/10/2026. Đã cắt đoạn chờ 10–60 giây ở bản ghi đầu và tăng tốc 1,6 lần. Tên/địa chỉ demo nhập ASCII khi điều khiển bằng ADB; nhập tiếng Việt được kiểm thử riêng ở Phase 12.

Kết quả sau diễn tập: một đơn DELIVERED, tổng 222.000 ₫, tồn cleanser 29, bốn dòng audit (tạo + ba chuyển trạng thái). Khách nhìn thấy Đã giao và không có Hủy đơn. SQLite integrity_check trả `ok`, foreign_key_check không có dòng lỗi.

## Thao tác trước khi bảo vệ

- Chọn đúng APK `.demo`, kiểm tra pin/emulator và bàn phím tiếng Việt.
- Đăng nhập thử admin; chuẩn bị khách và sản phẩm còn tồn. Cài đè APK không reset database.
- Nếu dữ liệu đã có đơn, chọn mã đơn thực tế trên màn hình; không hứa mã BB-000001/tồn 30 nếu database không mới.
- Mở slide ngoại tuyến; dùng ←/→ hoặc nút điều hướng, F để toàn màn hình; Ctrl/Cmd+P để in PDF.
- Kiểm tra QR/video/file ZIP trước buổi nộp nếu học phần yêu cầu; bộ hiện tại không tạo đường dẫn public hoặc upload.

## Câu hỏi bảo vệ và ý trả lời

| Câu hỏi | Ý trả lời gắn với mã đang có |
|---|---|
| Vì sao chọn SQLite? | Yêu cầu đề tài và mục tiêu học Android local storage; dùng SQLiteOpenHelper, migration và transaction. Không phù hợp kho dùng chung nhiều thiết bị nếu thiếu backend. |
| Mật khẩu được lưu thế nào? | Hash PBKDF2-HMAC-SHA256 + salt riêng 16 byte + 210.000 vòng. Database không lưu password rõ. Không gọi đây là hệ thống danh tính server. |
| Khách sửa userId thì sao? | Repository kiểm tra auth_session trong transaction. Đơn truy vấn thêm điều kiện owner. Admin kiểm tra role từ database mỗi lần. |
| Bấm đặt tám lần có tám đơn? | requestId UNIQUE + fingerprint; cùng mã/nội dung trả cùng đơn. Ca retry đồng thời chỉ một đơn/tồn giảm một lần. |
| Một dòng hết hàng thì sao? | Toàn transaction rollback; các dòng đủ tồn cũng không bị trừ khi đơn thất bại. |
| Vì sao lưu snapshot? | Tên/giá lúc mua không đổi khi admin sửa danh mục. Có FK sản phẩm để nhận diện, đồng thời có snapshot cho lịch sử. |
| Revision giải quyết gì? | Bản nháp sửa tồn cũ bị từ chối sau đặt/hủy/cập nhật khác, tránh ghi đè thay đổi tồn. |
| Khách và admin hủy/xác nhận cùng lúc? | UPDATE có trạng thái kỳ vọng, transaction tuần tự hóa. Chỉ trạng thái hợp lệ lưu, tồn khớp; ca race SQL không tương đương kiểm thử hai thiết bị. |
| Tắt ứng dụng sau khi đặt? | Request/result lưu SQLite. Cold start đọc lại đúng đơn. Bản nháp chưa lưu không được hứa tồn tại sau process death. |
| Có bán hàng thật/online payment? | Không. COD demo trên một thiết bị; chưa có API cửa hàng hoặc thanh toán trực tuyến. |
| Còn thiếu gì trước nộp? | Điền thông tin học phần/thành viên, chạy API 28/thiết bị nộp, xác nhận quy cách nộp và khóa ký nếu cần release chính thức. |

## Phân công

Điền theo đóng góp thực tế của từng thành viên ở phụ lục báo cáo. Chưa có dữ liệu để chốt phân công hoặc xác nhận toàn bộ nhóm đã diễn tập. Bộ tài liệu kỹ thuật không tự thay thế xác nhận của giảng viên/nhóm.
