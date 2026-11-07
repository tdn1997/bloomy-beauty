# Bloomy Beauty — Ứng dụng bán mỹ phẩm

Ứng dụng Android Kotlin/Jetpack Compose, SQLite schema 6. Khách đăng ký/đăng nhập, xem/tìm/yêu thích sản phẩm, giỏ/giao hàng/COD, lịch sử/hủy đơn, lịch chăm sóc da và nhắc hẹn cục bộ; admin quản lý sản phẩm và xử lý trạng thái. Tám sản phẩm/ảnh tham khảo Cocoon đóng gói ngoại tuyến. Giá tham khảo ngày 07/10/2026; đơn và tồn là demo trên một thiết bị.

- [Kế hoạch và tiến độ](docs/ke-hoach-de-tai.md)
- [Kế hoạch bổ sung Content Provider và Broadcast Receiver](docs/ke-hoach-content-provider-broadcast.md)
- [Sử dụng lịch chăm sóc, nhắc hẹn và broadcast đơn hàng](docs/lich-cham-soc.md)
- [Kiểm thử mới nhất trên Pixel 6 và ảnh minh chứng](docs/kiem-thu/pixel6-care-2026-11-07/README.md)
- [Nghiệm thu trên giả lập nhiều API và deep Doze](docs/kiem-thu/content-broadcast-emulator-2026-11-07/README.md)
- [Hướng dẫn cài/chạy/build và tài khoản demo](docs/huong-dan-chay.md)
- [Báo cáo kỹ thuật](docs/bao-cao/bao-cao-de-tai.md) · [Bản HTML để in](docs/bao-cao/bao-cao-de-tai.html)
- [Báo cáo tiến độ lần 2 — Nhóm 4 (Word)](docs/bao-cao/tien-do-lan-2/Bao-cao-tien-do-lan-2-Nhom-4.docx)
- [Sơ đồ SQLite và luồng nghiệp vụ](docs/bao-cao/so-do.md)
- [Slide ngoại tuyến 10 trang](docs/trinh-dien/slides.html) · [Kịch bản và câu hỏi bảo vệ](docs/trinh-dien/kich-ban.md)
- [Video demo 4 phút 10 giây, có phụ đề](docs/trinh-dien/bloomy-beauty-demo.mp4) — có trong ZIP bàn giao, không nằm trong ZIP mã nguồn.
- [Nguồn sản phẩm/ảnh](docs/product-sources.md)

## Chạy nhanh bản trình diễn

```sh
python3 scripts/create-demo-admin.py --presentation
./gradlew :app:assembleDebug -Ppresentation=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Package `com.example.bloomybeauty.demo`, version `1.0-demo`, có dữ liệu riêng. Admin công khai: `admin@bloomy.demo` / `BloomyDemo123!`. Khách tự đăng ký `khach@bloomy.demo` / `KhachDemo123!`. Bản debug gốc dùng admin ngẫu nhiên riêng; release không tự tạo admin.

Để thử lịch chăm sóc mới, dùng APK vừa build tại `app/build/outputs/apk/debug/app-debug.apk`. Các APK/ZIP trong `deliverables/` của đợt bàn giao trước cần được đóng gói lại để có tính năng này.

APK đã đóng gói: `deliverables/bloomy-beauty-demo.apk`; release QA khách/cold start: `deliverables/bloomy-beauty-release-qa.apk`. Zip bàn giao ở `deliverables/bloomy-beauty-ban-giao.zip`, checksum và source manifest trong cùng thư mục. Zip mã nguồn không chứa `.local`, SDK path hoặc Gradle cache.

## Firebase

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
See [Firestore realtime: flash sale và mã khuyến mãi](docs/firestore-realtime.md) for schema, rules, and setup.
See [Thông báo đẩy và cách gửi thử](docs/thong-bao-day.md) for setup and tests.
BoM `34.7.0` is pinned for compatibility with the project's Kotlin `2.0.21` compiler.

Sync Gradle in Android Studio or run `./gradlew :app:assembleDebug` using JDK 17+.
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

Authentication and app data still use the existing local SQLite repositories;
Firebase Authentication is not integrated. Firestore supplies public promotion campaigns; checkout discounts are not applied yet.

## Kiểm tra

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Chạy connected tests trên emulator dùng cho kiểm thử vì Gradle cài/gỡ gói app. Phase 12: 24 JVM + 81 Android = 105 ca đạt trên API 36. Cấu hình min SDK 28 chưa thay cho nghiệm thu runtime API 28. Chưa có backend/đồng bộ kho nhiều thiết bị/thanh toán online. Thông tin nhóm, lớp, giảng viên và phân công thực tế trong báo cáo còn chờ điền trước nộp.

Đóng gói lại: chuẩn bị APK đúng như hướng dẫn, chạy `python3 scripts/render-docs.py` khi sửa báo cáo/sơ đồ, rồi `python3 scripts/package-submission.py`. Mở báo cáo HTML/slide và In → Lưu PDF nếu cần định dạng PDF của học phần.
# bloomy-beauty
