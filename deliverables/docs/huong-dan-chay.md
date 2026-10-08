# Hướng dẫn chạy và build — Bloomy Beauty

Bản tài liệu 08/10/2026. Ứng dụng Android Kotlin/Compose, dữ liệu SQLite trên một thiết bị. Đặt COD là mô phỏng, không gửi đơn tới Cocoon. Báo cáo kiểm thử và giới hạn: [kế hoạch, mục 23](ke-hoach-de-tai.md#23-phase-12--kiểm-thử-và-ổn-định-bản-cục-bộ).

## 1. Cài APK trình diễn

APK được đóng gói tại `deliverables/bloomy-beauty-demo.apk`, package `com.example.bloomybeauty.demo`. Cài trên Android 9/API 28 trở lên; thiết bị đã chạy kiểm thử là Pixel 6 AVD Android 16/API 36. Android API 28 và thiết bị nộp thực tế còn cần nghiệm thu.

```sh
adb install -r deliverables/bloomy-beauty-demo.apk
adb shell am start -n com.example.bloomybeauty.demo/com.example.bloomybeauty.MainActivity
```

Nếu giải nén ZIP bàn giao, APK ở ngay thư mục gốc của bộ đó; dùng `adb install -r bloomy-beauty-demo.apk`. Các lệnh `deliverables/...` trong tài liệu áp dụng từ thư mục dự án. Muốn build, giải nén ZIP mã nguồn vào thư mục riêng trước.

Hoặc chuyển APK tới thiết bị và mở bằng trình cài ứng dụng. Gói `.demo` cài cạnh gói gốc và gói `.qa`, không dùng chung dữ liệu. Bản demo ký bằng khóa debug và có tài khoản quản trị mẫu. Tám sản phẩm/ảnh được ghi sẵn khi tạo SQLite, mỗi sản phẩm khởi tạo 30 đơn vị.

| Tài khoản | Email | Mật khẩu | Chuẩn bị |
|---|---|---|---|
| Quản trị mẫu | `admin@bloomy.demo` | `BloomyDemo123!` | Tự tạo lần mở database đầu trên APK `.demo` |
| Khách trình diễn | `khach@bloomy.demo` | `KhachDemo123!` | Đăng ký qua giao diện, tên “Khách trình diễn” |

Đây là tài khoản công khai dành cho dữ liệu trình diễn. `.local/admin-demo.properties` của bản phát triển riêng không được đóng gói. Nếu email admin đã tồn tại là CUSTOMER, bootstrap không tự nâng quyền; hãy dùng một cài đặt demo mới trên thiết bị thử nghiệm. Cài đè APK giữ tài khoản/giỏ/đơn cũ, không tự nạp lại tồn ban đầu.

## 2. Build từ mã nguồn

Dùng Android Studio, SDK Platform 35, Build Tools 35.0.0, JDK 17 tương thích AGP 8.9.1; mã Kotlin/Java đích JVM 11. Gradle wrapper đi kèm; không cần cài Gradle hệ thống. Cho Android Studio đồng bộ dự án, chọn emulator/thiết bị API 28+. `local.properties` do máy người chạy tạo để trỏ tới SDK, không được đóng gói.

```sh
python3 scripts/create-demo-admin.py --presentation
./gradlew :app:assembleDebug -Ppresentation=true
```

APK lúc này ở `app/build/outputs/apk/debug/app-debug.apk`, package `.demo`, version `1.0-demo`. Script tạo cấu hình demo riêng và không ghi đè file đã có. Email/hash/salt được đưa vào BuildConfig debug; mật khẩu rõ không đưa vào BuildConfig. Có thể dùng `--offline` nếu máy đã tải đủ Gradle/SDK/dependency. Máy mới cần mạng khi tải dependency lần đầu.

Build bản phát triển với admin ngẫu nhiên riêng:

```sh
python3 scripts/create-demo-admin.py
./gradlew :app:assembleDebug
```

Mật khẩu bản này nằm trong `.local/admin-demo.properties` trên máy người build; khác mật khẩu APK `.demo`. Không thay vai trò trực tiếp từ giao diện đăng ký.

## 3. Release và QA

```sh
./gradlew :app:assembleRelease :app:lintRelease
./gradlew :app:assembleRelease :app:lintRelease -PqaRelease=true
```

Lệnh đầu tạo `app-release-unsigned.apk`, package gốc: chưa cài được cho tới khi cấu hình khóa ký. Lệnh thứ hai tạo `app-release.apk`, package `.qa`, version `1.0-qa`, `DEBUG=false` và ký bằng khóa debug để nghiệm thu. Release không tự tạo admin demo, kể cả có cờ `presentation`. Sao chép APK QA trước khi build release thường vì hai lệnh dùng cùng thư mục output release. Gói QA kèm bàn giao ở `deliverables/bloomy-beauty-release-qa.apk` để thử luồng khách/cold start.

## 4. Kiểm thử

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Chạy connected tests trên emulator chuyên kiểm thử: Gradle cài/gỡ các gói ứng dụng phục vụ instrumentation. Không dùng thiết bị đang giữ dữ liệu cần bảo toàn. Fixture dùng database riêng; APK trình diễn `.demo` và QA `.qa` cũng có package riêng với gói được kiểm thử.

Kết quả Phase 12: 24 JVM + 81 Android = 105 ca đạt. Báo cáo sinh ở `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/`, `app/build/reports/lint-results-debug.html`. Min SDK 28 mới là cấu hình, chưa phải bằng chứng chạy trên API 28.

## 5. Kịch bản nghiệm thu 3–5 phút

1. Đăng ký khách → tìm “gel bi dao” → mở Gel bí đao rửa mặt.
2. Thêm 1 sản phẩm → mở giỏ → nhập người nhận, `0912345678`, địa chỉ hợp lệ ≥10 ký tự.
3. Kiểm tra COD: tạm tính 192.000 ₫ + phí 30.000 ₫ = 222.000 ₫ → xác nhận một lần.
4. Xem mã đơn/trạng thái Chờ xác nhận. Tồn giảm từ 30 xuống 29 với database mới.
5. Tài khoản → đăng xuất → admin đăng nhập → Quản trị → Đơn hàng → mở đơn.
6. Xác nhận → bắt đầu giao → đánh dấu đã giao. Mỗi lần lưu có lịch sử người thao tác/thời gian.
7. Đăng xuất admin → khách đăng nhập → Đơn hàng của tôi: Đã giao, không còn nút hủy.
8. Nếu còn thời gian: khách tạo đơn thứ hai rồi hủy khi Chờ xác nhận, tồn được hoàn đúng một lần.

Tên nút và minh chứng chi tiết: [kịch bản trình diễn](trinh-dien/kich-ban.md). Không dùng tồn/đơn của database cũ để khẳng định con số khởi tạo 30.

Video đã quay thực tế: [MP4 4 phút 10 giây](trinh-dien/bloomy-beauty-demo.mp4), có phụ đề tiếng Việt, không có thuyết minh âm thanh. Video cắt một đoạn chờ và tăng tốc thao tác 1,6 lần; giữ thứ tự luồng khách/admin/khách. File `.srt` đi cùng để sửa phụ đề. Mã nguồn ZIP bỏ video; bộ ZIP bàn giao đầy đủ có MP4 và phụ đề. Bản ghi thô ở `.local/recordings` trên máy chuẩn bị, không đóng gói. `scripts/render-video.py` chỉ mã hóa lại khi có đủ các bản ghi thô và chapters tương ứng.

## 6. Khắc phục thường gặp

| Tình huống | Cách xử lý |
|---|---|
| Không thấy nút quản trị | Đúng APK `.demo` và đăng nhập `admin@bloomy.demo`; tài khoản đăng ký thường là CUSTOMER |
| Admin mật khẩu công khai không đăng nhập được trên bản gốc | Bản gốc dùng file admin ngẫu nhiên riêng; cài APK `.demo` |
| Gradle offline thiếu dependency | Chạy một lần không `--offline` trên máy có mạng |
| SDK location not found | Mở Android Studio/cấu hình `local.properties` theo SDK của máy |
| Giá/tồn khác ảnh báo cáo | Database lưu các thay đổi demo; đối chiếu trạng thái hiện tại, không cài đè để reset |
| Sản phẩm đổi giá khi đang kiểm tra | Xác nhận giá mới tại giỏ rồi kiểm tra lại; không gửi yêu cầu cũ |
| Đơn đã xác nhận không thể hủy bằng khách | Đúng quy tắc: khách chỉ hủy PENDING; admin có thể hủy PENDING/CONFIRMED |
| Gỡ app rồi không thấy tài khoản/đơn | Dữ liệu SQLite cục bộ; database đã loại khỏi backup, chưa có đồng bộ/khôi phục riêng |

## 7. Đóng gói lại

```sh
python3 scripts/package-submission.py
```

Script yêu cầu APK demo và tài liệu đã có trong `deliverables/`; tạo zip mã nguồn, manifest SHA-256 và zip bàn giao. Bỏ cache, `local.properties`, cấu hình admin riêng và thư mục `.local`. Sửa tài liệu rồi chạy lại script để checksum/zip theo đúng bản cuối. Không có commit Git trong workspace hiện tại; `source-manifest.json` dùng hash từng file để xác định phiên bản bàn giao.
