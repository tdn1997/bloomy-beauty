# Kiểm tra bổ sung bằng giả lập — 07/11/2026

Tiếp nối [đợt kiểm thử F1–F3](../content-broadcast-2026-11-07/README.md). Theo yêu cầu tiếp tục bằng giả lập, phần nghiệm thu thiết bị thật được để lại cho đợt sau.

Các AVD riêng: `BloomyCareAPI28`, `BloomyCareAPI33`, `BloomyCareAPI35`, `BloomyCareQA` (API 36). ARM64, targetSdk 35; API 36 dùng trang nhớ 16 KB. Không chạy trên AVD cá nhân `Pixel_6`.

## Kết quả

| API | Kiểm thử ứng dụng | Giờ, timezone, Force stop | Alarm thật trong deep Doze |
|---|---|---|---|
| 28 | [F1–F3: 12 đạt, 1 bỏ qua](api28-features.json); [tầng dữ liệu: 62 đạt, 5 bỏ qua](api28-data.json) | Chưa chạy nhóm này trên API 28 | [Đạt, quan sát sau 95 giây](api28-doze.json) |
| 33 | [F1–F3: 12 đạt, 1 bỏ qua](api33-features.json); [từ chối quyền chạy riêng: 1 đạt](api33-permission-denied.json) | [Đạt](api33-environment.json) | [Đạt, quan sát sau 202 giây](api33-doze.json) |
| 35 | [Toàn bộ: 98 đạt, 7 bỏ qua](api35-regression.json) | [Đạt](api35-environment.json) | [Đạt, quan sát sau 182 giây](api35-doze.json) |
| 36 | [Kết quả đợt trước](../content-broadcast-2026-11-07/README.md) | [Đạt](api36-environment.json) | [Đạt, quan sát sau 156 giây](api36-doze.json) |

Các file JSON là kết quả từng lần chạy; số ca ở các nhóm có giao nhau, không cộng thành tổng ca độc lập. Thời gian Doze trong bảng tính từ lúc ép idle đến lúc quan sát thấy notification, không phải độ trễ so với giờ đã chọn.

Ở API 35, 7 ca bỏ qua gồm 4 ca Firestore cần môi trường riêng, 2 ca từ chối quyền cần chạy riêng sau ADB revoke và 1 fixture hệ thống chỉ chạy khi được yêu cầu bởi script QA. Ca từ chối quyền chăm sóc đã có kết quả chạy riêng trên API 33 và trong đợt trước trên API 36. Ở API 28, quyền notification runtime chưa áp dụng nên ca tương ứng được bỏ qua.

Build demo/lint cuối đợt đạt: [metadata build](build.json). Lint có 0 lỗi, 38 cảnh báo, 1 thông tin. APK debug hiện tại là bản `.demo`.

## Kịch bản hệ thống

- Đổi timezone giữa UTC, America/New_York và timezone ban đầu; xác nhận giờ địa phương của lịch giữ nguyên và mốc alarm được tính lại.
- Tắt tự động cập nhật giờ, tiến một giờ qua lần nhắc, xác nhận chỉ đặt lần tiếp theo và không phát bù. Lùi về giờ ban đầu rồi kiểm tra lại.
- Force stop: xác nhận alarm đang hoạt động bị hệ điều hành hủy; mở app lại thì alarm được phục hồi, lịch SQLite còn nguyên.
- Doze: bỏ sạc mô phỏng, tắt màn hình, ép deep idle; app không nằm trong whitelist. Chờ notification từ AlarmManager thật, xác nhận thiết bị vẫn `IDLE` lúc nhận và database đã ghi occurrence. Không phát Intent thủ công trong kịch bản này.
- Sau mỗi kịch bản, khôi phục giờ/timezone/tự động cập nhật giờ, trạng thái pin và Doze; xóa tài khoản QA tạm và phục hồi phiên trước đó.

Ép Doze dựa trên [hướng dẫn kiểm thử Doze của Android](https://developer.android.com/training/monitoring-device-state/doze-standby). Đây là Doze cưỡng bức trong một khoảng kiểm thử hữu hạn, không thay thế thử nghiệm thiết bị ngủ tự nhiên nhiều giờ.

## Điều chỉnh sau kiểm thử

1. Fixture migration cũ dùng `ALTER TABLE ... DROP COLUMN`, không chạy trên SQLite của API 28. Đã thay bằng dựng/copy/đổi tên bảng legacy trong database kiểm thử riêng, giữ dữ liệu để nghiệm thu migration thật.
2. Ca hai thông báo đồng thời cần chờ NotificationManager cập nhật bất đồng bộ trước khi xác nhận. Đã bổ sung chờ theo điều kiện, tối đa 5 giây.
3. Công cụ QA chờ boot hoàn tất và kiểm tra APK đã cài. Sau khi chuẩn bị/đọc fixture bằng instrumentation, kết thúc tiến trình test để broadcast hệ thống khởi động app sạch; việc này tránh instrumentation làm gián đoạn receiver.
4. Kiểm tra Force stop chỉ đọc các alarm đang hoạt động, loại phần lịch sử alarm đã hủy trong `dumpsys`.

Các điều chỉnh này nằm ở kiểm thử và công cụ QA; không đổi nghiệp vụ app trong đợt kiểm tra bổ sung.

## Chạy lại

Build bằng JDK 17+, cài app debug thường và APK instrumentation. Theo lựa chọn mới của người dùng, các lần chạy tiếp chỉ dùng AVD `Pixel_6` đã có. Script kiểm tra tên này và không chấp nhận thiết bị thật. Các AVD BloomyCare trong bảng là lịch sử kiểm thử trước yêu cầu này.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 shell getprop sys.boot_completed
# Chỉ tiếp tục khi kết quả là 1.
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

python3 scripts/qa-care-emulator.py --serial emulator-5554 \
  --scenario features --output /tmp/care-features.json
python3 scripts/qa-care-emulator.py --serial emulator-5554 \
  --scenario environment --output /tmp/care-environment.json
python3 scripts/qa-care-emulator.py --serial emulator-5554 \
  --scenario doze --timeout 300 --output /tmp/care-doze.json
```

`--scenario data` chạy hồi quy tầng dữ liệu; `--scenario regression` chạy toàn bộ instrumentation. Đóng màn hình app và không thao tác với AVD trong lúc chạy các kịch bản hệ thống. Tài khoản tạm chỉ do test APK tạo, không có điểm vào QA trong APK ứng dụng.

Với API 35 trên máy này, Vulkan làm emulator không boot tới ADB. Cấu hình chạy đạt:

```sh
emulator -avd BloomyCareAPI35 -no-window -no-audio -no-snapshot \
  -gpu swiftshader_indirect -feature -Vulkan
```

## Phần để lại

Nghiệm thu thiết bị thật, chính sách tiết kiệm pin của từng hãng và ngủ tự nhiên nhiều giờ được làm sau. Các ca Firestore live/emulator vẫn cần môi trường riêng. F4 chia sẻ danh mục liên ứng dụng và đóng gói lại ZIP bàn giao chưa nằm trong đợt này.
