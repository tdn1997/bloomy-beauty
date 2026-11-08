# Nghiệm thu A5 — phiếu PDF

Pixel_6 hiện có, Android 16/API 36. **8 kiểm thử exporter + 1 giao diện đều đạt** trong [hồi quy cuối](../pixel6-commerce-integration-2026-11-08/commerce-full-final-pixel6.txt). [Lượt riêng PDF reader và navigation](../pixel6-commerce-integration-2026-11-08/commerce-routes-reader-pixel6.txt) cũng đạt 3/3. Chỉ mở trình chọn và trình đọc cục bộ, không chọn người nhận hay gửi tệp.

- `01-snapshot-annule-coupon.pdf`: đơn đã hủy, catalog đã đổi tên/giá nhưng phiếu giữ snapshot; subtotal 200.000, giảm 20.000, ship 30.000, tổng COD 210.000; có dòng không yêu cầu thanh toán.
- `02-vietnamese-100-products-long-address.pdf`: 100 sản phẩm và địa chỉ dài hơn một trang, **8 trang**; trích xuất kiểm tra đủ sản phẩm số 100, tổng 29.950.000 và tiếng Việt. Đã xem ảnh render trang đầu/cuối, không cắt nội dung ra ngoài trang.
- `03-legacy-zero-discount.pdf`: giảm 0, không tự gán coupon; tổng 130.000.
- Ảnh 04–06: nút xuất, share sheet không chọn người nhận, phiếu mở trong Drive PDF Viewer bằng content URI. Hủy chooser giữ PENDING. Đây là UI fixture tổng hợp; tích hợp màn hình đơn thật qua BloomyApp có ảnh ở thư mục hồi quy chung.

Các ca quyền: khách khác không xuất, ADMIN hiện hành được xuất, thu hồi role/logout trước launch bị chặn và xóa file. Tên file riêng mỗi lần; dọn TTL/logout; FileProvider từ chối file bên ngoài subtree receipts. Lỗi ghi tệp không để lần xuất sau bị hỏng.

Trong phát triển phát hiện Android native PdfDocument không truyền lỗi IOException của stream như dự kiến; đã đổi sang dựng trong bộ nhớ rồi ghi qua Java stream để lỗi IO được báo đúng. Kiểm thử lỗi stream đã đạt sau sửa. Hai lần UI chưa đạt do hộp System UI ANR và do test chỉ chờ chooser trong khi Android mở thẳng reader; giữ các log trung gian, không tính các lượt đó là đạt. Đã đóng lỗi hệ thống và chạy lại trên đúng Pixel_6 với swiftshader; ảnh cuối đã được xem, không còn hộp lỗi hệ thống.

Các PDF/ảnh lưu là bằng chứng của lượt trước mà các ca liên quan đã đạt; acceptance toàn bộ cuối lưu riêng. [Hướng dẫn chia sẻ](../../phieu-don-hang-pdf.md) · [Manifest](../pixel6-commerce-integration-2026-11-08/manifest.json).
