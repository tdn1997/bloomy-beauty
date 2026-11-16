# Kế hoạch So sánh sản phẩm — 16/11/2026

Phạm vi: demo SQLite trên một thiết bị. Trạng thái: hoàn tất ngày 16/11/2026.
76/76 unit và 21/21 Android test liên quan đạt (chia ba nhóm), lint 0 lỗi.
Không thay schema hoặc triển khai backend. [Hồ sơ và ảnh](kiem-thu/comparison-2026-11-16/README.md).

## Quy tắc và luồng sử dụng

1. Chọn/bỏ sản phẩm từ thẻ ở Trang chủ, Khám phá, Yêu thích hoặc trang chi tiết.
2. Chọn tối đa 3 sản phẩm; chỉ mở bảng khi có ít nhất 2. Khi đủ 3, các mục
   chưa chọn bị khóa, vẫn cho bỏ mục đã chọn để thay thế.
3. Thanh lựa chọn hiển thị số mục, nút mở bảng và xóa lựa chọn. Bộ lọc danh mục
   không làm mất lựa chọn. Trên bảng có thể bỏ từng mục, xóa hết hoặc xem chi tiết.
4. Bảng cuộn ngang theo cột, cuộn dọc theo thuộc tính. Hiển thị giá hiện tại,
   dung tích nguyên bản, danh mục, thương hiệu, tồn, mô tả, thành phần, cách dùng.
5. Mục không có dữ liệu hiển thị “Chưa có thông tin”. Không tự suy ra loại da
   hay quy đổi giá/ml/g từ văn bản. Giá khác dung tích không được coi là so
   sánh giá trên cùng đơn vị.
6. Cho so sánh sản phẩm hết hàng; khóa thêm giỏ. Sản phẩm ngừng bán/xóa được
   loại khỏi lựa chọn khi catalog cập nhật. Tải lại, quay lại app hoặc thay đổi
   đơn/kho qua luồng hiện có giúp nhận dữ liệu mới.
7. Lựa chọn là trạng thái giao diện theo tài khoản, không thêm bảng SQLite;
   giữ khi điều hướng/xoay máy, không mô tả là dữ liệu lưu vĩnh viễn. Đổi tài
   khoản làm sạch lựa chọn. Lỗi tải ẩn bảng và cho thử lại.

## Điểm triển khai

- `ComparisonSelection`: kiểm tra giới hạn, loại trùng/mục không còn hoạt động,
  giữ thứ tự chọn, quy tắc thông tin thiếu.
- `HomeScreen`: lưu ID lựa chọn bằng `rememberSaveable(user.id)`; nối nút chọn
  từ thẻ/chi tiết và thanh mở so sánh. Khi mở chi tiết từ bảng, quay lại về bảng.
- `ComparisonScreen`: hiển thị cùng một hàng thuộc tính trên các cột, tải lại,
  xóa mục, xem chi tiết và thêm giỏ qua callback sẵn có.
- `HomeViewModel`/`CatalogRepository`: giữ nguồn catalog và kiểm tra phiên hiện
  có. Thêm giỏ tiếp tục đi qua CartViewModel/CartRepository để kiểm tra thực tế.

## Kiểm thử và bàn giao

- Unit: giới hạn 3, thêm/bỏ, loại trùng/mất/ngừng bán, hết hàng, thứ tự và thông
  tin thiếu. Dữ liệu đầu vào không bị sửa.
- Compose: chọn từ thẻ/chi tiết, thanh lựa chọn, 2–3 cột và cuộn ngang, bỏ/xóa,
  xem chi tiết rồi quay lại, callback thêm giỏ, lọc không ảnh hưởng, recreate,
  đổi tài khoản, giá/tồn thay đổi và lỗi/loading/retry.
- Hồi quy: HomeFlowTest, FavoritesFlowTest, ShopRecreationTest và CartFlowTest.
- Dùng Pixel_6 API 35 hiện có; ghi kết quả thật, ảnh và giới hạn tại
  `docs/kiem-thu/comparison-2026-11-16/README.md`.
- Cập nhật hướng dẫn, README và danh sách tính năng tương lai khi nghiệm thu.

[Hướng dẫn](20261116000000_so-sanh-san-pham.md).
