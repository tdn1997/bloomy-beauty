# Nghiệm thu A1 — đánh giá sản phẩm trên Pixel 6

Bắt đầu 07/11/2026, chốt 08/11/2026. Phạm vi [A1/E1](../../ke-hoach-mo-rong-ban-hang.md), SQLite schema 8: viết/sửa/xóa đánh giá từ đơn đã giao, điểm trung bình, quyền tài khoản và migration. Chưa đồng bộ đánh giá lên Firebase.

## Kết quả cuối

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| Build debug + APK kiểm thử | Đạt | `final-build.txt` |
| JVM | 46/46 đạt, không bỏ qua | `jvm-summary.json` |
| Lint | 0 lỗi, 38 cảnh báo | `final-build.txt` |
| Nghiệm thu Android | **9/9 đạt**, không bỏ qua; 422,818 giây | `final-acceptance.txt` |
| Ảnh | 6 ảnh đã xem trực tiếp, không có hộp thoại lỗi hệ thống che nội dung | `screenshots/` |

9 ca cuối gồm 3 UI (tạo/sửa/xóa; giữ bản nháp sau khôi phục; bản nháp cũ không thể xóa đánh giá mới hơn), 5 repository/migration đánh giá và 1 nền ngày giao. Repository chặn đơn chưa giao, sản phẩm ngoài đơn, tài khoản khác, phiên cũ, dữ liệu nhập sai, tạo trùng và thao tác sửa/xóa cũ. Mua lại được đánh giá riêng; migration kiểm tra `foreign_key_check`/`integrity_check`.

Các lượt hồi quy trước: `data-admin-care-initial.txt` đạt 17/17 (gồm 4 ca đánh giá đời đầu); `regression-initial.txt` có 25 ca dữ liệu đơn/catalog/migration giỏ đạt và 1 ca UI thất bại đã xử lý ở bản cuối. Không cộng các lượt này thành tổng ca mới vì có trùng lặp. Đây không phải lượt chạy lại toàn bộ 105 ca Android cũ.

## Môi trường và cách chạy

Chỉ dùng AVD **Pixel_6** đã có, serial `emulator-5554`, Android 16/API 36. Gói `com.example.bloomybeauty.demo`, build `-Ppresentation=true`, Firebase tắt. Cài bằng `adb install -r`, không dùng tác vụ Gradle tự gỡ ứng dụng.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -Ppresentation=true --offline
adb -s emulator-5554 shell am instrument -w -e class com.example.bloomybeauty.feature.review.ReviewScreenTest,com.example.bloomybeauty.data.review.ReviewRepositoryTest,com.example.bloomybeauty.data.care.CommerceFoundationTest com.example.bloomybeauty.demo.test/androidx.test.runner.AndroidJUnitRunner
```

## Ảnh minh chứng

Ảnh UIAutomation trên Pixel_6, dùng ComponentActivity và database fixture riêng; các composable OrdersScreen/ReviewEditor/ProductReviews dùng repository thật. Phần danh sách đánh giá được hiển thị riêng trong fixture, và được nhúng ở chi tiết sản phẩm trong ứng dụng.

- `00-editor-open.png`: mở biểu mẫu từ đơn đã giao.
- `01-viet-danh-gia.png`: chọn 4 sao và nhập nhận xét; bàn phím đang hiển thị.
- `02-danh-gia-san-pham.png`: điểm trung bình, số lượng và nhận xét đã lưu.
- `03-sua-danh-gia.png`: nội dung sửa trước khi lưu.
- `04-xac-nhan-xoa.png`: xác nhận xóa trong cùng cửa sổ hộp thoại.
- `05-chan-xoa-ban-nhap-cu.png`: báo xung đột và giữ lại đánh giá mới hơn.

## Các lỗi đã xử lý khi kiểm thử

Các lượt đầu có điều kiện chờ sai với nút lưu khi nhận xét trống và thao tác khi bàn phím mở. Đã chuyển sang nhận diện ô nhập bằng tag, căn vùng an toàn và xử lý focus. Cập nhật Compose về Main; truy vấn vẫn trên IO. Biểu mẫu giữ nội dung và khóa ID/revision qua khôi phục trạng thái, cả thao tác xóa cũng dùng khóa ban đầu.

Lỗi idle/focus khi chuyển xác nhận xóa về báo xung đột được sửa bằng một cửa sổ AlertDialog cố định, chỉ thay nội dung. Log các lượt lỗi ở `troubleshooting/`; lượt bị dừng có thể không có tổng kết hoàn chỉnh. Pixel_6 từng kẹt điều khiển, đã cold boot chính AVD này, bỏ qua snapshot và giữ dữ liệu; không tạo AVD mới. Hộp thoại System UI che bộ ảnh trung gian đã được xử lý. Bộ ảnh cuối được chụp lại và xem trực tiếp; `troubleshooting/systemui-overlay.png` chỉ là bằng chứng lỗi trung gian.

`manifest.json` ghi hash APK, một số tệp nguồn A1 và ảnh tại thời điểm chốt A1 trước A2. Chưa kiểm tra thiết bị thật, ảnh đính kèm đánh giá hoặc đồng bộ Firebase.
