# Áp dụng mã giảm giá trong bản demo

Tính năng áp mã chỉ bật ở package `.demo` và `.qa`. Bản thường vẫn hiển thị feed ưu đãi Firestore, chưa dùng feed đó để cấp quyền giảm giá khi checkout.

Ở bước giao hàng hoặc kiểm tra đơn, nhập **Mã giảm giá demo → Áp dụng**. Tổng mới chỉ có hiệu lực khi áp dụng thành công. Có thể bấm **Không dùng mã** để chủ động bỏ mã và xác nhận tổng mới. Nếu sửa nội dung mã nhưng chưa áp dụng, nút đặt hàng bị khóa.

Mã khởi tạo cùng database schema 10, hiệu lực 30 ngày kể từ lúc tạo/migration:

| Mã | Điều kiện tiền hàng trước giảm | Mức giảm |
|---|---|---|
| DEMO20 | Từ 200.000đ | 20%, tối đa 50.000đ |
| DEMO50K | Từ 500.000đ | 50.000đ |

Thời hạn chính xác hiển thị trong ứng dụng. Khoảng hiệu lực gồm thời điểm bắt đầu và không gồm thời điểm kết thúc. Mã được trim và chuẩn hóa chữ hoa.

Tiền dùng số nguyên VND, giảm phần trăm làm tròn xuống, không giảm quá tiền hàng. Phí ship vẫn dựa trên **subtotal trước giảm**: từ 500.000đ miễn phí, dưới ngưỡng phí 30.000đ. Tổng = subtotal − giảm giá + phí ship. Ví dụ 500.000đ giảm 50.000đ thì ship 0 và tổng 450.000đ; 200.000đ giảm 20% thì ship 30.000đ và tổng 190.000đ.

Đặt hàng kiểm tra lại giỏ, phiên, thời hạn và điều kiện mã trong transaction. Nếu mã hết hạn hoặc điều kiện đổi sau review, đơn chưa được tạo; ứng dụng giữ báo giá cũ ở trạng thái không hợp lệ để bạn áp mã khác hoặc chủ động bỏ mã. Không tự bỏ mã và đặt với tổng khác.

Đơn lưu snapshot mã/quy tắc/mức giảm, nên sửa, xóa hoặc hết hạn mã không đổi lịch sử. Retry cùng yêu cầu đã commit trả đúng đơn đó, không trừ tồn thêm. Payload v1 cũ đã commit vẫn khôi phục đúng đơn giảm 0; yêu cầu cũ chưa commit được báo giá lại và dừng ở bước review chờ xác nhận.

Đây là dữ liệu cục bộ theo giờ thiết bị, chưa có quota nhiều thiết bị hoặc quyền giảm giá do server xác minh. Những phần đó thuộc giai đoạn Firebase B.

[Nghiệm thu Pixel_6: 37/37 Android, 58/58 JVM và ảnh](kiem-thu/pixel6-coupon-2026-11-08/README.md).
