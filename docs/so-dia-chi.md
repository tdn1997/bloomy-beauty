# Sổ địa chỉ và bản nháp giao hàng

Từ schema 9, vào **Tài khoản → Sổ địa chỉ** hoặc **Chỉnh sửa hồ sơ → Sổ địa chỉ** để quản lý tối đa 10 địa chỉ. Mỗi địa chỉ có nhãn, người nhận, số điện thoại Việt Nam và địa chỉ đầy đủ. Số dạng +84 được chuẩn hóa về 0.

Địa chỉ đầu tiên tự làm mặc định. Có thể đặt một địa chỉ khác làm mặc định; xóa mặc định sẽ chọn địa chỉ còn lại được tạo sớm nhất. Đổi/xóa mặc định thực hiện trong cùng transaction. Biểu mẫu cũ bị chặn nếu dữ liệu hoặc trạng thái mặc định đã thay đổi; quay về danh sách và tải lại để chỉnh trên bản mới.

Ở bước giao hàng, bấm **Chọn từ sổ địa chỉ → Dùng địa chỉ này**. Thao tác này lưu một bản sao vào draft giao hàng, sau đó bạn vẫn có thể sửa biểu mẫu. Sửa/xóa địa chỉ nguồn hoặc thay mặc định không thay bản sao đã chọn và không thay đơn đã đặt. Nếu chưa có draft giao hàng, địa chỉ mặc định được dùng để điền ban đầu; draft đang được nhập không bị ghi đè.

**Lưu địa chỉ này vào sổ** mở biểu mẫu thêm với thông tin giao hàng đang nhập. Chỉ khi bấm **Lưu địa chỉ** mới ghi vào sổ. Lưu sổ không tự đổi draft checkout; chọn **Dùng địa chỉ này** nếu muốn áp dụng bản sao vừa sửa cho giao hàng.

Hồ sơ chỉ sửa tên tài khoản. Đổi tên không sửa người nhận của draft đang nhập, địa chỉ trong sổ hay đơn đã đặt.

Migration 8 → 9 sao chép địa chỉ cũ trong `shipping_details` thành một địa chỉ mặc định một lần. Bảng cũ tiếp tục lưu draft checkout. Dữ liệu sổ và quyền sở hữu lưu cục bộ theo tài khoản SQLite; phần đồng bộ Firebase nằm ở giai đoạn B.

[Nghiệm thu Pixel_6: 24/24 ca và 5 ảnh](kiem-thu/pixel6-address-2026-11-08/README.md).
