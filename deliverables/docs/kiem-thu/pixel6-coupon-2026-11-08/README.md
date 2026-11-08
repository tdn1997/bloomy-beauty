# Nghiệm thu A3 — mã giảm giá — 08/11/2026

Pixel_6 đã có (`emulator-5554`), Android 16/API 36. Package `.demo`, schema 10, Firebase tắt, mã mẫu cục bộ bật. Không tạo AVD mới hoặc thay giờ hệ thống.

## Kết quả

- **37/37 Android đạt**, không bỏ qua, 339,056 giây (`final-acceptance.txt`).
- **58/58 JVM đạt**, gồm 12 ca coupon/giá; lint **0 lỗi/38 cảnh báo**, build APK và kiểm thử đạt (`build-jvm-lint.txt`, `jvm-summary.json`).
- Bốn ảnh đã xem trực tiếp, không bị hộp thoại hệ thống che; JSON cùng tên ghi trạng thái báo giá và clock kiểm thử.

Bộ Android gồm 11 ca CouponOrderTest, 2 migration coupon, 16 hồi quy OrderRepositoryTest, 6 migration module cũ và 2 UI. Kiểm tra mã cố định/%, trần giảm, đơn tối thiểu, thời gian hiệu lực, đổi điều kiện có/không tăng revision, khóa theo phiên, quote bị sửa, tắt tính năng, rollback audit, retry đồng thời, snapshot sau xóa mã và khôi phục sau mở lại DB.

Migration 9 → 10 giữ lịch sử giảm 0. Payload v1 đã commit replay cùng đơn, giữ giỏ mới; payload chưa commit bị chặn tạo trực tiếp, được chuyển sang quote v2 ở bước review và không đặt trước thao tác xác nhận. Fingerprint v1 được khóa bằng fixture hash JVM; v2 có rule/revision, giảm, fee và tổng đã duyệt.

Mỗi ca dùng DB riêng, SQL fixture hoặc repository thật. Ảnh UI từ ComponentActivity/CartScreen fixture, không phải dữ liệu khách hàng. Ca hết hạn dùng clock tiêm trong LocalCouponRepository, không đổi giờ AVD; `repositoryNow` trong JSON là mốc đánh giá hiệu lực, còn `deviceNow` là thời điểm chụp. Vì vậy không dùng đồng hồ thanh trạng thái để suy thời điểm kiểm thử rule.

## Môi trường kiểm thử

Lượt đầu có một lỗi khởi tạo JUnit do setup trả sai kiểu; đã sửa để hai ca migration chạy thật. Lượt tổng trung gian có lỗi cleanup Activity ở STOPPED thay vì DESTROYED, không đánh dấu lượt đó đạt. Lượt cuối tạm tắt ba animation scale và khôi phục đúng các giá trị ban đầu sau khi xong; xem `animation-settings.json`. Log lỗi trung gian được giữ riêng để đối chiếu.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -Ppresentation=true --offline
```

Hai APK được cài bằng adb install -r; các lớp/case thực tế liệt kê trong log cuối. Không phải lượt chạy lại toàn bộ 105 ca Android cũ.

## Ảnh

- `01-ap-ma-phan-tram.png`: subtotal 576.000đ, giảm bị chặn ở 50.000đ, ship 0, tổng 526.000đ.
- `02-ma-het-han.png`: giữ báo giá cũ, hiển thị hết hạn và chặn đặt; chưa tạo đơn.
- `03-chu-dong-bo-ma.png`: khách chủ động bỏ mã, tổng mới 576.000đ rồi mới xác nhận đặt.
- `04-don-luu-snapshot-giam-gia.png`: đơn giữ mã DEMO50K, giảm 50.000đ, tổng 526.000đ dù rule đã xóa.

`manifest.json` ghi hash APK, nguồn A3 và bằng chứng tại thời điểm chốt trước A4. Bản thường không bật áp mã mẫu; API tắt tính năng đã được kiểm tra trong CouponOrderTest. Chưa có quota/server checkout, chưa đồng bộ Firebase hoặc kiểm tra thiết bị thật.
