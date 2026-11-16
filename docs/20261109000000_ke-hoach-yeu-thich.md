# Kế hoạch hoàn thiện sản phẩm yêu thích — 09/11/2026

Phạm vi: demo môn học, SQLite trên thiết bị; không triển khai backend. Mã nền đã có bảng `favorites`, nút tim và tab yêu thích. Đợt này hoàn thiện luồng sử dụng và bổ sung minh chứng, không tính lại phần nền là tính năng mới.

## Các bước triển khai

1. Tận dụng khóa chính `(user_id, product_id)` và kiểm tra phiên hiện có; chỉ đếm/hiển thị sản phẩm còn hoạt động. Không lưu thêm sản phẩm ngừng bán hoặc không tồn tại; vẫn cho bỏ lưu mục cũ. Không đổi schema hoặc xóa dữ liệu người dùng.
2. Thêm lối vào từ Tài khoản; bộ lọc riêng cho Yêu thích để tìm kiếm ở trang chủ không che danh sách đã lưu. Phân biệt danh sách rỗng với bộ lọc không khớp; có nút khám phá hoặc xóa lọc phù hợp.
3. Hiển thị tồn kho trên thẻ, thêm nhanh vào giỏ từ Yêu thích; hết hàng vẫn xem/bỏ lưu được nhưng không thêm giỏ. Nút tim đồng bộ với trang chi tiết.
4. Kiểm thử repository: tồn tại sau mở lại DB, tách tài khoản, từ chối phiên cũ, sản phẩm ngừng bán/bị xóa/hết hàng; kiểm thử Compose các lối vào, bộ lọc, thêm giỏ, bỏ lưu, trạng thái rỗng. Chạy hồi quy liên quan và JVM/lint.
5. Chỉ dùng AVD Pixel_6 hiện có. Chụp ảnh luồng thực thi, ghi kết quả thực tế và giới hạn vào bài nộp cá nhân Sản phẩm yêu thích ngày 09/11/2026 (Markdown và Word). Không thay số liệu lịch sử. Cập nhật README/báo cáo đề tài và bộ bàn giao.

## Tiêu chí nghiệm thu

- Tài khoản A lưu sản phẩm, tài khoản B không thấy; A đăng nhập lại vẫn còn.
- Nút tim hoạt động trên danh sách/chi tiết; badge chỉ đếm sản phẩm đang hiển thị hợp lệ.
- Tìm kiếm trang chủ không ảnh hưởng Yêu thích; thêm giỏ dùng luồng giỏ hiện có.
- Hết hàng vẫn có trong Yêu thích; ngừng bán hoặc xóa không xuất hiện.
- Có ảnh và log kiểm thử thật trên Pixel_6; thiết bị thật để sau.

Trạng thái: hoàn tất ngày 09/11/2026. Android 26/26 đạt; JVM 62/62; lint debug 0 lỗi, 68 cảnh báo, 1 thông tin. Đã chụp 11 ảnh mới và tạo [bài tập cá nhân Sản phẩm yêu thích](bai-tap/san-pham-yeu-thich/README.md). [Hồ sơ kiểm thử](kiem-thu/pixel6-favorites-2026-11-09/README.md) ghi phạm vi và lần chạy đầu cần sửa tên fixture.
