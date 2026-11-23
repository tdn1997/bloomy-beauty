# Kiểm thử Chia sẻ sản phẩm — 16/11/2026

Đã triển khai và nghiệm thu trên Pixel_6 Android 15/API 35 (ARM64), bản demo
`com.example.bloomybeauty.demo`. Tạo ảnh hoàn toàn từ SQLite và ảnh đóng gói;
không cần Firebase hoặc kết nối mạng. Không thay schema 11.

## Kết quả

| Nhóm | Kết quả | Phạm vi |
|---|---|---|
| Build demo và AndroidTest | Đạt | `assembleDebug`, `assembleDebugAndroidTest`, JDK 21 |
| JVM | 76/76 đạt, 0 bỏ qua | Bộ unit hiện có |
| ProductImageExporterTest | 8/8 đạt | PNG, MIME, URI/grant, phiên, giá, cache, lỗi output |
| ProductShareFlowTest | 4/4 đạt | Chi tiết → preview → Sharesheet → Back, hủy, thay giá, recreate |
| Hồi quy Android | 11/11 đạt | OrderPdfExporterTest (8), HomeFlowTest (2), ComparisonIntegrationTest (1) |
| lintDebug | 0 lỗi | 73 cảnh báo, 1 thông tin; thêm 2 gợi ý UseKtx ở Canvas/Bitmap |

Tổng Android liên quan: **23/23 đạt, 0 bỏ qua**. Đây là bộ kiểm thử chọn lọc cho
thay đổi này, chưa chạy toàn bộ connected suite. Runner xác nhận đúng AVD Pixel_6
và khôi phục thiết lập animation sau mỗi nhóm. Dùng cùng AVD ở chế độ headless
để chạy kiểm thử, sau đó mở lại cửa sổ để sử dụng.

## Điều đã xác nhận

- PNG rộng 1080 px, chiều cao theo nội dung; đọc được qua ContentResolver.
  Chuỗi tiếng Việt dài bị giới hạn dòng, ảnh nguồn không hợp lệ có placeholder.
- Intent `ACTION_SEND`, `image/png`, EXTRA_STREAM và ClipData cùng URI;
  chỉ có quyền đọc. Provider không exported và không mở được file ngoài thư mục.
- File UUID không trùng; hủy preview xóa file; ảnh từ 24 giờ được dọn ở lần
  xuất tiếp theo; đăng xuất/đổi tài khoản xóa cache và thu hồi quyền URI.
- Sản phẩm mất/ngừng bán hoặc phiên sai bị từ chối. Giá đổi sau preview yêu cầu
  tạo lại. Hết hàng vẫn tạo được ảnh có nhãn phù hợp. Lỗi ghi file có thể thử lại.
- Mở Sharesheet thật và dùng Back hệ thống quay về app; không đổi giỏ hoặc đơn.
  Xoay/recreate đóng preview chưa chia sẻ, không tự mở Sharesheet.
- Chia sẻ phiếu PDF vẫn hoạt động qua provider riêng hiện có.

## Ảnh thật

Các ảnh poster 01–03 được xuất bởi kiểm thử với dữ liệu fixture và thời gian cố
định; 04–07 là ảnh chụp màn hình trên Pixel_6, dùng dữ liệu SQLite test độc lập.
Không phải dữ liệu khách hàng thực.

- [01 — PNG sản phẩm](pixel6-product-share/01-cleanser.png)
- [02 — Hết hàng](pixel6-product-share/02-sold-out.png)
- [03 — Tiếng Việt dài và placeholder](pixel6-product-share/03-long-vietnamese.png)
- [04 — Nút tại chi tiết](pixel6-product-share/04-detail-action.png)
- [05 — Xem trước](pixel6-product-share/05-preview.png)
- [06 — Android Sharesheet](pixel6-product-share/06-android-sharesheet.png)
- [07 — Yêu cầu tạo lại khi giá đổi](pixel6-product-share/07-changed-product.png)

## Chạy lại

Dùng JDK 21 và ghi đè `org.gradle.java.home` nếu cấu hình toàn cục còn trỏ JDK 25.
Build/cài APK demo cùng APK AndroidTest, sau đó:

```sh
python3 scripts/run-pixel6-tests.py --classes com.example.bloomybeauty.export.ProductImageExporterTest --log docs/kiem-thu/product-share-2026-11-16/exporter-final.log --disable-animations
python3 scripts/run-pixel6-tests.py --classes com.example.bloomybeauty.feature.export.ProductShareFlowTest --log docs/kiem-thu/product-share-2026-11-16/ui-acceptance.log --disable-animations
python3 scripts/run-pixel6-tests.py --classes com.example.bloomybeauty.export.OrderPdfExporterTest,com.example.bloomybeauty.feature.home.HomeFlowTest,com.example.bloomybeauty.feature.home.ComparisonIntegrationTest --log docs/kiem-thu/product-share-2026-11-16/regression.log --disable-animations
```

Log/raw XML và environment JSON được giữ cục bộ theo .gitignore; Markdown/PNG
là hồ sơ trong repository. Lượt đầu có lỗi harness do hàm JUnit trả về File thay
vì Unit và Espresso Back khi native chooser giữ foreground; đã sửa chữ ký Unit
và dùng UiAutomation Back, chạy lại đạt. Ảnh Sharesheet chờ UI idle trước chụp.

Không chọn ứng dụng/người nhận và không gửi dữ liệu trong kiểm thử. Chưa xác
nhận nhận ảnh trên Zalo/Messenger hoặc điện thoại thật/API 28. Android Sharesheet
được mở thành công không đồng nghĩa đã gửi ảnh; ảnh đã mở Sharesheet được giữ
trong cache để bên nhận đọc bất đồng bộ, hệ điều hành vẫn có thể dọn cache.

[Hướng dẫn](../../20261116000000_chia-se-san-pham.md) ·
[Kế hoạch](../../20261116000000_ke-hoach-chia-se-san-pham.md)
