# Kiểm thử Yêu thích — 09/11/2026

AVD **Pixel_6** hiện có, emulator-5554, Android API 36. Không tạo AVD mới; mở lại Pixel_6 vì lúc bắt đầu không có thiết bị online. Gói `com.example.bloomybeauty.demo`, cài đè `adb install -r`. SQLite schema 11, không cần BE.

## Kết quả cuối

- Android: **26/26 đạt**, 0 bỏ qua, 0 lỗi. [Log](acceptance.txt), [môi trường/khôi phục animation](acceptance.environment.json).
- JVM: **62 ca**, 0 failures, 0 errors, 0 skipped. XML trong `jvm/`.
- Lint debug: **0 error, 0 fatal, 68 warning, 1 information**. [XML](lint-results-debug.xml).
- [Manifest hash nguồn và APK đã kiểm thử](validation-manifest.json). Không chạy lại toàn bộ bộ 172 ca lịch sử; đây là 26 ca liên quan.

Nhóm chạy: FavoritesFlowTest (3), CatalogRepositoryTest (4), SessionIsolationTest, HomeFlowTest, CartRepositoryTest, CartFlowTest. Bao gồm lưu qua đóng/mở DB và đăng nhập lại, tách tài khoản, từ chối phiên cũ, sản phẩm xóa/ngừng bán/hết hàng; giao diện lưu/bỏ lưu, bộ lọc độc lập, lối vào tài khoản, thêm giỏ và hồi quy checkout/hủy đơn.

## Minh chứng

11 ảnh có tiền tố 01–11 chụp mới bằng UI Automation trong 3 ca FavoritesFlowTest. Dùng giao diện Compose thật, repository và SQLite thật, nhưng tài khoản/tồn kho là fixture trong `favorites_flow_test.db`, không phải dữ liệu khách hàng. Mỗi ca dọn DB riêng sau khi chạy. Ảnh của các ca độc lập được xếp theo luồng để báo cáo, không mô tả thành một phiên liên tục. Xem [bài tập cá nhân Sản phẩm yêu thích](../../bai-tap/san-pham-yeu-thich/README.md) với đầy đủ ảnh/chú thích.

Lần đầu: 25/26 đạt, 1 ca dừng vì test dùng tên “Tinh chất bí đao” thay cho tên seed “Tinh chất bí đao N7”. Sửa tên trong test, build lại test APK, chạy lại cả 26 ca đạt. Giữ [log ban đầu](initial-fixture-name.txt) và [metadata](initial-fixture-name.environment.json); không tính lần đầu như lỗi nghiệp vụ hoặc cộng dồn số ca.

Giới hạn: chỉ Pixel_6, thiết bị thật để sau. Kiểm tra persistence bằng đóng/mở helper và đăng nhập lại, không khẳng định đã kiểm thử khởi động lại hệ điều hành. Không chạy Firebase/BE. Bản demo APK cập nhật cho lượt này; release-QA trong bộ bàn giao giữ nguyên mốc 08/11, không dùng làm minh chứng cho Yêu thích ngày 09/11.
