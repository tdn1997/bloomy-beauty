# Bloomy Beauty — Ứng dụng bán mỹ phẩm

Ứng dụng Android Kotlin/Jetpack Compose, SQLite schema 5. Khách đăng ký/đăng nhập, xem/tìm/yêu thích sản phẩm, giỏ/giao hàng/COD, lịch sử/hủy đơn; admin quản lý sản phẩm và xử lý trạng thái. Tám sản phẩm/ảnh tham khảo Cocoon đóng gói ngoại tuyến. Giá tham khảo ngày 07/10/2026; đơn và tồn là demo trên một thiết bị.

- [Kế hoạch và tiến độ](docs/ke-hoach-de-tai.md)
- [Hướng dẫn cài/chạy/build và tài khoản demo](docs/huong-dan-chay.md)
- [Báo cáo kỹ thuật](docs/bao-cao/bao-cao-de-tai.md) · [Bản HTML để in](docs/bao-cao/bao-cao-de-tai.html)
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

APK đã đóng gói: `deliverables/bloomy-beauty-demo.apk`; release QA khách/cold start: `deliverables/bloomy-beauty-release-qa.apk`. Zip bàn giao ở `deliverables/bloomy-beauty-ban-giao.zip`, checksum và source manifest trong cùng thư mục. Zip mã nguồn không chứa `.local`, SDK path hoặc Gradle cache.

## Kiểm tra

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Chạy connected tests trên emulator dùng cho kiểm thử vì Gradle cài/gỡ gói app. Phase 12: 24 JVM + 81 Android = 105 ca đạt trên API 36. Cấu hình min SDK 28 chưa thay cho nghiệm thu runtime API 28. Chưa có backend/đồng bộ kho nhiều thiết bị/thanh toán online. Thông tin nhóm, lớp, giảng viên và phân công thực tế trong báo cáo còn chờ điền trước nộp.

Đóng gói lại: chuẩn bị APK đúng như hướng dẫn, chạy `python3 scripts/render-docs.py` khi sửa báo cáo/sơ đồ, rồi `python3 scripts/package-submission.py`. Mở báo cáo HTML/slide và In → Lưu PDF nếu cần định dạng PDF của học phần.
# bloomy-beauty
