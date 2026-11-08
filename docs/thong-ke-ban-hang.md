# Thống kê bán hàng

Từ Tài khoản → Quản trị cửa hàng → Thống kê bán hàng. Repository kiểm tra phiên hiện hành và vai trò ADMIN ở mỗi truy vấn; thu hồi quyền sẽ xóa số liệu đã tải khỏi state.

Chọn 7 ngày, 30 ngày, toàn thời gian hoặc nhập khoảng `yyyy-MM-dd`. Ngày kết thúc được tính trọn ngày theo `Asia/Ho_Chi_Minh`; truy vấn dùng khoảng nửa mở từ đầu ngày đầu đến đầu ngày sau ngày cuối. Nhập bộ lọc chưa áp dụng hoặc bộ lọc lỗi không đổi nhãn của báo cáo đang hiển thị.

- Doanh thu chỉ tính đơn DELIVERED, theo `delivered_at`, bằng tiền hàng snapshot trừ giảm giá snapshot. Phí ship được cộng và hiển thị riêng; trung bình tiền hàng/đơn dùng phép chia nguyên VND, để “—” nếu chưa có đơn.
- Số đơn theo trạng thái dùng `created_at`, vì vậy có thể khác số đơn làm căn cứ tính doanh thu trong cùng khoảng ngày.
- Đơn đã giao thiếu ngày giao được đối chiếu trong nhóm riêng toàn thời gian, không tự gán ngày đặt làm ngày giao và không vẽ vào biểu đồ ngày. Chỉ chế độ toàn thời gian cộng nhóm này vào doanh thu tổng.
- Bán chạy tính số lượng dòng hàng của đơn đã giao; theo ngày giao khi lọc. Chế độ toàn thời gian gồm đơn thiếu ngày giao. Tên hiển thị hiện tại của sản phẩm không được dùng để tính lại giá lịch sử.
- Sắp hết hàng là tồn hiện tại ≤ 5 của sản phẩm đang bán, không phụ thuộc bộ lọc ngày.

Các tổng tiền có kiểm tra tràn `Long`. Repository tổng hợp trực tiếp cột kế toán, không đưa tên/điện thoại/địa chỉ người nhận vào dashboard. Không tăng schema ở A4; dùng cột ngày giao và index đã thêm ở A0.

Kết quả nghiệm thu và ảnh được ghi riêng trong thư mục kiểm thử Pixel 6; kiểm tra thiết bị thật để sau theo yêu cầu.
