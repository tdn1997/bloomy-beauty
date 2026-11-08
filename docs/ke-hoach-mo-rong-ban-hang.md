# Kế hoạch mở rộng Bloomy Beauty

Ngày lập: 07/11/2026. Trạng thái: **Đã hoàn thành phạm vi demo môn học A0–A7; không triển khai BE. B0/B1 là thử nghiệm tham khảo, B2–B4 ngoài phạm vi**.

Phạm vi gồm sáu tính năng đã đề xuất: đánh giá sản phẩm, áp mã giảm giá, thống kê quản trị, sổ địa chỉ, chia sẻ PDF đơn hàng và trung tâm thông báo. Theo điều chỉnh phạm vi của người dùng ngày 08/11/2026, không cần triển khai backend riêng, giao dịch server hay đồng bộ nhiều thiết bị để hoàn tất demo. Phần B dưới đây được giữ làm lịch sử thiết kế/tham khảo, không phải công việc còn thiếu. Nhật ký bên dưới ghi nhận phần đã triển khai.

## 1. Mốc xuất phát trước A0 và nguyên tắc

- Android Kotlin/Compose, minSdk 28, targetSdk 35; SQLite schema **6**. Đăng nhập, sản phẩm, giỏ, giao hàng, đơn, quản trị và lịch chăm sóc đang lưu trên thiết bị.
- `shipping_details` hiện giữ một địa chỉ theo tài khoản, đồng thời phục vụ checkout. `orders` có snapshot người nhận, giá và tổng tiền; chưa có coupon snapshot hoặc `delivered_at` riêng.
- `CheckoutRequest.fingerprint` hiện dựa trên dòng giỏ và giao hàng. Thêm coupon phải mở rộng cả payload, fingerprint và khôi phục yêu cầu đang chờ.
- Firestore hiện công bố flash sale/mã; chưa áp mã vào checkout. FCM chưa gắn token với tài khoản hoặc có backend gửi theo đơn. Broadcast đổi đơn chỉ phục vụ trong cùng thiết bị.
- Cơ sở kiểm thử đã lưu: 46 JVM đạt; Pixel_6 gói demo có 98 Android đạt, 7 bỏ qua theo điều kiện trong 105 ca. Đây là mốc hồi quy, không phải kết quả cho tính năng trong kế hoạch này.
- **Chỉ sử dụng Pixel_6 đã có** khi kiểm thử Android; không tạo hoặc mở AVD khác. Thiết bị thật sẽ làm sau. Firebase Emulator Suite có thể chạy trên máy tính để hỗ trợ kiểm thử backend.
- Không tự gán phân công thành viên, ngày nộp hoặc phần trăm hoàn thành khi chưa có thông tin xác nhận.

Nguồn hiện trạng: [OrderSchema](../app/src/main/java/com/example/bloomybeauty/data/order/OrderSchema.kt), [OrderModels](../app/src/main/java/com/example/bloomybeauty/data/order/OrderModels.kt), [ProfileRepository](../app/src/main/java/com/example/bloomybeauty/data/profile/ProfileRepository.kt), [ưu đãi Firestore](firestore-realtime.md), [FCM](thong-bao-day.md), [kiểm thử Pixel 6](kiem-thu/pixel6-care-2026-11-07/README.md).

## 2. Phạm vi bàn giao

**Chỉ cột A thuộc phạm vi demo môn học.** Cột B là hướng mở rộng tham khảo; không cần triển khai để nghiệm thu hoặc trình diễn. Thử nghiệm B0/B1 có thể bỏ qua khi chạy APK `.demo`.

| Mã | Tính năng | Phiên bản cục bộ A | Phiên bản Firebase B |
|---|---|---|---|
| E1 | Đánh giá sản phẩm | Đánh giá đơn đã giao, điểm trung bình, sửa/xóa đánh giá của mình | Kiểm chứng đơn theo UID trên server; đánh giá chung giữa thiết bị |
| E2 | Mã giảm giá | Coupon mẫu trên `.demo`/`.qa`, snapshot và tính tiền đầy đủ | Backend kiểm tra chiến dịch/quota, tính giảm và tạo đơn nguyên tử |
| E3 | Thống kê quản trị | Đơn, doanh thu sản phẩm đã giao, bán chạy, sắp hết hàng | Thống kê nguồn đơn/kho dùng chung |
| E4 | Sổ địa chỉ | Nhiều địa chỉ, mặc định, chọn nhanh vào checkout | Đồng bộ riêng theo UID, xử lý xung đột |
| E5 | PDF đơn hàng | Xuất/chia sẻ từ snapshot đơn | Xuất từ đơn server đã xác nhận và được phép xem |
| E6 | Trung tâm thông báo | Lưu thông báo đơn, đọc/chưa đọc, mở đối tượng liên quan | Server tạo inbox; FCM báo có nội dung mới trên thiết bị |
| E7 | Nền tảng Firebase | Thiết kế hợp đồng Repository, giữ demo ngoại tuyến | Auth, quyền server, transaction đơn/kho, đồng bộ và kiểm thử |

A là bản demo hoàn chỉnh trên một thiết bị. Khách và quản trị dùng thiết bị/phiên Firebase khác nhau là hướng mở rộng ngoài phạm vi đã chốt. Cache Firestore hoặc role SQLite không được mô tả là backend xử lý giao dịch.

MVP không gồm ảnh đánh giá, phản hồi nhiều tầng, hoàn tiền/đổi trả, hóa đơn điện tử, chat hoặc thanh toán online. Việc chia sẻ danh mục qua provider liên ứng dụng của kế hoạch trước vẫn là phần riêng, không được nhầm với E5.

## 3. Quy tắc dùng chung cần chốt trong mã trước

1. Repository vẫn là nơi kiểm tra phiên/quyền; ViewModel không quyết định chủ sở hữu từ ID do màn hình truyền vào.
2. Tiền là `Long` nguyên VND, có kiểm tra tràn. Trạng thái và điều kiện hủy đơn giữ theo quy tắc hiện có.
3. Snapshot đơn bất biến: sửa sản phẩm, địa chỉ hoặc chiến dịch không làm đổi lịch sử/PDF.
4. Tách `LocalDemo` và `Cloud` qua Repository/source factory; không cho chuyển nguồn khi đang checkout. Đơn cũ có nguồn rõ ràng, không trộn ID cục bộ với ID server.
5. Không làm mất phiên/giỏ/yêu thích/lịch chăm sóc/đơn khi nâng schema. Mỗi migration có dữ liệu phiên bản trước làm fixture thật.
6. Trạng thái giao hàng và dấu thời gian phải lưu trong cùng transaction với audit. Backfill `delivered_at` từ audit DELIVERED nếu có; nếu không có thì để thiếu, không lấy ngày đặt làm ngày giao.
7. Các thao tác đọc lại sau notification/broadcast luôn kiểm tra quyền ở Repository. Nội dung thông báo không phải nguồn xác nhận trạng thái đơn.

## 4. E1 — Đánh giá sau khi nhận hàng

**Luồng:** Đơn đã giao → chọn sản phẩm → viết đánh giá → hiển thị trong chi tiết sản phẩm. Người dùng sửa/xóa đánh giá của mình.

- Một đánh giá cho một dòng sản phẩm trong một đơn: UNIQUE `(user_id, order_id, product_id)`. Mua số lượng nhiều trong một dòng vẫn chỉ có một đánh giá; đơn mua khác được đánh giá riêng.
- Điểm 1–5; nhận xét sau trim dài 1–1.000 ký tự. Chỉ cho tạo khi đúng chủ đơn và trạng thái DELIVERED, kiểm tra trong transaction; đơn khác tài khoản/chưa giao bị từ chối.
- Kiểm chứng sản phẩm thuộc `order_items` bằng FK kép `(order_id, product_id)`. Không cho sửa các cột chủ sở hữu/chứng minh mua hàng qua biểu mẫu.
- Hiển thị tổng số đánh giá, điểm trung bình tính từ các đánh giá hiện có, danh sách mới nhất và phân trang. Không có đánh giá thì hiển thị trạng thái rỗng, không tự gán điểm 5.
- Update/delete kiểm tra revision; gửi lặp không tạo thêm đánh giá. Xóa đánh giá cập nhật lại tổng/điểm trung bình.
- Bản đầu không thêm ảnh hoặc chức năng admin sửa điểm của khách.

**Dữ liệu:** `product_reviews(_id, user_id, order_id, product_id, rating, comment, revision, created_at, updated_at)`; index theo `product_id, created_at, _id`.

**Điểm sửa:** mới `data/review/`, `feature/review/`; nút đánh giá ở `OrdersScreen`, khu vực đánh giá ở chi tiết sản phẩm; schema/helper và wiring app.

**Nghiệm thu:** đơn chưa giao và ID đơn người khác không viết được; đánh giá lặp không nhân đôi; sửa/xóa đúng chủ; trung bình và phân trang đúng sau mở lại app.

## 5. E4 — Sổ địa chỉ

**Luồng:** Tài khoản → Sổ địa chỉ → thêm/sửa/xóa/đặt mặc định; checkout → chọn địa chỉ → nhận bản sao vào biểu mẫu giao hàng.

- Mỗi tài khoản tối đa 10 địa chỉ; trường gồm nhãn, tên người nhận, số điện thoại, địa chỉ đầy đủ. Tái sử dụng `ShippingValidation`/normalize hiện có.
- Tối đa một địa chỉ mặc định. Địa chỉ đầu tiên tự thành mặc định; xóa mặc định thì chọn địa chỉ còn lại theo thứ tự tạo/ID, hoặc không có mặc định nếu danh sách rỗng. Thực hiện nguyên tử.
- Chọn một địa chỉ tạo snapshot vào draft checkout, không giữ liên kết sống với sổ địa chỉ. Sửa/xóa địa chỉ nguồn không tự sửa draft đang nhập hoặc đơn đã đặt.
- Chỉ lưu trở lại sổ khi người dùng chủ động chọn. Đổi mặc định không ghi đè draft giao hàng đã chỉnh sửa.
- Di chuyển dữ liệu địa chỉ cũ ở `shipping_details` thành địa chỉ mặc định một lần. Sau migration, sổ địa chỉ là nguồn mặc định; `shipping_details` tiếp tục là draft checkout, tránh hai nơi tự ghi đè nhau.
- Màn hình hồ sơ giữ sửa tên tài khoản; thay phần sửa địa chỉ mặc định cũ bằng lối vào sổ địa chỉ.

**Dữ liệu:** `addresses(_id, user_id, label, recipient, phone, address, is_default, revision, created_at, updated_at)`; unique index có điều kiện theo `user_id` khi `is_default=1`.

**Điểm sửa:** `data/address/`, `feature/address/`, `ProfileRepository/ProfileScreen`, `CartRepository/CartViewModel/CartScreen` và nguồn giá trị mặc định.

**Nghiệm thu:** nâng DB giữ địa chỉ; chọn/sửa/xóa/mặc định bền sau mở lại; không đọc chéo tài khoản; sửa địa chỉ không đổi đơn hoặc draft đã chọn.

## 6. E2 — Áp dụng mã giảm giá tại checkout

### Phạm vi nguồn mã

Trong A, chỉ bật tính năng áp mã mẫu ở `.demo`/`.qa` qua `LocalCouponRepository` và seed có thời hạn; ghi rõ là mã demo. Bản thường giữ feed công bố ưu đãi hiện tại cho tới B có backend cấp quyền giảm giá. Không tự coi document quảng bá công khai là quyền nhận mã hay bảo đảm quota.

### Quy tắc tiền và thời gian

- Một mã/đơn; trim và chuẩn hóa mã. Hỗ trợ giảm cố định hoặc phần trăm theo điều kiện đơn tối thiểu, thời gian `[startsAt, endsAt)`, trạng thái đang phát hành và trần giảm.
- `subtotal` là tổng giá hàng trước giảm. `discount = min(subtotal, mức giảm hợp lệ)`; phần trăm làm tròn xuống tới VND, tính bằng phép toán nguyên và chặn tràn.
- `total = subtotal - discount + shippingFee`.
- Giữ chính sách phí giao hàng hiện có: ngưỡng 500.000đ tính theo subtotal **trước giảm**, dưới ngưỡng phí 30.000đ. Ví dụ subtotal 500.000đ giảm 50.000đ → ship 0, tổng 450.000đ; subtotal 200.000đ giảm 20% → ship 30.000đ, tổng 190.000đ.
- A dùng giờ thiết bị và không có quota nhiều thiết bị; không trình bày như cơ chế chống gian lận. B dùng giờ và điều kiện do server xác minh.

### Checkout, snapshot và chống trùng

- Preview hiển thị mã/giảm/ship/tổng và lý do mã không hợp lệ. Tạo đơn kiểm tra lại sản phẩm, mã, điều kiện, phiên và giá trong transaction.
- Mã hết hạn/đổi điều kiện sau review: trả lỗi yêu cầu kiểm tra lại, không âm thầm đặt với tổng khác hoặc tự bỏ mã.
- Fingerprint và payload mới phải gồm định danh/revision mã, mức giảm và báo giá đã xác nhận; đổi mã hoặc tổng tiền cần yêu cầu xác nhận mới. Không dùng riêng chuỗi mã làm chống trùng.
- Snapshot đơn lưu `coupon_code`, `coupon_rule_snapshot`, `discount_vnd`; order cũ mặc định giảm 0. Sửa/hết hạn/xóa mã không đổi lịch sử.
- Payload checkout schema cũ được đọc bằng decoder có phiên bản. Yêu cầu đã commit trả lại đúng đơn cũ; yêu cầu cũ chưa commit được đưa về review để xác nhận lại, không tự gửi lại với giá mới.
- A không áp thêm flash-sale price vào catalog để tránh cộng dồn hai cơ chế. B cũng chỉ áp một nguồn giá/mã theo hợp đồng đã kiểm tra, không tự suy ra chính sách stacking.

**Dữ liệu:** bảng seed `coupon_rules` cho local demo, bổ sung snapshot/discount vào `orders`, version vào checkout payload. Đơn, audit, trừ tồn, dọn giỏ vẫn cùng transaction; retry trả cùng đơn.

**Điểm sửa:** `data/coupon/`, `OrderModels`, `OrderRepository`, `CartViewModel`, màn hình review/thành công/chi tiết đơn và seed theo variant.

**Nghiệm thu:** cố định/%, trần giảm, đơn tối thiểu, biên thời gian, mã hết hạn lúc xác nhận, số lớn, phí ship trước giảm, retry đồng thời, rollback và khôi phục request trước/sau migration đều đúng.

## 7. E3 — Thống kê quản trị

**Luồng:** Quản trị → Thống kê → khoảng ngày → thẻ chỉ số, biểu đồ và danh sách.

- Chỉ ADMIN được truy vấn thống kê ở tầng dữ liệu.
- Doanh thu sản phẩm trong phạm vi demo: tổng `subtotal_vnd - discount_vnd` của đơn DELIVERED; phí ship hiển thị riêng. Không gộp đơn chờ, đang giao hoặc đã hủy thành doanh thu.
- Số đơn theo trạng thái là nhóm chỉ số riêng, lọc theo `created_at`. Doanh thu theo ngày lọc `delivered_at`. UI ghi rõ mốc lọc để không so sánh hai tập dữ liệu như cùng một chỉ số.
- Khoảng ngày dùng múi giờ Asia/Ho_Chi_Minh, chuyển thành `[startInclusive, endExclusive)` trước khi query.
- Đơn cũ đã giao nhưng thiếu dấu thời gian giao được liệt kê trong nhóm “Chưa có ngày giao”; không tự gán vào biểu đồ ngày. Toàn thời gian có thể hiển thị nhóm này riêng để đối chiếu.
- Bán chạy theo tổng số lượng sản phẩm trong đơn DELIVERED; đồng điểm sắp theo ID để ổn định. Ngưỡng sắp hết hàng mặc định stock ≤ 5, chỉ sản phẩm đang bán.
- Tính aggregate trong Repository/query; không tải mọi đơn vào UI để tính. Bản đầu không có thống kê lợi nhuận vì chưa có giá vốn.

**Điểm sửa:** `data/statistics/`, `feature/statistics/`, lối vào Admin; `AdminRepository.transition` lưu `delivered_at`; order snapshot khi E2 thêm giảm giá.

**Nghiệm thu:** đối chiếu kết quả với fixture SQL có đủ trạng thái, giảm giá, phí ship, biên ngày và đơn thiếu ngày giao; khách gọi trực tiếp Repository bị từ chối; dữ liệu rỗng không chia cho 0.

## 8. E5 — Xuất/chia sẻ PDF đơn hàng

**Luồng:** Chi tiết đơn → Xuất PDF → xem/chia sẻ bằng trình chọn ứng dụng Android.

- Phiếu thể hiện mã đơn, ngày đặt, trạng thái, người nhận/địa chỉ, dòng hàng theo snapshot, subtotal, mã/mức giảm, phí ship, tổng và phương thức COD. Tên hiển thị là “Phiếu đơn hàng — bản demo”.
- Không lấy lại giá/tên hiện tại từ catalog để dựng lịch sử. Đơn đã hủy thể hiện trạng thái rõ ràng.
- Khách chỉ xuất đơn mình; admin xuất qua quyền quản trị. Kiểm tra phiên trước đọc dữ liệu và trước khi cấp quyền chia sẻ.
- Dùng PDF native, hỗ trợ tiếng Việt, xuống dòng và nhiều trang; không cắt dòng sản phẩm hoặc tổng tiền ra ngoài trang. Hủy thao tác chọn app không được đánh dấu đơn thành công hay thay đổi trạng thái.
- Tệp tạm nằm trong cache riêng, định danh riêng theo tài khoản/đơn/lần xuất; dọn tệp quá hạn và khi đăng xuất. FileProvider chỉ mở thư mục PDF chuyên biệt, authority theo application ID, cấp quyền đọc tạm cho URI được chia sẻ.
- Không xin quyền đọc/ghi toàn bộ bộ nhớ cho tệp cache. Cách dùng content URI và quyền tạm căn cứ [hướng dẫn chia sẻ tệp Android](https://developer.android.com/training/secure-file-sharing).

**Điểm sửa:** `export/OrderPdfExporter`, cấu hình FileProvider/path XML trong Manifest; nút và trạng thái đang xuất/lỗi ở chi tiết đơn khách/admin.

**Nghiệm thu:** tiếng Việt, tên/địa chỉ dài, đơn nhiều trang, đơn cũ giảm 0, tổng có coupon, tên/giá catalog đã đổi, quyền tài khoản khác, lỗi tạo tệp và hủy share sheet. Kiểm tra trực tiếp nội dung PDF và khả năng mở URI trong app đọc trên Pixel_6.

## 9. E6 — Trung tâm thông báo

**Luồng:** Biểu tượng chuông → danh sách → chạm nội dung để đánh dấu đã đọc và mở đúng đơn/lịch còn được phép xem. Có badge chưa đọc và thao tác đọc tất cả.

- Danh sách theo tài khoản, mới nhất trước, phân trang; bản đầu không xóa toàn bộ lịch sử từ app bằng thao tác ngầm.
- Nguồn MVP: đơn được tạo, bị hủy, được xác nhận/đang giao/đã giao. Nhắc chăm sóc tiếp tục dùng alarm hiện có và deep link hiện có; có thể đưa vào inbox bằng cùng hợp đồng loại đối tượng, nhưng không ghi dòng vô hạn mỗi lần mở màn hình.
- Thông báo đơn phải được ghi bền **trong transaction nghiệp vụ**, cùng audit, với unique `event_id` (có thể dẫn từ order audit event). Sau commit mới phát broadcast cập nhật badge và thử hiển thị notification hệ thống. Không dùng một receiver động đang mở làm nơi duy nhất ghi lịch sử.
- Tạo/hủy/chuyển trạng thái lặp không sinh thêm dòng. Thông báo hệ thống đến lặp không nhân bản inbox. Lỗi hiển thị notification không biến một đơn đã commit thành đặt hàng thất bại.
- Từ chối quyền thông báo hệ thống không làm mất inbox; đã đọc inbox không tự hủy lịch chăm sóc hoặc đổi trạng thái đơn.
- Đăng xuất/đổi tài khoản xóa state, hủy observer/token mở màn hình cũ. Deep link chỉ mang ID/loại; Repository đọc lại đối tượng theo tài khoản. Đối tượng không còn hoặc không có quyền thì hiển thị trạng thái phù hợp.

**Dữ liệu:** `user_notifications(_id, user_id, event_id, type, entity_id, title, body, created_at, read_at)`, UNIQUE `(user_id, event_id)`; index theo chủ sở hữu/thời gian và chủ sở hữu/đọc. Khóa kép cho phép cùng sự kiện có các người nhận được phép khác nhau.

**Điểm sửa:** `data/notification/`, `feature/notification/`, badge Home, bộ phân giải deep link, `OrderRepository`, `AdminRepository` và publisher hiện có. Không lưu điện thoại/địa chỉ vào payload broadcast hoặc màn hình khóa.

**Nghiệm thu:** app đóng lúc commit vẫn có lịch sử khi mở lại; rollback không có thông báo giả; retry không trùng; từ chối quyền vẫn đọc inbox; badge/read-all đúng chủ; mở thông báo cũ sau đổi tài khoản không lộ đơn.

## 10. Migration và thứ tự A

Phiên bản dưới đây là đích dự kiến nếu schema hiện tại vẫn là 6 khi bắt đầu. Không nhảy bỏ migration hoặc sửa lại migration đã phát hành; nếu có thay đổi khác xen vào thì đánh số lại trước khi code.

| Bước | Schema dự kiến | Thay đổi | Lát cắt cần demo được |
|---|---|---|---|
| A0 | 6 → 7 | `delivered_at`, backfill audit; hợp đồng tiền/định danh/mode | Đơn cũ còn nguyên, ngày giao có nguồn rõ ràng |
| A1 | 7 → 8 | `product_reviews` | Đơn đã giao → đánh giá → chi tiết sản phẩm |
| A2 | 8 → 9 | `addresses`, migrate địa chỉ cũ | Hồ sơ → địa chỉ → chọn tại checkout |
| A3 | 9 → 10 | coupon mẫu, order snapshot giảm giá, version checkout payload; index đã chốt cho thống kê | Nhập mã → xem tổng → đặt COD → mở lại đơn |
| A4 | 10 | Query thống kê và UI, không đổi schema | Admin xem doanh thu đã trừ coupon và bán chạy |
| A5 | 10 | FileProvider/PDF, không thêm bảng nếu không cần | Chia sẻ đúng phiếu đơn đã lưu |
| A6 | 10 → 11 | inbox và unique event | Đổi đơn → ghi inbox → badge → mở chi tiết |
| A7 | 11 | Hồi quy, ảnh, tài liệu | Toàn bộ sáu tính năng cùng luồng bán hàng cũ |

A0 tạo nền thời gian thống kê; A1/A2 ít ảnh hưởng tổng tiền; A3 phải xong trước khi chốt A4/A5. A6 mở rộng transaction đang ổn định sau A3. Mỗi bước có build chạy được, migration và kiểm thử liên quan trước khi sang bước sau.

## 11. Giai đoạn B — thiết kế tham khảo, không triển khai BE

Các mục B0–B4 bên dưới lưu lại thiết kế ban đầu. B0/B1 đã thử nghiệm trên Emulator; B2–B4 được loại khỏi phạm vi theo yêu cầu demo môn học. Không có yêu cầu deploy Functions, bật billing hay chuyển bản thường sang Cloud để bàn giao.

### B0. Hợp đồng và môi trường

- Thêm Firebase Auth và backend tin cậy (đề xuất Cloud Functions + Firestore) qua các Repository interface, không đưa SDK trực tiếp vào Composable.
- Bản demo/QA ngoại tuyến giữ LocalDemo. Bản thường chuyển Cloud theo cấu hình build đã kiểm chứng; dữ liệu và request ID có namespace theo nguồn.
- Chốt yêu cầu triển khai project, quyền truy cập, môi trường test và điều kiện dịch vụ trước khi ấn định mốc chạy online. Kiểm thử trước bằng Firebase Emulator Suite trên máy tính và Pixel_6.

### B1. Danh tính, vai trò và tài khoản cũ

- Firebase UID là danh tính server; trạng thái đăng nhập và logout hủy listener theo UID. Quản trị server do môi trường tin cậy cấp, không lấy từ `users.role` cục bộ hoặc màn hình hồ sơ.
- Tài khoản SQLite không tự trở thành tài khoản Firebase. Đăng ký/đăng nhập Firebase riêng; không upload mật khẩu/hash SQLite.
- Chuyển hồ sơ/sổ địa chỉ cần đồng thời chứng minh quyền với tài khoản local đang mở và phiên Firebase đích. Không tự gộp hai tài khoản chỉ vì email giống nhau. Nếu cần xác minh email, thêm bước xác minh trong luồng này. Cơ sở tích hợp: [Firebase Authentication Android](https://firebase.google.com/docs/auth/android/start).
- Đơn cục bộ cũ ở mục lịch sử demo; không nhập thành doanh thu/đơn hợp lệ của server. Giữ khả năng xem/xuất PDF dữ liệu cũ đúng tài khoản local, không cho UID khác kế thừa lịch sử bằng cách sửa mapping.

### B2. Đơn, kho và coupon phía máy chủ

- API dự kiến: `quoteCheckout`, `createOrder(requestId, quoteId, shipping)`, `cancelOrder`, `transitionOrder`. Quote có hạn dùng, điều kiện/revision rõ ràng; thay đổi cần xác nhận lại.
- Server đọc giá, hoạt động sản phẩm, tồn, chiến dịch, thời gian và quyền; tính lại tiền, không tin tổng do app gửi.
- Transaction tạo đơn kiểm tra quota mã/giới hạn mỗi UID, ghi redemption, trừ kho, lưu snapshot, audit, inbox/outbox và kết quả requestId. Hủy hợp lệ hoàn kho/giải phóng quyền dùng mã đúng một lần theo trạng thái redemption; replay không hoàn lần hai.
- Đặt/hủy/sửa trạng thái cần mạng. Chỉ hiển thị thành công khi server xác nhận, không khi mới ghi cache. Nếu mất phản hồi sau commit, tra lại cùng requestId, không tạo yêu cầu mới.
- Firestore transaction có thể chạy lại và thất bại khi offline; vì vậy không gửi FCM hoặc tạo tác dụng ngoài transaction ngay trong callback. Ghi outbox nguyên tử rồi worker gửi, dedupe theo event ID. Căn cứ: [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).

### B3. Đồng bộ sáu tính năng và FCM

| Dữ liệu | Nguồn và quy tắc Cloud |
|---|---|
| Sản phẩm/kho/đơn/audit | Server giữ nguồn chính; admin đổi qua API; khách chỉ đọc đơn của UID mình |
| Địa chỉ | `users/{uid}/addresses`; revision/expectedVersion, xung đột yêu cầu tải lại thay vì ghi đè âm thầm |
| Đánh giá | Server chứng minh UID sở hữu dòng đơn DELIVERED; uniqueness và aggregate cập nhật nguyên tử |
| Coupon | Campaign công khai chỉ để giới thiệu; quote/checkout server cấp quyền và tiêu thụ quota |
| Thống kê | Query/aggregate từ đơn server, chỉ admin; cùng định nghĩa thời gian/doanh thu như A |
| PDF | Dữ liệu từ order snapshot được xác nhận; không xuất một draft chưa commit như đơn thành công |
| Inbox | `users/{uid}/notifications/{eventId}`; server tạo, chủ tài khoản chỉ sửa trạng thái đã đọc |
| Thiết bị nhận FCM | Đăng ký token dưới UID, cập nhật khi đổi token; logout gỡ liên kết, deep link vẫn kiểm tra quyền |

- FCM chỉ báo nội dung mới; inbox trên server là nguồn lịch sử để phục hồi khi mất/chậm/nhận lặp push. Không chứa địa chỉ/điện thoại trong payload.
- Đọc cached order có nhãn nguồn; thao tác quan trọng cần xác nhận online. Cache Firestore có cơ chế đồng bộ và last-write-wins, nên không dùng ghi cache tùy ý để giải quyết xung đột đơn/kho. Căn cứ: [Firestore offline](https://firebase.google.com/docs/firestore/manage-data/enable-offline).
- Phân trang đơn/review/inbox theo thời gian + ID; indexes/rules đặt trong repo. Rules chỉ cho dữ liệu theo UID và vai trò server; client không ghi trực tiếp giá/kho/tổng/trạng thái/role.
- UI và ViewModel chỉ quan sát một nguồn cho mỗi thực thể. SQLite local demo và cache của Cloud không cùng được trừ tồn hoặc phát sinh hai đơn cho một thao tác.

### B4. Nghiệm thu đồng bộ chỉ dùng Pixel_6

Kiểm tra hai phiên người dùng bằng test client/Emulator Suite trên máy tính và app Pixel_6: admin cập nhật → khách thấy trạng thái; hai request đồng thời tranh đơn vị tồn/mã cuối; sai UID bị từ chối; mất phản hồi rồi retry trả đúng đơn; logout không nhận/xem nội dung của UID trước.

Không gọi đây là kiểm thử hai điện thoại hoặc thiết bị thật. Chạy rules, API và nghiệp vụ trên emulator project trước; kiểm tra live được ghi riêng với cấu hình/fixture có thể phục hồi, không ghi đè dữ liệu đang dùng.

## 12. Ước lượng ban đầu và mốc bàn giao

Các ước lượng B là lịch sử thiết kế, không còn là kế hoạch thực hiện hoặc điều kiện hoàn thành. Mốc bàn giao hiện tại là bản demo A0–A7 đã nghiệm thu.

Ước lượng cho **một người đã quen codebase, làm đủ thời gian**, chưa phải hạn nộp hoặc cam kết nhóm. Cộng thời gian chờ môi trường Firebase nếu có.

| Công việc | Ngày công dự kiến |
|---|---:|
| A0 — hợp đồng, migration nền | 1–2 |
| A1 — đánh giá | 2–3 |
| A2 — sổ địa chỉ | 2–3 |
| A3 — coupon và checkout | 3–4 |
| A4 — thống kê | 2–3 |
| A5 — PDF | 2–3 |
| A6 — trung tâm thông báo | 3–4 |
| A7 — tích hợp, hồi quy, ảnh/báo cáo | 2–3 |
| **Tổng A** | **17–25** |
| B0/B1 — nguồn Cloud, Auth, vai trò/chuyển hồ sơ | 3–4 |
| B2 — quote, đơn/kho/coupon, chống trùng | 6–8 |
| B3 — đồng bộ review/địa chỉ/thống kê/inbox và FCM | 4–6 |
| B4 — tích hợp, rules, lỗi mạng và nghiệm thu | 3–4 |
| **Tổng B** | **16–22** |
| **Tổng đầy đủ A + B** | **33–47 ngày công, khoảng 7–10 tuần làm việc** |

Mốc M1: review + sổ địa chỉ chạy xuyên suốt. M2: coupon, thống kê, PDF cùng dùng một snapshot tiền. M3: inbox bền và hồi quy sáu tính năng cục bộ đạt. M4: danh tính/quyền Cloud đạt. M5: checkout/kho/coupon server chịu được retry/đồng thời. M6: sáu tính năng đồng bộ, hồ sơ kiểm thử và ảnh Pixel_6 đầy đủ.

## 13. Ma trận kiểm thử bắt buộc

| Nhóm | Ca chính | Tiêu chí |
|---|---|---|
| Migration | Fixture schema 6 có giỏ, địa chỉ, đơn, audit, lịch; nâng từng phiên bản và nâng thẳng tới đích | Không mất dữ liệu; FK/integrity đúng; payload pending cũ được phục hồi có kiểm soát |
| Đánh giá | Chưa giao, đơn người khác, retry, sửa revision cũ, xóa và tính lại điểm | Chặn sai quyền; không trùng; aggregate đúng |
| Địa chỉ | Hai thao tác đặt mặc định, xóa cuối cùng, chọn rồi sửa địa chỉ nguồn, đổi phiên | Một mặc định tối đa; draft/snapshot độc lập; không lộ dữ liệu |
| Coupon | Hết hạn sau review, trần %, giảm lớn hơn subtotal, ngưỡng ship, số lớn, đổi mã khi retry | Tổng và fingerprint đúng; không đặt với báo giá khác |
| Thống kê | Đơn đủ trạng thái, giảm/ship, đúng biên ngày, delivered_at thiếu, quyền khách | Kết quả đối chiếu fixture; không tính sai doanh thu/ngày |
| PDF | Tiếng Việt, nhiều trang, địa chỉ dài, giá đổi, hủy share, đọc URI ngoài thư mục | File mở được, tiền khớp, quyền đúng, không lộ thư mục khác |
| Inbox | Đóng app lúc commit, rollback, trùng event, đọc tất cả, không có quyền notification | Lịch sử bền, không trùng, badge và deep link đúng tài khoản |
| Cloud (ngoài phạm vi demo) | Sai UID/role, sửa trực tiếp totals/stock, hai checkout tranh tồn/quota cuối, mất phản hồi | Server từ chối sai quyền; không âm tồn/quota; idempotency đúng |
| Cloud cache/phiên (ngoài phạm vi demo) | Offline, online lại, chuyển UID khi listener đang tải, xung đột địa chỉ | Không báo thành công giả; không hiển thị cache riêng của UID trước |
| Hồi quy | Đăng nhập, danh mục, giỏ, COD/hủy, admin, hồ sơ, lịch chăm sóc/Doze, ưu đãi hiện tại | Giữ hành vi đã có; kiểm tra bản thường và demo theo cấu hình |

JVM kiểm tra quy tắc tiền, lịch/thời gian, aggregate và mapping. Android instrumentation kiểm tra SQLite/Provider/Repository, file URI, Compose và vòng đời. Rules/API/concurrency dùng Firebase Emulator Suite; kiểm tra giao FCM qua mạng và ngủ tự nhiên nhiều giờ ghi thành mục riêng, không suy ra từ fake Intent.

Quy trình chạy: build + JVM + lint → cài đè app/test bằng ADB trên **Pixel_6** → chạy nhóm liên quan → hồi quy khi có thay đổi tích hợp → chụp ảnh tại mốc trạng thái → lưu log/JSON và cập nhật báo cáo. Không dùng lệnh kiểm thử tự gỡ gói chứa dữ liệu cần giữ; fixture tạo tài khoản riêng và phục hồi trạng thái.

## 14. Kịch bản demo và định nghĩa hoàn thành

1. Chọn địa chỉ đã lưu, nhập coupon demo, xác nhận tổng và đặt một đơn COD.
2. Admin xử lý tới DELIVERED; thống kê tăng doanh thu sản phẩm đúng số sau giảm.
3. Khách nhận nội dung trong inbox, mở đơn, viết đánh giá; điểm sản phẩm cập nhật.
4. Xuất PDF, mở bằng ứng dụng nhận; số tiền và địa chỉ khớp snapshot dù hồ sơ/catalog đã đổi.
5. Thử lại thao tác đặt/chuyển trạng thái; không nhân đơn, review hoặc thông báo.
6. Tham khảo ngoài phạm vi: ở B, lặp chuỗi với server và hai phiên xác thực; ghi rõ client máy tính/Pixel_6, không mô tả như đã chạy hai điện thoại.

Mỗi tính năng chỉ được đánh dấu xong khi có UI, dữ liệu thật qua Repository, kiểm tra quyền/phiên, migration nếu cần, trạng thái tải/lỗi/rỗng, test nghiệp vụ liên quan, ảnh Pixel_6 và tài liệu đúng phạm vi. **Phạm vi demo môn học hoàn thành khi A0–A7 đạt các tiêu chí này.** Giao dịch/đồng bộ server và nghiệm thu B không phải điều kiện hoàn thành; kết quả B0/B1 chỉ là minh chứng thử nghiệm bổ sung.

Checklist triển khai:

- [x] A0 — hợp đồng và dữ liệu nền (phân nguồn build/UID hoàn tất ở B0).
- [x] A1/E1 — đánh giá.
- [x] A2/E4 — địa chỉ.
- [x] A3/E2 — mã giảm giá.
- [x] A4/E3 — thống kê.
- [x] A5/E5 — PDF.
- [x] A6/E6 — trung tâm thông báo.
- [x] A7 — hồi quy sáu tính năng và tài liệu.
- Tham khảo đã có: B0/B1 — Auth và quyền server trên Emulator, không bắt buộc cho demo.
- Ngoài phạm vi, không triển khai: B2 — giao dịch đơn/kho/mã trên server.
- Ngoài phạm vi, không triển khai: B3 — đồng bộ toàn bộ tính năng và FCM theo đơn server.
- Ngoài phạm vi: B4 — nghiệm thu tích hợp/live của backend.

Phạm vi triển khai đã chốt: A0 → A1 → A2 → A3 → A4 → A5 → A6 → A7. Nhật ký dưới đây giữ lịch sử từng mốc; đề xuất B trước khi đổi phạm vi không còn là công việc phải làm.

## 15. Nhật ký triển khai

A0: đã thêm schema 7 với delivered_at và index trạng thái/ngày giao. Backfill chỉ từ audit DELIVERED; dữ liệu thiếu bằng chứng giữ NULL. Admin ghi thời điểm giao trong transaction chuyển trạng thái. Đã cập nhật fixture migration chạy được trên SQLite cũ.

Kiểm tra trên Pixel_6, package demo: CommerceFoundationTest, CareMigrationTest và AdminRepositoryTest đạt 14/14 ca, không lỗi. Build debug, JVM và lint đạt. Đây là kết quả riêng ở mốc A0; không coi phần nền này là hoàn tất sáu tính năng.


A1: đã thêm schema 8 `product_reviews` (khóa ngoại tới dòng đơn, unique theo tài khoản/đơn/sản phẩm). Repository kiểm tra phiên, chủ đơn và trạng thái DELIVERED trong transaction; giới hạn 1–5 sao, nhận xét đã trim 1–1000 ký tự. Khóa sửa gồm ID và revision, chặn cả bản nháp cũ sau khi xóa/tạo lại. Mỗi lần mua lại có đánh giá riêng.

Giao diện: chi tiết đơn đã giao có nút viết/sửa/xóa từng sản phẩm; chi tiết sản phẩm hiển thị điểm trung bình, số đánh giá và danh sách mới nhất (30 mục/trang, nút xem thêm). Biểu mẫu giữ nội dung và khóa sửa khi khôi phục trạng thái màn hình. Chưa có ảnh đánh giá và chưa đồng bộ Firebase.

Nghiệm thu A1 trên Pixel_6/Android 16: **9/9 ca đạt**, không bỏ qua; gồm 3 UI, 5 repository/migration và 1 nền ngày giao. JVM **46/46** đạt, lint **0 lỗi/38 cảnh báo**. Đã lưu 6 ảnh, log và manifest tại [kiểm thử A1](kiem-thu/pixel6-reviews-2026-11-07/README.md). Đã sửa khóa xóa cho bản nháp khôi phục và chỉ mở một hộp thoại tại một thời điểm. A2–A7 và giai đoạn B chưa triển khai. A0 đã hoàn tất phần dữ liệu ngày giao; hợp đồng tiền/định danh/mode tiếp tục được kiểm chứng khi triển khai các tính năng liên quan.

Cập nhật 08/11: đã chốt lại A1 bằng một cửa sổ hộp thoại cố định, kiểm tra focus đúng cửa sổ và chụp lại 6 ảnh sạch. Lượt cuối 9/9 đạt trong 422,818 giây. A2 đã bắt đầu repository và màn sổ địa chỉ, chưa tích hợp schema/checkout hoặc nghiệm thu.

A2 hoàn thành (08/11): schema 9, sổ tối đa 10 địa chỉ, mặc định nguyên tử và khóa ID/revision. Nhập địa chỉ cũ một lần; tách hồ sơ sửa tên khỏi draft checkout; chọn địa chỉ lưu snapshot giao hàng và lưu sổ chỉ qua hành động rõ ràng. Lượt cuối **24/24 Android**, **46/46 JVM**, lint **0 lỗi/38 cảnh báo**; đã xem trực tiếp 5 ảnh. [Bằng chứng A2](kiem-thu/pixel6-address-2026-11-08/README.md). Bắt đầu A3; các mốc A3–A7 và B chưa hoàn thành.

A3 đang triển khai schema 10: bảng mã mẫu chỉ seed/bật ở `.demo`/`.qa`; snapshot mã/giảm giá vào đơn. Payload v2 giữ báo giá/fee và fingerprint phiên bản, decoder v1 giữ retry đơn đã commit, yêu cầu chưa commit phải xác nhận lại. Đã nối UI áp/bỏ mã, khóa đặt khi mã chưa áp dụng hoặc báo giá không hợp lệ. JVM 58 ca và lint 0 lỗi đã đạt ở bản đang phát triển. Lượt Android dữ liệu đầu có 32 ca đạt và 1 lỗi khởi tạo lớp migration (hàm setup trả sai kiểu), đã sửa; ca migration và UI đang chờ xác nhận. Chưa đánh dấu A3 hoàn thành.

A3 hoàn thành (08/11): schema 10, snapshot giảm giá, fingerprint/payload v2 và tương thích request v1. Lượt cuối **37/37 Android**, **58/58 JVM**, lint **0 lỗi/38 cảnh báo**. Đã xem trực tiếp 4 ảnh và lưu trace clock; animation tạm tắt đã được khôi phục. [Bằng chứng A3](kiem-thu/pixel6-coupon-2026-11-08/README.md). A4 đã bắt đầu repository aggregate theo quyền ADMIN, khoảng ngày Việt Nam và nhóm thiếu ngày giao; chưa nghiệm thu A4.

A4–A6 nghiệm thu (08/11): thống kê theo ngày giao/quyền ADMIN; PDF snapshot tiếng Việt/nhiều trang và mở bằng reader bên ngoài; inbox schema 11 ghi cùng transaction audit. Hồi quy cuối 172 ca: 164 đạt, 8 skip có điều kiện, không lỗi; lượt riêng denied 4/4; JVM 62/62; lint 0 lỗi/43 cảnh báo. [Bằng chứng và các lượt trung gian](kiem-thu/pixel6-commerce-integration-2026-11-08/README.md). A0 còn phần phân nguồn/hợp đồng Cloud gắn B0; A7 đang chốt tài liệu và kiểm tra biến thể thường/QA. B chưa triển khai.

A7 hoàn thành (08/11): tài liệu kỹ thuật/ERD cập nhật schema 11; bản thường build với coupon local tắt, release QA build và cold start đạt. Đã lưu log, ảnh đã xem, XML JVM/lint và hash APK. Bộ bàn giao local được đóng gói lại; A0 còn hợp đồng/phân nguồn Cloud ở B0, không đánh dấu hoàn thành toàn bộ kế hoạch Firebase.

B0/B1 (08/11): thêm CloudAuthGateway/CloudAccounts, Firebase Auth UID + nonce, Cloud Functions accountInfo/verifyAdmin và rules owner/server-only. Biến thể `.cloud` debug riêng; local/demo/QA giữ SQLite, không gộp tài khoản theo email hoặc nhập đơn local lên server. Tắt Care Provider/receivers trong Cloud để cài đè/khởi động không tạo DB local. Backend 7/7 và rules quảng bá cũ 9/9 đạt; Pixel_6 đã qua các ca danh tính/vai trò và hai lượt cold-start thật; hồi quy local 48/48, JVM 62/62. [Hướng dẫn](firebase-auth-emulator.md), [log/ảnh/manifest](kiem-thu/pixel6-cloud-identity-2026-11-08/README.md). B2 tiếp theo: quote, transaction đơn/kho/quota, request replay, audit/inbox/outbox server. B3/B4 và cấu hình live chưa hoàn thành.

Điều chỉnh phạm vi theo người dùng (08/11/2026): đây là demo môn học, không cần triển khai BE. Chốt bản local schema 11 và sáu tính năng đã nghiệm thu làm bản bàn giao chính. Giữ B0/B1 như thử nghiệm tùy chọn; dừng kế hoạch B2–B4, không deploy backend và không xem chúng là hạng mục còn thiếu của đồ án.
