# Bloomy Beauty — Ứng dụng bán mỹ phẩm

Ứng dụng Android Kotlin/Jetpack Compose, SQLite schema 11. Khách đăng ký/đăng nhập, xem/tìm/yêu thích sản phẩm, giỏ/giao hàng/COD, sổ địa chỉ, áp mã mẫu trong bản demo/QA, lịch sử/hủy/mua lại đơn, đánh giá sau khi giao hàng, lịch chăm sóc da và nhắc hẹn cục bộ; admin quản lý sản phẩm, xử lý trạng thái và xem thống kê. Có phiếu PDF và hộp thông báo đơn hàng theo tài khoản. Tám sản phẩm/ảnh tham khảo Cocoon đóng gói ngoại tuyến. Giá tham khảo ngày 07/10/2026; đơn và tồn là demo trên một thiết bị.

**Phạm vi chốt: demo môn học trên một thiết bị, không yêu cầu triển khai backend riêng.** Dùng APK `.demo` để trình diễn đủ sáu tính năng; phần Cloud/Auth Emulator đã thử nghiệm chỉ là tài liệu tham khảo, không phải bước bắt buộc để chạy hoặc nghiệm thu.

- [Kế hoạch và tiến độ](docs/20261007000000_ke-hoach-de-tai.md)
- [So sánh sản phẩm](docs/20261116000000_so-sanh-san-pham.md) · [Kế hoạch](docs/20261116000000_ke-hoach-so-sanh-san-pham.md) · [Kiểm thử và ảnh](docs/kiem-thu/comparison-2026-11-16/README.md) — 76 unit và 21 Android test liên quan đạt.
- [Chia sẻ sản phẩm bằng ảnh](docs/20261116000000_chia-se-san-pham.md) · [Kế hoạch](docs/20261116000000_ke-hoach-chia-se-san-pham.md) · [Kiểm thử và ảnh](docs/kiem-thu/product-share-2026-11-16/README.md) — tạo PNG ngoại tuyến, xem trước và mở Android Sharesheet.
- [Pixel_6 tạo lại sau nâng cấp macOS](docs/20261116000000_pixel6-moi-2026-11-16.md)
- [Mua lại đơn cũ](docs/20261116000000_mua-lai-don-cu.md) · [Kế hoạch](docs/20261116000000_ke-hoach-mua-lai-don-cu.md) · [Kiểm thử ngày 16/11/2026](docs/kiem-thu/reorder-2026-11-16/README.md) — hoàn tất; 71 unit và 57 Android đạt trên Pixel_6 API 35.
- [Danh sách tính năng phát triển sau](docs/20261116000000_tinh-nang-phat-trien-sau.md)
- [Kế hoạch sáu tính năng mở rộng và đồng bộ Firebase](docs/20261107000000_ke-hoach-mo-rong-ban-hang.md)
- [Kế hoạch bổ sung Content Provider và Broadcast Receiver](docs/20261107000000_ke-hoach-content-provider-broadcast.md)
- [Sử dụng lịch chăm sóc, nhắc hẹn và broadcast đơn hàng](docs/20261107000000_lich-cham-soc.md)
- [Áp dụng mã giảm giá demo](docs/20261108000000_ma-giam-gia-demo.md)
- [Kiểm thử mã giảm giá trên Pixel 6](docs/kiem-thu/pixel6-coupon-2026-11-08/README.md)
- [Sử dụng sổ địa chỉ và bản nháp giao hàng](docs/20261108000000_so-dia-chi.md)
- [Kiểm thử sổ địa chỉ trên Pixel 6](docs/kiem-thu/pixel6-address-2026-11-08/README.md)
- [Kiểm thử đánh giá sản phẩm trên Pixel 6](docs/kiem-thu/pixel6-reviews-2026-11-07/README.md)
- [Kiểm thử lịch chăm sóc trên Pixel 6 và ảnh minh chứng](docs/kiem-thu/pixel6-care-2026-11-07/README.md)
- [Nghiệm thu trên giả lập nhiều API và deep Doze](docs/kiem-thu/content-broadcast-emulator-2026-11-07/README.md)
- [Hướng dẫn cài/chạy/build và tài khoản demo](docs/20261008000000_huong-dan-chay.md)
- [Báo cáo kỹ thuật](docs/bao-cao/bao-cao-de-tai.md) · [Bản HTML để in](docs/bao-cao/bao-cao-de-tai.html)
- [Báo cáo tiến độ lần 2 — Nhóm 4 (Word)](docs/bao-cao/tien-do-lan-2/Bao-cao-tien-do-lan-2-Nhom-4.docx)
- [Sơ đồ SQLite và luồng nghiệp vụ](docs/bao-cao/so-do.md)
- [Slide ngoại tuyến 10 trang](docs/trinh-dien/slides.html) · [Kịch bản và câu hỏi bảo vệ](docs/trinh-dien/kich-ban.md)
- [Video demo 4 phút 10 giây, có phụ đề](docs/trinh-dien/bloomy-beauty-demo.mp4) — có trong ZIP bàn giao, không nằm trong ZIP mã nguồn.
- [Nguồn sản phẩm/ảnh](docs/20261007000000_product-sources.md)

## Chạy nhanh bản trình diễn

```sh
python3 scripts/create-demo-admin.py --presentation
./gradlew :app:assembleDebug -Ppresentation=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Package `com.example.bloomybeauty.demo`, version `1.0-demo`, có dữ liệu riêng. Admin công khai: `admin@bloomy.demo` / `BloomyDemo123!`. Khách tự đăng ký `khach@bloomy.demo` / `KhachDemo123!`. Bản debug gốc dùng admin ngẫu nhiên riêng; release không tự tạo admin.

Bộ bàn giao cập nhật 08/11/2026 có đủ sáu tính năng local, SQLite schema 11. [Kết quả hồi quy và ảnh Pixel_6](docs/kiem-thu/pixel6-commerce-integration-2026-11-08/README.md). Bản thường đã build với coupon local tắt; demo/QA bật mã mẫu. Backend giao dịch/đồng bộ nhiều thiết bị không thuộc phạm vi demo đã chốt.

APK đã đóng gói: `deliverables/bloomy-beauty-demo.apk`; release QA khách/cold start: `deliverables/bloomy-beauty-release-qa.apk`. Zip bàn giao ở `deliverables/bloomy-beauty-ban-giao.zip`, checksum và source manifest trong cùng thư mục. Zip mã nguồn không chứa `.local`, SDK path hoặc Gradle cache.

## Firebase tùy chọn

Thử nghiệm tham khảo B0/B1 đã có biến thể **Bloomy Beauty Cloud (test)** (`-PcloudEmulator=true`): Firebase Auth, phiên theo UID và quyền server trên Emulator Suite. [Cách chạy và giới hạn](docs/20261108000000_firebase-auth-emulator.md). [Bằng chứng Pixel_6](docs/kiem-thu/pixel6-cloud-identity-2026-11-08/README.md).

Firebase is configured for the normal `com.example.bloomybeauty` debug/release builds.
The checked-in config template is `app/google-services.json.template`; the API key
comes from the `FIREBASE_API_KEY` environment variable or the ignored root `.env.local`
file. The environment variable takes precedence. For local Android Studio builds:

```sh
cp .env.example .env.local
# Edit .env.local and set FIREBASE_API_KEY to your Firebase Android API key (no quotes).
./gradlew :app:assembleDebug
```

For CI, set `FIREBASE_API_KEY` using the CI secret store. Gradle generates
`app/build/generated/firebase/google-services.json`, then the Google Services plugin
generates the resources used to initialize Firebase automatically at startup. Firebase
Analytics, Cloud Messaging, and Firestore are included, with SDK versions managed by the Firebase BoM.
See [Firestore realtime: flash sale và mã khuyến mãi](docs/20261028000000_firestore-realtime.md) for schema, rules, and setup.
See [Thông báo đẩy và cách gửi thử](docs/20261028000000_thong-bao-day.md) for setup and tests.
BoM `34.7.0` is pinned for compatibility with the project's Kotlin `2.0.21` compiler.

Sync Gradle in Android Studio or run `./gradlew :app:assembleDebug` using JDK 17+.
Với Gradle 8.11.1 hiện tại, dùng JDK 21 để chạy các lệnh kiểm thử bên dưới;
JDK 25 đi kèm Android Studio mới chưa chạy được cấu hình này. Nếu
`~/.gradle/gradle.properties` đặt `org.gradle.java.home`, truyền thêm
`-Dorg.gradle.java.home=/duong-dan/JDK21/Contents/Home` để chọn đúng JDK cho Gradle,
ngoài việc đặt `JAVA_HOME`.
The `-Ppresentation=true` and `-PqaRelease=true` builds skip Google Services config
processing and run without Firebase: their `.demo` and `.qa` package IDs are not
registered in the supplied config. Register those Android apps in Firebase and
add matching variant configs before enabling Firebase for those builds.

Do not commit `.env.local` or a downloaded `google-services.json`. The API key is
still included in the APK, as required by Firebase's client SDKs. Restrict its API
usage in Google Cloud and protect data with Firebase Security Rules; see
[Firebase API key guidance](https://firebase.google.com/docs/projects/api-keys).
The previously committed key remains in Git history. If Google flagged it,
review its restrictions and replace it in Google Cloud and `.env.local` as needed;
moving it to local configuration does not revoke it or resolve an existing alert.

Normal, demo and QA builds retain local SQLite authentication and commerce. The opt-in `.cloud` debug build uses Firebase Authentication and server-owned roles in an isolated demo project. Firestore promotion feeds remain advertising; demo/QA checkout coupons have separate local authority. Cloud orders/stock/coupon transactions are outside the agreed coursework demo scope; B2–B4 are not required deliverables.

## Kiểm tra

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Chạy connected tests trên emulator dùng cho kiểm thử vì Gradle cài/gỡ gói app. Phase 12: 24 JVM + 81 Android = 105 ca đạt trên API 36. Cấu hình min SDK 28 chưa thay cho nghiệm thu runtime API 28. Backend riêng, đồng bộ kho nhiều thiết bị và thanh toán online nằm ngoài phạm vi demo môn học. Thông tin nhóm, lớp, giảng viên và phân công thực tế trong báo cáo còn chờ điền trước nộp.

Đóng gói lại: chuẩn bị APK đúng như hướng dẫn, chạy `python3 scripts/render-docs.py` khi sửa báo cáo/sơ đồ, rồi `python3 scripts/package-submission.py`. Mở báo cáo HTML/slide và In → Lưu PDF nếu cần định dạng PDF của học phần.

- [Thống kê quản trị](docs/20261108000000_thong-ke-ban-hang.md)
- [Phiếu đơn hàng PDF](docs/20261108000000_phieu-don-hang-pdf.md)
- [Trung tâm thông báo](docs/20261108000000_trung-tam-thong-bao.md)

## Cập nhật buổi 09/11/2026

Hoàn thiện Yêu thích: lối vào từ Tài khoản, bộ lọc riêng, thêm nhanh giỏ và trạng thái hết hàng. SQLite schema 11, không cần BE. [Hướng dẫn](docs/20261109000000_san-pham-yeu-thich.md) · [Kế hoạch](docs/20261109000000_ke-hoach-yeu-thich.md) · [Bài tập cá nhân và ảnh](docs/bai-tap/san-pham-yeu-thich/README.md) · [Kiểm thử](docs/kiem-thu/pixel6-favorites-2026-11-09/README.md).

## Cập nhật buổi 16/11/2026

Hoàn thiện Mua lại đơn cũ: xem trước, gộp giỏ theo giá/tồn hiện tại, xác nhận lại
khi dữ liệu thay đổi và xử lý checkout nháp. Không đổi schema SQLite 11.
Build demo đạt; 71/71 unit test và 57/57 Android test liên quan đạt, lint 0 lỗi.
Đã tạo lại một Pixel_6 Android 15/API 35 sau nâng cấp macOS.
[Hướng dẫn](docs/20261116000000_mua-lai-don-cu.md) · [Kết quả và ảnh](docs/kiem-thu/reorder-2026-11-16/README.md).
APK mới: `deliverables/bloomy-beauty-demo-reorder-2026-11-16.apk`, có checksum
`.apk.sha256` cạnh bên. Các ZIP bàn giao ngày 08/11 là bộ lưu của đợt trước.
