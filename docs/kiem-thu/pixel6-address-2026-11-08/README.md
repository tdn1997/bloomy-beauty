# Nghiệm thu A2 — sổ địa chỉ — 08/11/2026

Chỉ dùng AVD Pixel_6 đã có (`emulator-5554`), Android 16/API 36, package `com.example.bloomybeauty.demo`, schema 9, Firebase tắt.

## Kết quả

- **24/24 ca Android đạt**, không bỏ qua, 594,823 giây (`final-acceptance.txt`).
- **46/46 JVM đạt**; build debug/APK kiểm thử và lint đạt, lint **0 lỗi, 38 cảnh báo** (`build-jvm-lint.txt`, `jvm-summary.json`).
- Bộ ca gồm 10 address repository, 4 profile repository, 7 hồi quy/migration các module cũ và 3 UI (2 sổ địa chỉ, 1 đổi tên không ghi đè draft giao hàng).

Các ca dữ liệu kiểm tra chuẩn hóa số điện thoại, quyền tài khoản/phiên, tối đa 10 địa chỉ kể cả tạo đồng thời, đổi/xóa mặc định nguyên tử, rollback khi lỗi ghi, khóa ID/revision và token đã xóa. Migration schema 8 → 9 nhập địa chỉ cũ một lần, giữ phiên/giỏ/yêu thích/đơn/đánh giá/lịch, kiểm tra foreign key và integrity. Các migration từ 1/2/3/4/5/7 cũng đã chạy tới schema 9 trong bộ hồi quy.

Luồng UI kiểm tra số điện thoại sai, tạo hai địa chỉ, đặt mặc định, chọn vào checkout, sửa/xóa địa chỉ nguồn và đặt COD bằng snapshot cũ. Ca khác giữ bản nháp biểu mẫu qua khôi phục trạng thái và chỉ lưu vào sổ khi bấm lưu. Đổi tên hồ sơ không ghi đè giao hàng đang nhập.

Lượt đầu có 3 fixture lưu giao hàng trước khi thêm sản phẩm nên bị chặn đúng quy tắc giỏ trống (`initial-fixture-errors.txt`). Đã sửa thứ tự fixture, xác nhận 6/6 (`fixed-fixtures-and-ui.txt`), sau đó chạy lại bộ 24 ca đạt toàn bộ. Không cộng các lượt trùng nhau thành tổng kiểm thử mới.

## Cách chạy

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -Ppresentation=true --offline
```

Cài hai APK bằng `adb install -r`, chạy AndroidJUnitRunner với các lớp/case được liệt kê trong `final-acceptance.txt`. Chỉ sử dụng Pixel_6; chưa kiểm tra thiết bị thật hoặc đồng bộ Firebase. Không phải lượt chạy lại toàn bộ 105 ca Android cũ.

## Ảnh

Ảnh UIAutomation từ ComponentActivity với database fixture riêng và repository thật. Harness nối AddressScreen/CartScreen theo luồng ứng dụng; không phải dữ liệu khách hàng. Hash APK, nguồn A2 và ảnh ghi trong `manifest.json` tại thời điểm chốt A2 trước A3.

- `01-dia-chi-dau-tien.png`: địa chỉ đầu tự làm mặc định, số điện thoại đã chuẩn hóa.
- `02-doi-mac-dinh.png`: chuyển mặc định sang Văn phòng.
- `03-chon-tai-checkout.png`: bản sao địa chỉ Nhà trong giao hàng.
- `04-don-giu-dia-chi-snapshot.png`: đơn vẫn dùng địa chỉ đã chọn dù nguồn bị sửa/xóa.
- `05-luu-tu-ban-nhap.png`: lưu thông tin giao hàng vào sổ bằng hành động rõ ràng.
