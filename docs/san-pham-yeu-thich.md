# Sản phẩm yêu thích

Cập nhật 09/11/2026. Chạy local SQLite, schema 11; không cần backend.

- Nhấn tim trên thẻ hoặc trang chi tiết để lưu/bỏ lưu.
- Mở bằng tab **Yêu thích** hoặc **Tài khoản → Sản phẩm yêu thích**.
- Tìm kiếm, danh mục và sắp xếp trong Yêu thích độc lập với bộ lọc danh mục chính; trạng thái giao diện được giữ bằng rememberSaveable theo tài khoản.
- Dùng **Thêm vào giỏ** trên thẻ yêu thích; sản phẩm vẫn nằm trong danh sách sau khi thêm. Giỏ kiểm tra giá, trạng thái và tồn kho qua luồng hiện có.
- Hết hàng vẫn hiện để xem/bỏ lưu, nút thêm giỏ bị khóa. Sản phẩm ngừng bán hoặc bị xóa không hiện và không tính trong badge. Ngừng bán chỉ ẩn mục đã lưu; nếu quản trị bán lại, mục đó hiện lại. Xóa sản phẩm sẽ cascade mục yêu thích.
- Khi chưa lưu gì, chọn **Khám phá sản phẩm**. Nếu chỉ bộ lọc không khớp, chọn **Xóa bộ lọc và tìm kiếm**; không cần lưu lại sản phẩm.

Bảng `favorites` dùng khóa `(user_id, product_id)` và khóa ngoại xóa theo user/product. Mỗi thao tác kiểm tra phiên bằng SessionAccess trong transaction; không truy cập được mục của tài khoản khác. Dữ liệu tồn tại sau đăng nhập lại và đóng/mở database. Không thay schema hoặc di chuyển dữ liệu trong đợt này.

Nền bảng/nút tim/tab đã có trước ngày 09/11; phần hoàn thiện hôm nay là các lối vào, bộ lọc độc lập, trạng thái rỗng/tồn kho, thêm nhanh giỏ, kiểm tra sản phẩm đang bán và kiểm thử bổ sung.

[Kế hoạch](ke-hoach-yeu-thich.md) · [Kiểm thử Pixel_6](kiem-thu/pixel6-favorites-2026-11-09/README.md) · [Bài tập cá nhân](bai-tap/san-pham-yeu-thich/README.md)
