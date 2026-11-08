# Nghiệm thu A6 — trung tâm thông báo

Pixel_6 hiện có, Android 16/API 36, schema 11. **8 repository + 2 UI đạt** trong [hồi quy toàn bộ](../pixel6-commerce-integration-2026-11-08/commerce-full-final-pixel6.txt). Lượt riêng tắt POST_NOTIFICATIONS **4/4 đạt**: InboxPermissionDeniedTest, PushPermissionDeniedTest và 2 UI. File `inbox-denied-permission.json` chứng minh quyền ban đầu true → trong kiểm thử false → khôi phục true, giữ nguyên flags; animation cũng được phục hồi.

Đã kiểm tra tạo/hủy retry không nhân event; ADMIN chuyển đủ trạng thái ghi cho chủ đơn; mở thông báo cũ đọc trạng thái đơn hiện tại. Trigger làm lỗi ghi inbox khiến đơn/tồn/audit/giỏ rollback, retry cùng request sau gỡ trigger thành công. Publisher lỗi sau commit không làm đơn thành thất bại. Đọc tất cả và mở đơn không vượt chủ/sai phiên; sai đích giữ lịch sử và báo lỗi. 65 sự kiện cùng timestamp phân trang không trùng/mất; đóng/mở helper giữ lịch sử. Migration 10→11 backfill đúng event/time audit và không mất commerce data; mở lại không backfill trùng.

Khi quyền hệ thống bị từ chối: tạo đơn thành công, retry không trùng, không có notification hệ thống, inbox/unread vẫn tồn tại, mở đúng đơn và đánh dấu đọc; đơn vẫn PENDING. Luồng deep link sau đổi tài khoản được kiểm tra thêm ở CommerceExtensionsIntegrationTest.

Ảnh 01–04 đã xem trực tiếp: chưa đọc, đọc tất cả, đích mất và xóa state khi phiên thay đổi; không có hộp lỗi hệ thống. Fixture 35 dòng cố ý dùng audit tổng hợp cho phân trang; không mô tả đây là 35 lần chuyển trạng thái hợp lệ của một đơn thật. Ảnh được chụp ở lượt trước đã đạt các ca UI; log riêng quyền bị từ chối là bằng chứng bổ sung.

Nhắc chăm sóc/alarm vẫn theo luồng cũ, không tự ghi thêm inbox mỗi lần mở lịch. FCM/server inbox thuộc B, chưa được nghiệm thu ở đây. [Hướng dẫn](../../trung-tam-thong-bao.md) · [Manifest](../pixel6-commerce-integration-2026-11-08/manifest.json).
