# Kiểm tra Content Provider và Broadcast Receiver — 07/11/2026

Môi trường: emulator riêng `BloomyCareQA`, Android 16/API 36, arm64, trang nhớ 16 KB; compile/target SDK 35, min SDK 28. Không dùng AVD Pixel_6 sẵn có của người dùng.

## Kiểm tra tự động

| Lần chạy | Kết quả | Minh chứng |
|---|---|---|
| JVM | 46 đạt, 0 lỗi | [Tóm tắt hồi quy](regression.json) |
| Android toàn bộ, trước chỉnh sửa cuối cho alarm | 97 đạt, 5 bỏ qua, 0 lỗi trong 102 ca | [Tóm tắt hồi quy](regression.json) |
| Android nhóm F1–F3 sau chỉnh sửa alarm | 12 đạt, 1 bỏ qua, 0 lỗi trong 13 ca | [Tên và kết quả từng ca](features.json) |
| Từ chối quyền thông báo, chạy riêng sau ADB revoke | 1 đạt | [Kết quả](permission-denied.json) |

Các nhóm có ca trùng nhau. Không cộng các lần chạy thành số ca độc lập. Năm ca hồi quy bỏ qua gồm 1 Firestore live, 3 Firestore emulator và 1 FCM permission denied; các ca này yêu cầu cấu hình riêng. Ca từ chối quyền trong nhóm F1–F3 đã được chạy riêng sau khi cài APK app/test rồi thu hồi quyền trước instrumentation.

Bao phủ phần mới: Provider CRUD/MIME/URI, revision cũ, dữ liệu sai và giả chủ sở hữu, cách ly phiên, ContentObserver, migration 5 → 6 giữ giỏ/đơn/tồn, nhận lặp và hai lịch cùng giờ, receiver tĩnh/notification, đăng xuất hủy nhắc, broadcast sau commit và không phát khi rollback/retry, giao diện CRUD và khôi phục bản nháp.

## Build và biến thể

- Debug thường: assemble, JVM và lint đạt; lint 0 lỗi, 38 cảnh báo, 1 thông tin.
- Release QA: assemble/lint với `-PqaRelease=true` đạt; lint 0 lỗi, 34 cảnh báo, 1 thông tin.
- Demo: assemble với `-Ppresentation=true` đạt, application ID `.demo`.
- Cả ba package cài song song trên emulator. `dumpsys package` xác nhận các authority `.care`, `.demo.care`, `.qa.care` tương ứng.
- Đã đăng nhập admin demo và mở màn hình lịch trong chế độ máy bay. Bản demo không khởi tạo Firebase.

## Kiểm tra thực tế

Đăng ký khách mới trên giao diện bản thường, mở lịch từ Tài khoản, tạo lịch nhắc hằng ngày lúc 10:29. Sau đó khởi động lại emulator mà không mở lại ứng dụng. `dumpsys alarm` xác nhận receiver đã khôi phục alarm cho 10:29: [alarm sau reboot](alarm-after-reboot.txt).

Sau reboot, kết thúc tiến trình app bằng tín hiệu kill từ cùng UID trên emulator kiểm thử, không dùng Force stop. PID trước kill là 2906; xác nhận không còn tiến trình trước khi tới giờ. Trong chế độ máy bay, đến 10:29:37 đã quan sát thấy notification `care:2` và tiến trình mới PID 3980. Đây là mốc quan sát, không khẳng định notification được giao chính xác tại giây 37.

Chạm notification mở đúng biểu mẫu lịch 10:29 của tài khoản khách. Quay lại danh sách vẫn có lịch đã lưu. Trên bản demo đang ngoại tuyến, mở sản phẩm N7 → tạo lịch thì tên lịch và sản phẩm được điền sẵn. Sau kiểm tra đã tắt chế độ máy bay.

Ảnh minh chứng:

- [Danh sách lịch đã lưu](01-lich-cham-soc.png).
- [Tạo lịch từ sản phẩm trên demo ngoại tuyến](02-demo-tao-tu-san-pham.png).
- [Notification do alarm thật sau reboot và process death](03-thong-bao-alarm-that.png).
- [Mở đúng lịch khi chạm notification](04-mo-lich-tu-thong-bao.png).

Kết quả có cấu trúc: [Kiểm tra alarm thực tế](live-alarm.json). Chưa chạy kịch bản Doze kéo dài hoặc nghiệm thu thời điểm nhắc trên thiết bị thật.

## Giới hạn

Các ca API 28/33/35 và Doze được tiếp tục trong [đợt kiểm tra bằng giả lập](../content-broadcast-emulator-2026-11-07/README.md); phần thiết bị thật được để lại cho đợt sau. Không đảm bảo thời điểm giao alarm chính xác tuyệt đối. Các ca receiver tự động dùng Intent mô phỏng để kiểm tra xử lý; việc này được phân biệt với kiểm tra alarm thực tế ở trên. F4 chia sẻ danh mục liên ứng dụng chưa triển khai. APK/ZIP bàn giao cũ chưa được đóng gói lại.
