# Phiếu đơn hàng PDF

Trong chi tiết đơn của khách hoặc quản trị, chọn **Xem PDF** hoặc **Chia sẻ PDF**. Android mở trình chọn ứng dụng; người dùng tự quyết định ứng dụng nhận tệp. Hủy trình chọn không cập nhật trạng thái đơn.

Phiếu có tiêu đề “Phiếu đơn hàng — bản demo”, mã, ngày giờ Việt Nam, trạng thái, COD, người nhận, điện thoại, địa chỉ, sản phẩm/đơn giá/số lượng, tạm tính, mã và tiền giảm, phí ship và tổng COD. Đơn hủy ghi rõ không yêu cầu thanh toán. Đây là phiếu minh họa, không phải hóa đơn thuế.

Nội dung lấy từ snapshot orders/order_items, không lấy tên hoặc giá mới trong catalog. Native PdfDocument/StaticLayout hỗ trợ tiếng Việt, tự xuống dòng và sang trang, kể cả khi một địa chỉ dài hơn một trang.

Khách xuất đơn của mình; ADMIN hiện hành được xuất đơn qua quyền quản trị. Kiểm tra phiên/quyền trước đọc, sau dựng PDF và ngay trước mở trình chọn. Phiếu không được chia sẻ nếu người dùng đã đăng xuất hoặc quyền bị thu hồi.

File nằm trong `cache/order-pdfs/`, có tên riêng theo người xem, mã đơn và UUID. Dọn file quá 24 giờ khi xuất và dọn/revoke quyền đọc các file cache khi phiên thay đổi. FileProvider riêng `${applicationId}.receipts` chỉ mở thư mục này, không xuất toàn bộ cache hay storage. Intent truyền content URI với ClipData và quyền đọc tạm, không cấp quyền ghi/persistable/prefix; không cần quyền bộ nhớ. Tệp đã được người nhận lưu thành bản riêng nằm ngoài phạm vi cache của ứng dụng.

Cách cấp quyền theo [tài liệu Android về chia sẻ tệp](https://developer.android.com/training/secure-file-sharing/share-file).

Kiểm thử dùng dữ liệu tổng hợp trên Pixel_6, gồm PDF dài, tiếng Việt, đơn hủy/coupon, đơn cũ giảm 0, quyền khác tài khoản, lỗi IO, dọn cache, hủy share sheet và trình đọc PDF bên ngoài. Không gửi phiếu tới người nhận thực tế.
