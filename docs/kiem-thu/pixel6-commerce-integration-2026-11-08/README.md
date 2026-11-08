# Hồi quy sáu tính năng local — schema 11

Chỉ dùng **Pixel_6 hiện có**, emulator-5554, Android 16/API 36. Không tạo AVD, không kiểm tra điện thoại thật. Các repository dùng SQLite thật và fixture riêng; không đưa đơn/tài khoản test thành doanh thu server.

Lượt cuối `commerce-full-final-pixel6.txt`: **172 ca, 164 thành công, 8 assumption skip, 0 lỗi**, 358,920 giây instrumentation. Lượt tắt POST_NOTIFICATIONS riêng **4/4 đạt**, gồm hai ca đã skip vì cần quyền denied và hai ca UI inbox. JVM **62/62 đạt**, lint **0 lỗi / 43 cảnh báo / 1 thông tin**. Cảnh báo gồm style/API modernization, được giữ trong XML; không gọi đây là lint sạch cảnh báo.

Các skip của lượt đầy đủ: Firebase Emulator opt-in (5), care device scenario opt-in (1), hai ca cần POST_NOTIFICATIONS denied (2). Hai ca denied đã chạy riêng và khôi phục quyền. Kiểm thử live Firebase/FCM và Doze nhiều giờ không suy ra từ kết quả này; bằng chứng lịch/Doze và Firebase cũ vẫn thuộc đợt nghiệm thu trước.

Hồi quy bao gồm xác thực/phiên, danh mục/giỏ/COD/hủy, quản trị/hồ sơ, care Provider/lifecycle, ưu đãi, đánh giá, sổ địa chỉ, coupon, thống kê, PDF và inbox. Migration từ các fixture cũ lên v11 được chạy; fingerprint/payload v1 giữ đường phục hồi tương thích. CommerceExtensionsIntegrationTest kiểm tra chuông có badge → inbox → đúng chi tiết đơn/PDF/đánh giá; sổ địa chỉ từ Tài khoản; đổi phiên chặn thông báo cũ; ADMIN mở dashboard.

Ảnh 01–04 đã xem trực tiếp và không có hộp lỗi hệ thống. Ảnh dashboard còn toast có chủ đích “Thông báo không thuộc tài khoản hiện tại” sau khi chặn link cũ. Ảnh và PDF trong các thư mục A4/A5/A6 được lấy từ các lượt trước mà các ca tương ứng đã đạt; nghiệm thu cuối toàn bộ ghi ở log riêng, không đổi nhãn lượt chụp. `manifest.json` ghi hash mã nguồn/APK, tổng JVM/lint, thời điểm và môi trường. XML JVM được lưu cùng thư mục, tránh phụ thuộc report build bị ghi đè khi đổi variant.

Các lượt chưa đạt được giữ: `commerce-full-pixel6.txt` có 1 lỗi tìm node tổng COD; kiểm tra riêng có thêm assert state REVIEW và tổng 222.000 đạt 1/1, rồi toàn bộ lượt cuối đạt. Không đổi cách tính giá/ship để làm test qua. Các lần giả lập chậm/System UI ANR ở A4/A5 không tính là nghiệm thu. Khởi động nguội lại chính Pixel_6, chạy `-gpu swiftshader` để ổn định render; không sửa file cấu hình AVD. Animation tạm tắt trong mỗi lượt đã được khôi phục đúng giá trị gốc; xem environment JSON.

[Thống kê](../pixel6-statistics-2026-11-08/README.md) · [PDF](../pixel6-pdf-2026-11-08/README.md) · [Inbox/quyền denied](../pixel6-inbox-2026-11-08/README.md) · [Kế hoạch và mục B chưa hoàn thành](../../ke-hoach-mo-rong-ban-hang.md).

Bản thường assembleDebug đạt (`LOCAL_COUPONS_ENABLED=false`, Firebase feed bật); release `.qa` assembleRelease đạt (`LOCAL_COUPONS_ENABLED=true`, Firebase tắt). Bản thường chỉ được build, không cài/không ghi dữ liệu Firebase. Release QA được cài đè và force-stop → cold launch trên Pixel_6, giữ phiên ReleaseQA và hiển thị cửa hàng/badge; xem ảnh/log cold start. Không gọi lượt smoke release này là toàn bộ instrumentation trên release.
