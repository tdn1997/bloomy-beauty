# Kiểm thử Mua lại đơn cũ — 16/11/2026

## Kết quả thực tế

| Hạng mục | Kết quả |
|---|---|
| Build demo `assembleDebug` | Đạt |
| Biên dịch/đóng gói `assembleDebugAndroidTest` | Đạt; không đồng nghĩa đã chạy instrumentation |
| Unit test `testDebugUnitTest` | 71/71 đạt, 0 lỗi, 0 bỏ qua |
| Trong đó `ReorderRulesTest` mới | 9/9 đạt; có một ca duyệt 2.400 tổ hợp số lượng/tồn |
| `lintDebug` | 0 lỗi, 71 cảnh báo, 1 thông tin |
| Android `ReorderRepositoryTest` mới | 14/14 đạt |
| Android `ReorderFlowTest` mới | 7/7 đạt |
| Hồi quy Android liên quan | 36/36 đạt |
| Tổng lượt Android cuối | 57/57 đạt, 0 lỗi, 0 bỏ qua |
| Thiết bị | Pixel_6 mới, emulator-5554, Android 15/API 35, ARM64 |
| Ảnh minh chứng/runtime trên thiết bị | 8 ảnh thật trong thư mục `pixel6-reorder/` |

**Hoàn tất nghiệm thu phạm vi demo một thiết bị.** Lượt cuối chạy cả repository,
Compose UI, checkout và hồi quy liên quan; không dùng các kết quả Android lịch sử
thay cho lần chạy này. Chưa kiểm thử thiết bị thật hoặc đồng bộ nhiều thiết bị.

## Môi trường và lệnh đã chạy

- Biến thể debug demo, `-Ppresentation=true`, package `com.example.bloomybeauty.demo`.
- Gradle wrapper 8.11.1; JDK Temurin 21.0.13 đặt riêng trong `.local/jdk21`
  (bỏ qua bởi Git), không thay cấu hình Android Studio.
- JDK đi kèm Android Studio đã đổi sang 25.0.4 trong lúc thực hiện. Một lượt
  lỗi `Failed to exec spawn helper`; chạy lại với JDK 25 lỗi `25.0.4`.
  Đã dừng daemon cũ và dùng JDK 21, ghi đè `org.gradle.java.home` trên dòng lệnh
  vì cấu hình Gradle toàn máy đang trỏ vào JDK Android Studio.

```sh
JAVA_HOME="$PWD/.local/jdk21/Contents/Home" ./gradlew \
  "-Dorg.gradle.java.home=$PWD/.local/jdk21/Contents/Home" \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug -Ppresentation=true --no-daemon
```

Lượt build/unit/lint: `BUILD SUCCESSFUL in 42s`. Sau sửa test, biên dịch APK test
và lint chạy lại thành công trong 23 giây. Log gốc đã chép vào thư mục hồ sơ này;
log và XML/HTML kết quả được giữ cục bộ, không đưa vào Git theo `.gitignore`.

## Tạo lại thiết bị và các lượt kiểm thử

Ban đầu `adb devices` không có thiết bị. Lần đầu liệt kê AVD còn thấy `Pixel_6`, nhưng
khi khởi động thì emulator báo `Unknown AVD name [Pixel_6]`. Kiểm tra sau đó:
`/Users/thuan/.android/avd` trống; tìm `Pixel_6.ini` không thấy vị trí thay thế.
Sau khi người dùng xác nhận macOS vừa nâng cấp và yêu cầu tạo lại Pixel 6, đã
tạo đúng một AVD cùng tên. [Cấu hình và cách mở](../../20261116000000_pixel6-moi-2026-11-16.md).

- **API 37 ban đầu:** 37/57 đạt; 20 ca UI lỗi do Espresso cũ gọi API nội bộ
  `InputManager.getInstance` không còn có trên image này. 14/14 ca repository
  Mua lại đạt. Lưu log `android-tests-api37.log` và metadata cạnh bên.
- **API 35 lần đầu:** 54/57 đạt. Hai ca mới dùng assertion so toàn câu trong
  khi cần tìm đoạn thông báo; đã sửa sang `hasText(..., substring = true)`.
  Một ca checkout cũ bấm nút lúc IME đang đổi bố cục.
- **Kiểm tra lại UI:** 7/7 ca mua lại đạt, một ca checkout khác gặp cùng vấn đề
  IME. Đã sửa helper `CartFlowTest.openConfirmation()` đóng bàn phím và chờ idle
  trước khi định vị/bấm nút lưu; không đổi quy tắc sản phẩm/checkout vì lỗi test này.
- **Lượt cuối sau sửa:** `android-tests-final.log`: `OK (57 tests)`, 57 đạt,
  0 bỏ qua; script đã khôi phục cả ba thiết lập animation ban đầu.
  `android-tests-final.environment.json` ghi API/thiết bị, thời gian và kết quả.

| Bộ Android trong lượt cuối | Số ca đạt |
|---|---:|
| ReorderRepositoryTest | 14 |
| ReorderFlowTest | 7 |
| CartRepositoryTest | 7 |
| OrderRepositoryTest | 16 |
| CartFlowTest | 7 |
| CartRefreshTest | 4 |
| CommerceExtensionsIntegrationTest | 2 |

## Các bộ kiểm thử đã bổ sung

- `ReorderRulesTest`: giá/tên hiện tại, giữ giá lịch sử, gộp dòng trùng, thiếu tồn,
  giới hạn 99, sản phẩm mất/ngừng bán/hết hàng, giỏ vượt tồn, giá chưa chấp nhận,
  kiểm tra nhiều tổ hợp và tràn số tiền.
- `ReorderRepositoryTest`: quyền sở hữu/phiên/trạng thái; preview không ghi;
  gộp và giữ lịch sử/tồn; giữ giỏ sau mở lại DB; sản phẩm thiếu; kiểm tra lại
  dữ liệu; nhiều xác nhận đồng thời; rollback khi ghi dòng thứ hai hoặc xóa
  checkout lỗi; bảo toàn kết quả đã commit; xóa nháp/coupon cũ và tạo đơn mới.
- `ReorderFlowTest`: luồng thật từ tài khoản → đơn → xem trước → giỏ → checkout;
  thiếu tồn/đổi giá/ngừng bán; hủy; recreate activity; dữ liệu thay đổi cần xác
  nhận lần hai; khóa xác nhận khi không còn hàng; nháp giao hàng/coupon và màn
  kết quả checkout chưa hoàn tất.

## Chạy lại trên Pixel_6

Chỉ khởi động Pixel_6 đã được xác nhận. Kiểm tra `adb -s emulator-5554 emu avd name`
trước khi dùng script; nếu serial khác, truyền `--serial` tương ứng.

```sh
JAVA_HOME="$PWD/.local/jdk21/Contents/Home" ./gradlew \
  "-Dorg.gradle.java.home=$PWD/.local/jdk21/Contents/Home" \
  :app:installDebug :app:installDebugAndroidTest -Ppresentation=true

python3 scripts/run-pixel6-tests.py \
  --classes com.example.bloomybeauty.data.order.ReorderRepositoryTest,com.example.bloomybeauty.feature.order.ReorderFlowTest,com.example.bloomybeauty.data.cart.CartRepositoryTest,com.example.bloomybeauty.data.order.OrderRepositoryTest,com.example.bloomybeauty.feature.cart.CartFlowTest,com.example.bloomybeauty.feature.cart.CartRefreshTest,com.example.bloomybeauty.feature.app.CommerceExtensionsIntegrationTest \
  --log docs/kiem-thu/reorder-2026-11-16/android-tests-final.log \
  --disable-animations
```

`ReorderFlowTest` lưu ảnh thật vào external files của gói demo, thư mục
`pixel6-reorder`; đã chép 8 ảnh vào hồ sơ này. APK demo đã cài trên Pixel_6,
bản sao/checksum tại `deliverables/bloomy-beauty-demo-reorder-2026-11-16.apk`
và `.apk.sha256` trong thư mục gốc dự án. ZIP bàn giao cũ không được đóng gói lại.

## Ảnh minh chứng

| Ảnh | Nội dung |
|---|---|
| [01](pixel6-reorder/01-order-action.png) | Nút Mua lại trong chi tiết đơn |
| [02](pixel6-reorder/02-preview.png) | Xem trước lượng và giá trước khi gộp giỏ |
| [03](pixel6-reorder/03-cart-merged.png) | Giỏ sau khi thêm, giữ mặt hàng cũ |
| [04](pixel6-reorder/04-new-order.png) | Tạo đơn COD mới thành công |
| [05](pixel6-reorder/05-partial-preview.png) | Thiếu tồn, đổi giá, sản phẩm ngừng bán |
| [06](pixel6-reorder/06-partial-result.png) | Kết quả thêm một phần và bỏ qua |
| [07](pixel6-reorder/07-stale-preview.png) | Dữ liệu đổi, yêu cầu xác nhận lại |
| [08](pixel6-reorder/08-unavailable.png) | Không có mặt hàng có thể thêm |

[Hướng dẫn tính năng](../../20261116000000_mua-lai-don-cu.md) · [Kế hoạch](../../20261116000000_ke-hoach-mua-lai-don-cu.md).
