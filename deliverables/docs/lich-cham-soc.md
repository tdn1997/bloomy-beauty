# Lịch chăm sóc da và broadcast thay đổi đơn

Phiên bản triển khai ngày 07/11/2026, theo [kế hoạch Content Provider và Broadcast Receiver](ke-hoach-content-provider-broadcast.md).

## Sử dụng

1. Đăng nhập, mở **Tài khoản → Lịch chăm sóc da → Thêm lịch chăm sóc**; hoặc mở sản phẩm rồi chọn **Tạo lịch chăm sóc với sản phẩm**.
2. Nhập tên lịch, ghi chú tùy chọn, sản phẩm tham khảo, giờ và các ngày lặp. Chọn ít nhất một ngày rồi lưu.
3. Danh sách hỗ trợ sửa, bật/tắt và xóa có xác nhận. Mỗi tài khoản có tối đa 50 lịch.
4. Cho phép thông báo khi được hỏi. Nếu đã từ chối hoặc tắt kênh, chọn **Bật thông báo chăm sóc** để mở Cài đặt Android.
5. Chạm thông báo để mở biểu mẫu của lịch tương ứng. Nếu đã đăng xuất, đăng nhập đúng tài khoản để tiếp tục; tài khoản khác không mở được lịch đó.

Giờ nhắc là giờ địa phương và có thể trễ do cơ chế tiết kiệm pin của Android. Nhắc cục bộ hoạt động ngoại tuyến, kể cả bản `.demo` và `.qa`. Force stop có thể ngăn nhận nhắc cho tới khi mở lại app. Sau reboot, đổi giờ/múi giờ hoặc cập nhật ứng dụng, receiver khôi phục lần nhắc tiếp theo; không phát bù toàn bộ lần đã qua.

Đăng xuất hủy nhắc và notification của tài khoản cũ. Đăng nhập lại khôi phục các lịch đang bật. Lịch lưu trong SQLite trên thiết bị; chưa đồng bộ sang điện thoại khác.

## Thiết kế đã triển khai

- SQLite schema **6**, thêm `care_routines` và index theo tài khoản/trạng thái; migration giữ các bảng hiện có.
- `CareRoutineProvider`, `exported=false`, authority `${applicationId}.care`. Không cấp quyền truy cập lịch cá nhân cho ứng dụng khác.
- URI `/routines` cho danh sách và insert; `/routines/<id>` cho query một lịch, update và delete. Các thao tác cần `expected_user_id`; update/delete cần thêm `expected_revision` để phát hiện thao tác trên bản cũ.
- Provider kiểm tra phiên trong transaction, gán chủ sở hữu từ phiên, giới hạn trường/cách sắp xếp cho phép. Không nhận SQL selection tùy ý.
- `CareRoutineRepository` gọi ContentResolver trên IO, đóng Cursor, nhận thay đổi qua ContentObserver và đưa dữ liệu vào ViewModel/StateFlow.
- AlarmManager đặt alarm không yêu cầu exact-alarm permission; explicit PendingIntent immutable có định danh riêng theo lịch. Kênh `bloomy_care_reminders` tách khỏi FCM.
- `CareReminderReceiver` dùng `goAsync`, kiểm tra lịch/phiên/revision/occurrence đã ghi trước khi thông báo. Mỗi lần nhận chỉ đặt lại chính lịch đó để các lịch cùng giờ không hủy nhau.
- `CareScheduleRestoreReceiver` nhận boot, đổi giờ/múi giờ và cập nhật package. Activity đối chiếu lịch khi resume. Application không đối chiếu trước receiver alarm vì thao tác đó có thể làm mất occurrence đang chờ trong lần khởi động tiến trình từ alarm.
- `OrderRepository` và `AdminRepository` phát broadcast theo package sau commit. Nhánh chống trùng và rollback không phát thay đổi mới. Receiver động chỉ đăng ký khi giao diện đăng nhập ở trạng thái STARTED; dữ liệu vẫn tải lại theo quyền qua Repository.

F3 cập nhật dữ liệu trong cùng thiết bị. Việc admin thao tác trên điện thoại khác cần backend và cơ chế đồng bộ riêng. F4 chia sẻ danh mục với ứng dụng đồng hành còn là phần mở rộng.

## Kiểm thử

```sh
# Dùng JDK 17+ và emulator chuyên kiểm thử.
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest

# Chỉ chạy các lớp mới.
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.bloomybeauty.data.care.CareRoutineProviderTest,com.example.bloomybeauty.data.care.CareMigrationTest,com.example.bloomybeauty.events.OrderChangesTest,com.example.bloomybeauty.feature.care.CareScreenTest
```

Ca `notificationPermissionDeniedStillKeepsRoutine` cần quyền đã bị thu hồi trước khi chạy instrumentation. Sau khi build/cài APK app và test trên emulator:

```sh
adb shell pm revoke com.example.bloomybeauty android.permission.POST_NOTIFICATIONS
adb shell am instrument -w \
  -e class com.example.bloomybeauty.data.care.CareRoutineProviderTest#notificationPermissionDeniedStillKeepsRoutine \
  com.example.bloomybeauty.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm grant com.example.bloomybeauty android.permission.POST_NOTIFICATIONS
```

Các ca receiver tự động mô phỏng giao Intent để kiểm tra xử lý và notification; không thay thế việc nghiệm thu AlarmManager, reboot thật hoặc Doze. Xem kết quả thực tế và giới hạn trong mục tiến độ của kế hoạch.

Kết quả tiếp tục trên API 28/33/35/36, thử đổi giờ/múi giờ, Force stop và deep Doze cưỡng bức nằm trong [ma trận nghiệm thu bằng giả lập](kiem-thu/content-broadcast-emulator-2026-11-07/README.md). Thiết bị thật được để lại cho đợt sau theo yêu cầu.
