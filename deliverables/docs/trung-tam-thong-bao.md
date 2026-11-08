# Trung tâm thông báo đơn hàng

Mở biểu tượng chuông ở đầu cửa hàng hoặc mục **Thông báo đơn hàng** trong Tài khoản. Badge hiển thị số chưa đọc (trên 99 hiển thị 99+). Danh sách mới nhất trước, tải 30 dòng/lần, có “Xem thêm” và “Đánh dấu tất cả đã đọc”.

Nguồn sự kiện: tạo đơn, hủy, xác nhận, đang giao và đã giao. Mỗi audit event ghi một dòng inbox cho chủ đơn trong **cùng transaction** với cập nhật nghiệp vụ. Retry tạo/hủy không ghi thêm audit hoặc inbox. Publisher/broadcast/notification hệ thống chỉ chạy sau commit; lỗi hiển thị không đổi kết quả thành thất bại. Quyền POST_NOTIFICATIONS bị tắt không ảnh hưởng lịch sử hoặc badge trong app.

Schema 11 thêm `user_notifications` và các index owner/time, owner/read. Unique `(user_id,event_id)` dùng event ID dẫn từ order audit. Migration 10→11 backfill từ audit thật, giữ thời gian gốc; đơn cũ không có audit không được gán sự kiện giả. Không đổi dữ liệu giá, coupon, địa chỉ hay trạng thái đơn khi migration.

Danh sách, phân trang, đọc tất cả và mở đích đều kiểm tra phiên hiện hành. Chạm dòng thông báo đọc lại đơn theo chủ sở hữu, đánh dấu đọc rồi mở chi tiết. Đơn đã mất hoặc không còn quyền sẽ có thông báo lỗi và không lộ dữ liệu. Đọc inbox không đổi trạng thái đơn hoặc hủy lịch chăm sóc. State/observer được gắn theo tài khoản; đổi phiên xóa state và pending deep link cũ.

Thông báo hệ thống chỉ hiện cho tài khoản chủ đơn đang đăng nhập. PendingIntent bất biến chỉ mang ID chủ và ID thông báo; màn hình khóa dùng nội dung chung, ẩn chi tiết. Khi chạm, repository kiểm tra lại phiên và quyền thay vì tin extras. Khi đổi phiên, hủy các notification đơn đang hiển thị. Không đưa điện thoại/địa chỉ vào broadcast hoặc lockscreen.

Nhắc chăm sóc tiếp tục dùng alarm và deep link hiện có; bản này không tự tạo dòng inbox mỗi lần mở lịch. Đồng bộ inbox/FCM từ máy chủ thuộc giai đoạn Firebase sau phần local, chưa được coi là đã hoàn thành.
