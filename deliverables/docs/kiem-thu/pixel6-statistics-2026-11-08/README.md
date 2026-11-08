# Nghiệm thu A4 — thống kê quản trị

Pixel_6 hiện có, Android 16/API 36, APK demo schema 11. **7 repository + 3 giao diện đều đạt** trong [hồi quy cuối](../pixel6-commerce-integration-2026-11-08/commerce-full-final-pixel6.txt). Quy tắc ngày có thêm 4 kiểm thử JVM trong tổng 62 ca. Không kiểm tra điện thoại thật.

Fixture có 9 đơn, đủ 5 trạng thái, giảm giá và ship riêng, ngày tạo khác ngày giao, thời điểm đúng biên ngày Việt Nam và một đơn DELIVERED thiếu ngày giao. Trong ngày 08/11: tiền hàng sau giảm 270.000 ₫, ship 30.000 ₫, 2 đơn, trung bình 135.000 ₫; số đơn tạo trong ngày là 7, gồm 3 DELIVERED. Toàn thời gian: tiền hàng 1.020.000 ₫, ship 90.000 ₫, 5 đơn; một đơn thiếu ngày giao 50.000 ₫ không được đưa vào biểu đồ. Catalog đổi giá không đổi doanh thu; sản phẩm inactive không vào cảnh báo tồn.

Đã kiểm tra khách/sai phiên không được đọc, thu hồi ADMIN xóa snapshot, không chia 0 khi rỗng/giảm 100%, tràn Long báo lỗi, dữ liệu discount hỏng bị chặn và ngày kết thúc trước ngày đầu không đổi nhãn báo cáo cũ.

Đã xem trực tiếp cả 7 ảnh trong thư mục: số liệu khớp fixture, không có hộp lỗi hệ thống. Ảnh lấy ở lượt hồi quy trước, các ca thống kê ở lượt đó đều đạt; lượt nghiệm thu toàn bộ cuối lưu riêng. Tên fixture/ngày được kiểm soát, không phải dữ liệu bán hàng thực tế.

Lượt đầu 9/10 đạt, một ca UI hết chờ 10 giây khi giả lập tải cao. Giữ log `statistics-pixel6.txt`; tăng giới hạn chờ UI lên 30 giây mà không đổi đối chiếu dữ liệu. Giả lập sau đó khởi động nguội lại đúng Pixel_6 và dùng renderer swiftshader theo tham số chạy, không sửa cấu hình AVD. Animation tạm tắt được khôi phục, metadata nằm cùng log.

[Hướng dẫn tính số liệu](../../thong-ke-ban-hang.md) · [Manifest mã nguồn/APK và tổng kiểm thử](../pixel6-commerce-integration-2026-11-08/manifest.json).
