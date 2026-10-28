# Thông báo đẩy Firebase Cloud Messaging

Bản debug/release gốc (`com.example.bloomybeauty`) nhận FCM. Bản `.demo` và `.qa`
không khởi tạo Firebase. Chạy bản gốc trên thiết bị/emulator có Google Play services
và mạng; Android 13+ sẽ hỏi quyền thông báo một lần khi mở ứng dụng. Nếu từ chối,
ứng dụng vẫn dùng bình thường; bật lại trong Cài đặt Android → Ứng dụng → BloomyBeauty
→ Thông báo. Kênh thông báo là `bloomy_updates`.

## Gửi thử từ Firebase Console

1. Build/cài bản gốc bằng Android Studio, mở ứng dụng và cho phép thông báo.
2. Đọc token của bản debug bằng lệnh dưới đây. Token nằm trong bộ nhớ riêng của
   ứng dụng, được cập nhật khi mở ứng dụng và khi FCM đổi token; không ghi vào log
   và không sao lưu/chuyển sang thiết bị khác.

   ```sh
   adb shell run-as com.example.bloomybeauty cat shared_prefs/push_registration.xml
   ```

   Nếu chưa có file/token, kiểm tra mạng, Google Play services và cấu hình Firebase,
   sau đó mở lại ứng dụng. Không đưa token vào Git hoặc báo cáo công khai.
3. Mở Firebase Console đúng project → Messaging → tạo notification campaign →
   nhập tiêu đề/nội dung → Send test message → dán token → gửi thử.
4. Thử khi app đang mở, về màn hình chính Android, và đóng app khỏi recent apps.
   Chạm thông báo để mở BloomyBeauty. Nếu đã Force stop trong Cài đặt Android,
   cần mở lại ứng dụng trước khi nhận tiếp.
5. Tắt quyền thông báo trong Cài đặt, gửi lại và xác nhận không hiện thông báo,
   ứng dụng vẫn hoạt động.

## Payload và giới hạn

- Khi app đang mở, `BloomyMessagingService` hiển thị notification payload và cả
  data payload có khóa `title`/`body`. Nội dung notification được ưu tiên; thiếu
  tiêu đề dùng `Bloomy Beauty`; payload không có nội dung hiển thị được bỏ qua.
- Khi app chạy nền, FCM tự hiển thị notification payload trên cùng kênh/icon.
  Data-only đi qua service khi hệ điều hành cho phép; không đảm bảo giao ngay
  trong Doze. Không gửi data-only để yêu cầu hiển thị khẩn cấp.
- Chạm thông báo mở màn hình hiện tại của app; chưa có điều hướng tới đơn hàng.
- Chưa có backend gửi thông báo tự động khi trạng thái đơn hàng thay đổi, hoặc
  liên kết token với tài khoản. Có thể gửi thử trực tiếp đến thiết bị từ Console.
  Không nhúng service-account key hoặc thông tin gửi FCM vào APK.

## Kiểm thử tự động

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.bloomybeauty.notifications.PushNotificationsTest
```

Ca từ chối quyền chạy riêng trên API 33+ sau bộ kiểm thử trên (APK app và test
cần cài lại vì Gradle gỡ chúng sau connected tests):

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm revoke com.example.bloomybeauty android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e class com.example.bloomybeauty.notifications.PushPermissionDeniedTest \
  com.example.bloomybeauty.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm grant com.example.bloomybeauty android.permission.POST_NOTIFICATIONS
```

Cần JDK 17+; emulator kiểm thử có thể bị Gradle cài/gỡ ứng dụng. Bộ kiểm thử gồm
6 ca JVM cho payload và 4 ca Android cho kênh, hiển thị/nút mở app, service nhận
FCM data và chặn thông báo. Kiểm thử Android cấp quyền thông báo cho 3 ca đầu; ca thứ tư chạy riêng sau
khi thu hồi quyền thật bằng ADB. Chỉ chạy trên thiết bị kiểm thử.
Ca từ chối quyền được bỏ qua nếu quyền chưa bị thu hồi.
Các ca tự động không gửi qua máy chủ FCM; dùng quy trình Console ở trên để xác
nhận giao nhận qua mạng.

Tham khảo: [Thiết lập FCM Android](https://firebase.google.com/docs/cloud-messaging/android/get-started),
[Nhận tin nhắn](https://firebase.google.com/docs/cloud-messaging/android/receive-messages).
