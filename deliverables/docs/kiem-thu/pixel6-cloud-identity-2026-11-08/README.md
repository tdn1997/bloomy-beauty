# Nghiệm thu B0/B1 — Firebase Auth và quyền server

**Phạm vi hiện tại:** đây là minh chứng thử nghiệm tùy chọn. Theo yêu cầu demo môn học, không cần triển khai BE và không tiếp tục B2–B4.

Ngày 08/11/2026. Chỉ dùng Pixel_6 hiện có, emulator-5554, Android 16/API 36; dịch vụ Auth/Firestore/Functions Emulator chạy trên máy tính với project `demo-bloomy-commerce`. Bản Android `.cloud` debug, tên **Bloomy Beauty Cloud (test)**. Chưa triển khai backend/rules/Auth mới lên project live.

| Nhóm | Kết quả | Bằng chứng |
|---|---|---|
| Cloud Android: 5 repository + 3 UI | 8/8, không skip/lỗi | cloud-policy-final-pixel6.txt |
| Cold start: prepare + inspect | 2 lượt đạt; force-stop và launch COLD thật ở giữa | cloud-policy-cold-*.txt |
| Backend Auth/role/profile/rules thật | 7/7 | cloud-backend-identity-tests.txt |
| Rules ưu đãi cũ, project demo-bloomy-beauty riêng | 9/9 | cloud-legacy-promo-rules.txt |
| Hồi quy local các nhóm chịu ảnh hưởng | 48/48 | cloud-local-regression-pixel6.txt |
| JVM | 62/62 | TEST-*.xml |
| Lint Cloud | 0 lỗi / 44 cảnh báo / 1 thông tin | lint-results-cloud.xml |

Tám ca Android cuối gồm: local account/đơn cùng email không tự đăng nhập hoặc nhập lên Cloud; đăng ký Firebase tường minh giữ nguyên local; stale UID và nonce bị chặn; gateway đóng không dùng lại được; client không truyền UID/role để nâng quyền và không ghi role trực tiếp; server grant/revoke với cùng ID token; đăng ký tiếng Việt/logout/login và role UI đổi theo server; chuyển UID xóa account/role cũ; mật khẩu hợp lệ của tài khoản Firebase đã có được đăng nhập mà không áp nhầm quy tắc đăng ký local.

Backend dùng ID token/Auth user, không tin UID/role trong data. Đã kiểm tra anonymous, giả identity/role, profile bootstrap lặp không thay document cũ, đọc/ghi/enumeration sai chủ bị chặn, disabled Auth user không dùng token cũ để đọc account. API admin đọc lại role trong transaction sau revoke, không chờ refresh token.

Cold start dùng MainActivity thật và tài khoản public seed customer@bloomy.cloud.test. UID giữ nguyên, nonce đổi sau tiến trình mới; server xác minh lại CUSTOMER. Provider và hai receiver lịch local đều được kiểm tra disabled trong `.cloud`; file database bán hàng `bloomy_beauty.db` không được tạo. `cold-prepare.json`/`cold-inspect.json` lưu UID/nonce của fixture, không lưu ID token.

Hồi quy local gồm AuthRepositoryTest, AuthFlowTest, OrderRepositoryTest, CareRoutineProviderTest, CartFlowTest, ShopColdStartTest, ShopIntegrationTest và CommerceExtensionsIntegrationTest. Đây là nhóm liên quan 48 ca, không phải chạy lại toàn bộ 172 ca của A7. Bản thường cũng assembleDebug đạt với CLOUD_EMULATOR=false, Firebase feed=true và coupon local=false; xem normal-build-flags.json. Release variant không tồn tại khi bật cloudEmulator; xem cloud-variant-tasks.txt.

Ảnh 01–05 đã xem trực tiếp: khách vừa đăng ký, logout xóa account, ADMIN từ server, role bị thu hồi, chuyển sang khách thứ hai. Hai ảnh cold và ảnh process cold launch cũng đã xem, không có hộp lỗi hệ thống. Ảnh 01–05 từ lượt 8/8 cuối; ảnh cold prepare/inspect từ hai lượt cuối cùng trên cùng APK. Ảnh process cold launch riêng được giữ từ lượt cold launch đạt trước đó. Ảnh/UID đều là fixture tổng hợp, không phải khách thật. Animation tạm tắt trong instrumentation đã khôi phục đúng giá trị gốc ở mọi lượt nghiệm thu; metadata JSON lưu kèm.

Trong triển khai phát hiện receiver khôi phục lịch cũ tạo DB trống ở `.cloud` khi cài đè. Đã xác minh 0 user/session/order/cart/address/review/inbox/care trước khi xóa riêng DB trống của package Cloud; không xóa dữ liệu `.demo`, `.qa` hay bản thường. Sau đó thêm manifest disable và guard trong code; cold-start/48 ca local xác nhận tách nguồn đúng. cloud-empty-db-verification.json ghi số liệu trước dọn fixture. Log cloud-acceptance-build.log là lượt build test thiếu import Rule, đã sửa; không dùng lượt đó làm nghiệm thu.

APK đã kiểm thử được giữ ở `.local/cloud-auth-emulator.apk` và `.local/cloud-auth-emulator-test.apk`, hash trong manifest.json. APK app đã cài trên Pixel_6; ZIP trong deliverables vẫn là bản local A7, chưa được gọi là bản Cloud hoàn chỉnh. SDK credentials, dữ liệu emulator export và cấu hình local không nằm trong nguồn/QA công khai. Chưa commit.

B2 (giao dịch server), B3 (đồng bộ) và B4 (nghiệm thu live) không thuộc phạm vi demo đã chốt, không phải việc còn thiếu phải triển khai. Kết quả ở đây chỉ chứng minh phần Auth thử nghiệm đã thực hiện. [Hướng dẫn chạy và hợp đồng](../../firebase-auth-emulator.md) · [Kế hoạch](../../ke-hoach-mo-rong-ban-hang.md).
