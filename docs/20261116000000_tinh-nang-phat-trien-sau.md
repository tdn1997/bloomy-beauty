# Danh sách tính năng phát triển sau — 16/11/2026

Danh sách ý tưởng đã lưu, chưa triển khai và chưa có lịch cam kết.
Ưu tiên dưới đây là đề xuất để lựa chọn sau khi hoàn thành
[Mua lại đơn cũ](20261116000000_ke-hoach-mua-lai-don-cu.md).
Giữ định hướng demo ngoại tuyến một thiết bị; mỗi tính năng cần kế hoạch riêng
trước khi thực hiện.

Mua lại đơn cũ: hoàn tất ngày 16/11/2026; build/lint đạt, 71 unit và 57 Android
test liên quan đạt trên Pixel_6 API 35. [Hồ sơ](kiem-thu/reorder-2026-11-16/README.md).
Các ý tưởng trong bảng dưới đây vẫn chưa triển khai.

| Ưu tiên | Tính năng | Phạm vi ban đầu | Độ khó dự kiến |
|---|---|---|---|
| 1 | Hồ sơ làn da & gợi ý sản phẩm | Lưu loại da và nhu cầu theo tài khoản; gắn nhãn catalog; gợi ý bằng quy tắc rõ ràng, hiển thị lý do | Vừa |
| 2 | Nhắc hạn dùng mỹ phẩm | Ghi ngày mở nắp, số tháng dùng sau mở nắp; tính ngày nhắc và gửi thông báo cục bộ | Vừa |
| 3 | So sánh sản phẩm | Chọn 2–3 sản phẩm, so giá, dung tích, công dụng và loại da phù hợp; xóa từng mục so sánh | Vừa |
| 4 | Nhật ký chăm sóc da | Mở rộng lịch chăm sóc hiện có: ghi chú, ảnh theo ngày và đánh dấu hoàn thành từng bước | Vừa |
| 5 | Chia sẻ sản phẩm | Chia sẻ tên, ảnh và mô tả qua Android Sharesheet từ chi tiết sản phẩm | Dễ |

## Ghi chú để lập kế hoạch sau

- **Hồ sơ làn da:** bổ sung nhãn phù hợp cho các sản phẩm hiện có và cho phép
  sửa/xóa hồ sơ. Đây là gợi ý mua sắm theo thuộc tính, không chẩn đoán bệnh da
  hoặc suy đoán loại da từ ảnh. Phiên bản đầu không cần AI hay API ngoài.
- **Hạn dùng:** một sản phẩm có thể có nhiều lọ với ngày mở khác nhau. Phân biệt
  ngày hết hạn trên bao bì với thời hạn sau mở nắp; không tự suy ra thời hạn từ
  tên sản phẩm. Cho sửa ngày, tắt nhắc và đánh dấu đã dùng hết.
- **So sánh:** kiểm tra schema catalog trước khi thêm thuộc tính. Dữ liệu chưa
  có thì hiển thị “Chưa có thông tin”; không tự tạo công dụng hoặc thành phần.
  Giá theo dung tích chỉ tính khi đơn vị có thể so sánh được.
- **Nhật ký:** dữ liệu riêng từng tài khoản; ảnh lưu trong vùng riêng của app,
  dùng Photo Picker khi chọn ảnh; xác định cách dọn ảnh khi xóa mục hoặc tài khoản.
  Tận dụng lịch/nhắc hiện có, không tạo cơ chế nhắc song song.
- **Chia sẻ:** kiểm tra chức năng Content Provider/chia sẻ hiện có để tránh làm
  trùng. Ảnh dùng content URI và cấp quyền đọc tạm thời. Chưa tạo liên kết mở
  ứng dụng nếu chưa có thiết kế deep link tương ứng.

## Thứ tự lựa chọn

- Muốn có điểm nhấn đúng chủ đề mỹ phẩm: chọn **Hồ sơ làn da & gợi ý sản phẩm**.
- Muốn tận dụng lịch và thông báo hiện có: chọn **Nhắc hạn dùng mỹ phẩm**.
- Muốn một phần bổ sung nhỏ: có thể đưa **Chia sẻ sản phẩm** lên trước.

Chưa bao gồm đồng bộ nhiều thiết bị, backend giao dịch hoặc thanh toán online.
Khi bắt đầu một mục, tạo kế hoạch riêng và cập nhật trạng thái tại đây.
