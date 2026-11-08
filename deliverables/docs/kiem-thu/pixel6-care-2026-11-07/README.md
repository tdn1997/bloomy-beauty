# Kiểm thử và ảnh minh chứng trên Pixel 6 — 07/11/2026

AVD: **Pixel_6 của người dùng**, API 36, ARM64. Đợt này không tạo hoặc mở AVD khác; phần thiết bị thật để sau. Chạy trên package `com.example.bloomybeauty.demo`, cài đè bằng ADB để giữ dữ liệu; không dùng Gradle connected test tự gỡ APK.

## Kết quả

- Nhóm F1–F3: **12 đạt, 1 bỏ qua theo điều kiện, 0 lỗi** trong 13 ca. [Kết quả từng ca](features.json), [log instrumentation](instrumentation.txt).
- Bộ hồi quy toàn app sau sửa fixture: **98 ca đạt, 7 bỏ qua theo điều kiện, 0 lỗi** trong 105 ca. [Kết quả từng ca](regression.json), [log cuối](regression.txt). JVM: 46/46 đạt; lint: 0 lỗi, 38 cảnh báo, 1 thông tin.

Ca bị bỏ qua cần thu hồi quyền notification trước instrumentation. Ca này đã có bằng chứng trong các đợt trước; không tính là ca đạt ở lần chạy này.

## Ảnh chụp trên Pixel_6

Ảnh chụp bằng UiAutomation tại các mốc kiểm thử Compose. Các ca giao diện gọi Repository/ContentResolver/Provider/SQLite thật, sử dụng tài khoản tạm và phục hồi phiên trước đó sau kiểm thử. Đây là ảnh kiểm thử giao diện, không phải bằng chứng giao alarm thật trong đợt này.

| Ảnh | Nội dung |
|---|---|
| [01](anh/01-bieu-mau-them.png) | Nhập tên và cấu hình lịch |
| [02](anh/02-lich-da-luu.png) | Lịch đã lưu qua Provider |
| [03](anh/03-sua-lich.png) | Sửa tên lịch |
| [04](anh/04-tat-nhac.png) | Lịch ở trạng thái tắt nhắc |
| [05](anh/05-xac-nhan-xoa.png) | Dialog xác nhận xóa |
| [06](anh/06-da-xoa-lich.png) | Danh sách rỗng sau khi xóa |
| [07](anh/07-khoi-phuc-nhap-va-loi-ngay.png) | Bản nháp giữ sau khôi phục state; không lưu khi chưa chọn ngày |

Ảnh được đưa vào [báo cáo chính, mục 7.1](../../bao-cao/bao-cao-de-tai.md) và [báo cáo HTML](../../bao-cao/bao-cao-de-tai.html).

Từ đợt này, script QA chỉ chấp nhận AVD `Pixel_6` theo yêu cầu. Các kết quả trên BloomyCare ở thư mục khác được giữ như lịch sử, không mô tả là các lần chạy trên Pixel_6.

## Điều chỉnh kiểm thử trong đợt này

Lần toàn bộ ban đầu có 3 lỗi fixture: package assertion cố định, tên package IME kiểm thử cố định và kỳ vọng FCM hoạt động trong demo tắt Firebase. Đã sửa theo BuildConfig/application ID và chạy lại toàn bộ. [Log lần đầu](regression-initial.txt) được giữ để đối chiếu, không dùng làm kết quả cuối. Không thay mã nghiệp vụ app.
