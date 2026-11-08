# Firebase Auth và quyền server — mốc B0/B1

**Tài liệu tham khảo, ngoài phạm vi demo môn học đã chốt ngày 08/11/2026.** Không cần chạy các dịch vụ này để sử dụng hoặc trình diễn bản `.demo`; không tiếp tục triển khai B2–B4 hay deploy BE. Phần đã làm được giữ để tra cứu và tái lập minh chứng.

Bản kiểm thử `com.example.bloomybeauty.cloud`, tên **Bloomy Beauty Cloud (test)**, hiện có đăng ký/đăng nhập Firebase, xem tài khoản/vai trò đã xác minh từ backend, đăng xuất, đổi tài khoản và khôi phục phiên. SDK Auth, Firestore và Functions chạy cùng project `demo-bloomy-commerce` trên máy tính; Android chỉ dùng Pixel_6 đã có. Các dịch vụ Emulator Suite không tạo thêm máy Android.

Bản này là mốc danh tính và phân quyền. Giao dịch server B2, đồng bộ B3 và nghiệm thu live B4 là thiết kế tham khảo đã loại khỏi phạm vi. `.demo`, `.qa` và bản thường hiện vẫn dùng nghiệp vụ SQLite; APK/ZIP đã bàn giao A7 vẫn là snapshot local. Source Cloud chỉ được chọn ở lúc build, không có thao tác đổi nguồn giữa checkout.

## Chạy

Dùng Node 20 và JDK 21 (JBR của Android Studio trên máy này phù hợp). Firebase CLI nằm trong workspace, được khóa bởi package-lock.

```sh
npm ci --prefix firebase-tests
npm ci --prefix functions
scripts/run-cloud-emulators.sh
```

Ở terminal khác:

```sh
node functions/scripts/seed-emulator.cjs
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug -PcloudEmulator=true
/Users/thuan/Library/Android/sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Fixture công khai chỉ trong demo project: `customer@bloomy.cloud.test` và `admin@bloomy.cloud.test`, mật khẩu `CloudDemo123!`. Seed không thay mật khẩu đã có, và chỉ thao tác emulator trên loopback với project cố định. Source `.cloud` không đọc cấu hình/hash quản trị local. Dữ liệu emulator export/import nằm ở `.local/cloud-emulator-data`, ngoài Git và ZIP nguồn. Khi khởi động lại bằng script, phiên thử nghiệm có thể tiếp tục dùng dữ liệu đã export; SDK vẫn cần backend xác minh tài khoản.

| Dịch vụ | Máy tính | Android Pixel_6 |
|---|---|---|
| Auth | 127.0.0.1:9099 | 10.0.2.2:9099 |
| Firestore | 127.0.0.1:8080 | 10.0.2.2:8080 |
| Functions | 127.0.0.1:5001 | 10.0.2.2:5001 |

Project demo, host và region `us-central1` được đặt cố định trong factory kiểm thử. Release variant bị loại khi bật `cloudEmulator`; cấu hình network debug chỉ cho HTTP tới 10.0.2.2. `FIREBASE_ENABLED` cũ vẫn chỉ điều khiển feed/FCM của bản local; `CLOUD_EMULATOR` điều khiển nguồn Auth/Account mới.

## Hợp đồng và quyền

`CloudAuthGateway` xuất `CloudSession(uid, nonce)` và token của phiên hiện hành. Firebase UID giữ nguyên dạng chuỗi; không ép sang ID SQLite, không tra/gộp theo email. Nonce đổi khi đổi phiên hoặc tạo lại gateway; repository kiểm tra UID/nonce trước token, trước gọi và sau nhận kết quả để loại response cũ.

`CloudAccounts.current` gọi HTTPS callable qua `CloudApi`; token được truyền bằng Authorization. Backend xác minh Auth user còn tồn tại/không bị vô hiệu hóa và đọc quyền từ `roles/{uid}` trong transaction. UI chỉ nhận account sau khi server trả thành công; listener role chỉ báo cần tải lại, không dùng dữ liệu cache hay write đang chờ để cấp quyền. Lỗi server ẩn account đã tải; đăng xuất/đổi UID hủy listener và xóa account của phiên cũ.

| Callable B1 | Input | Kết quả và nguồn quyền |
|---|---|---|
| accountInfo | `{}` | UID/email/name từ Auth, role từ document server; bootstrap users/{uid} một lần |
| verifyAdmin | `{}` | Chỉ ADMIN hiện hành; đọc lại role với cùng ID token sau khi grant/revoke |

Hai API từ chối input UID/role tự truyền. Firestore rules cho chủ đọc đúng profile/role của mình; client không ghi hai loại document này hoặc liệt kê toàn bộ người dùng. Roles chỉ được môi trường tin cậy ghi; công cụ seed và thao tác quản trị đặc biệt trong test APK chỉ phục vụ emulator. Chúng không nằm trong API ứng dụng hay cho phép client production tự nâng quyền.

Các bảng/đơn/hash local được giữ trong bản local. Kiểm thử tạo local account có cùng email rồi thử đăng nhập Cloud phải thất bại; sau đăng ký Firebase tường minh, UID mới không thay phiên hoặc đơn local. Chưa có chức năng nhập hồ sơ/sổ địa chỉ; nếu bổ sung cần chứng minh cả hai phiên, không dùng email làm bằng chứng sở hữu. Đơn local không được upload như doanh thu server.

Care Provider và hai receiver lịch local bị tắt trong manifest `.cloud`, có guard trong code chạy nền. Bản local giữ các thành phần này bật. MainActivity Cloud và cold start đã kiểm tra không tạo database bán hàng `bloomy_beauty.db`.

## Kiểm thử và phạm vi online

```sh
node --test functions/test/identity.test.cjs
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node --test firebase-tests/firestore.rules.test.mjs
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebugAndroidTest -PcloudEmulator=true
```

Cài đè APK app/test `.cloud`, sau đó dùng `scripts/run-pixel6-tests.py --package com.example.bloomybeauty.cloud` với hai lớp FirebaseCloudIdentityTest và CloudAccountScreenTest. Cold-start scenario chạy hai lần bằng `--arg cloudScenario=prepare` và `--arg cloudScenario=inspect`, có force-stop/launch thật ở giữa. Các ca fixture xóa đúng UID test đã tạo; tài khoản public seed được giữ để xem lại. [Log, ảnh đã xem và manifest B0/B1](kiem-thu/pixel6-cloud-identity-2026-11-08/README.md).

Chưa triển khai Functions/rules/Auth mới lên project live. Để chạy online cần kiểm chứng project/application ID, bật provider Email/Password, quyền triển khai và cấu hình Functions/Firestore; Functions deploy cần Blaze theo [hướng dẫn Firebase](https://firebase.google.com/docs/functions/get-started). Các điều kiện này chỉ để tham khảo; không có mốc online hoặc yêu cầu triển khai BE cho demo môn học.

Tham khảo cách tích hợp từ [Firebase Auth Android](https://firebase.google.com/docs/auth/android/start), [kết nối Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth), [callable protocol](https://firebase.google.com/docs/functions/callable) và [Admin quản lý tài khoản](https://firebase.google.com/docs/auth/admin/manage-users). Bản build dùng BoM 34.7.0 sẵn có cho Kotlin 2.0; không tự đổi lên BoM mới chỉ vì ví dụ tài liệu dùng phiên bản khác.
