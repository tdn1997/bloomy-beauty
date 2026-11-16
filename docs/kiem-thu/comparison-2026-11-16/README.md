# Kiểm thử So sánh sản phẩm — 16/11/2026

## Kết quả nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| Build demo `assembleDebug`, `assembleDebugAndroidTest` | Đạt |
| Unit test | 76/76 đạt, 0 lỗi, 0 bỏ qua; gồm 5 ca mới ComparisonSelectionTest |
| Lint debug | 0 lỗi, 71 cảnh báo, 1 thông tin |
| Android mới | ComparisonFlowTest 6/6 và ComparisonIntegrationTest 1/1 đạt |
| Hồi quy | FavoritesFlowTest 3/3, HomeFlowTest 2/2, CartFlowTest 7/7, ShopRecreationTest 2/2 đạt |
| Tổng Android cuối | 21 ca khác nhau đạt, chia ba nhóm, 0 bỏ qua |
| Môi trường | Pixel_6 hiện có, API 35/Android 15, ARM64, emulator-5554; package `.demo` |
| Ảnh | 5 ảnh giao diện Compose thật, trong `pixel6-comparison/` |

Không gọi đây là kiểm thử toàn bộ ứng dụng. Chưa nghiệm thu thiết bị thật hoặc
API 28. Không thay schema SQLite 11 và không triển khai backend.

## Những gì được kiểm tra

- Quy tắc chọn: tối đa 3, bỏ/chọn lại, không trùng, giữ thứ tự, loại ID không
  tồn tại/ngừng bán; hết hàng vẫn có thể so sánh; thông tin thiếu được ghi rõ.
- UI: chọn từ thẻ/chi tiết, cần 2 mục trước khi mở, ba cột/cuộn ngang, bỏ/xóa
  toàn bộ, xem chi tiết và quay lại, lọc không mất lựa chọn, recreate activity,
  đổi tài khoản, giá/tồn thay đổi, ẩn bảng khi loading/lỗi và retry/tải lại.
- Integration: dùng AuthRepository/CatalogRepository/CartRepository thật với
  database riêng `comparison-integration-test.db`, đi qua BloomyApp và CartViewModel.
  Chọn hai mục → thêm cleanser từ bảng → kiểm tra SQLite → mở giỏ thấy lượng 1
  → quay lại mở bảng vẫn có hai lựa chọn. Không tạo đơn ngoài ý muốn.
- Ảnh từ ComparisonFlowTest dùng fixture HomeUiState/CatalogSeed trong bộ nhớ,
  có điều khiển giá/tồn để kiểm tra phản ứng giao diện; đây không phải ảnh từ
  một phiên mua hàng liên tục. Ca integration riêng xác nhận SQLite/luồng thật.

## Các lượt chạy và giới hạn

Các lượt gộp ban đầu gặp timeout ở ca checkout/đổi hướng màn hình của bộ cũ,
kèm lệnh ADB phản hồi chậm. Có một assertion của CartFlowTest trông chờ nội dung
giao hàng nằm sẵn trong viewport. Đã bổ sung đóng bàn phím, chờ idle trước khi
bấm lưu và cuộn đến nội dung trước khi kiểm tra hiển thị; không đổi nghiệp vụ
checkout để làm test đạt.

Giữ log các lượt lỗi/dừng sớm: `android-tests.log`, `regression-final.log`,
`acceptance-final.log`. Không cộng chúng vào số ca đạt. Sau khi khởi động lại
Pixel_6 ở chế độ `-no-window`, ba nhóm sau hoàn tất:

| Nhóm cuối | Log/metadata | Kết quả |
|---|---|---|
| UI và integration mới | `comparison-acceptance.log`, `.environment.json` | OK (7 tests) |
| Home, Yêu thích, giỏ | `regression-acceptance.log`, `.environment.json` | OK (12 tests) |
| Recreate/đổi hướng và chữ lớn của checkout | `recreation-acceptance.log`, `.environment.json` | OK (2 tests) |

Ba nhóm đều báo `passed: true`, 0 bỏ qua, `restoreErrors: []`, đã khôi phục
animation. Với các lượt dừng để reboot, thiết lập animation đã được trả thủ
công về giá trị ban đầu trước các lượt cuối. Sau kiểm thử đã mở lại Pixel_6
có cửa sổ và giữ bản demo mới để dùng tiếp.

## Tái lập

Dùng JDK 21; cấu hình Gradle toàn máy đang trỏ vào JDK 25 đi kèm Android Studio
nên ghi đè `org.gradle.java.home` khi chạy CLI:

```sh
JAVA_HOME="$PWD/.local/jdk21/Contents/Home" ./gradlew \
  "-Dorg.gradle.java.home=$PWD/.local/jdk21/Contents/Home" \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest \
  :app:lintDebug -Ppresentation=true --no-daemon
```

Cài APK demo và APK test lên đúng Pixel_6 trước khi chạy. Lặp lại script dưới
cho ba nhóm, dùng log riêng:

```sh
python3 scripts/run-pixel6-tests.py \
  --classes com.example.bloomybeauty.feature.home.ComparisonFlowTest,com.example.bloomybeauty.feature.home.ComparisonIntegrationTest \
  --log docs/kiem-thu/comparison-2026-11-16/comparison-acceptance.log --disable-animations
```

Nhóm hai: `FavoritesFlowTest`, `HomeFlowTest` trong package `feature.home`,
`CartFlowTest` trong `feature.cart`. Nhóm ba: `feature.app.ShopRecreationTest`.
Log/metadata, XML unit và lint lưu cục bộ theo quy ước `.gitignore`; Markdown
và PNG được giữ trong mã nguồn. Không suy diễn một lần chạy gộp 21/21 từ các nhóm.

## Ảnh minh chứng

Ảnh đã cập nhật sau yêu cầu thu gọn chữ/nút ngày 16/11/2026: chữ bảng 12–13sp,
chữ nút 12sp, nút 36dp và ảnh 64dp. Build/lint đạt; chạy lại 7/7 ca
ComparisonFlowTest/ComparisonIntegrationTest trên Pixel_6 có cửa sổ đều đạt,
0 bỏ qua, animation khôi phục thành công (`compact-ui.log`, metadata cạnh bên;
`compact-build.log`). Đây là chạy lại 7 ca đã có, không cộng thêm vào 21 ca ở trên.

Điều chỉnh padding tiếp theo: các nút cùng chiều rộng theo cột, đệm 10dp ngang/
8dp dọc, cách nhau 8dp, bo góc 10dp, cao tối thiểu 40dp và tự giãn theo chữ.
Tên sản phẩm có tối thiểu hai dòng để căn cụm nút. Build/lint đạt và 7/7 ca
UI/integration chạy lại đạt (`button-padding.log`, metadata cạnh bên;
`button-padding-build.log`). Năm ảnh bên dưới đã thay bằng giao diện padding mới.

| Ảnh | Nội dung |
|---|---|
| [01](pixel6-comparison/01-selection.png) | Chọn sản phẩm, thanh số lượng và mở so sánh |
| [02](pixel6-comparison/02-table-two.png) | Hai sản phẩm và các hàng thuộc tính |
| [03](pixel6-comparison/03-three-products.png) | Vuốt ngang đến cột thứ ba |
| [04](pixel6-comparison/04-empty.png) | Xóa toàn bộ và lối chọn lại |
| [05](pixel6-comparison/05-current-price-sold-out.png) | Giá hiện tại cập nhật, hết hàng khóa thêm giỏ |

[Hướng dẫn](../../20261116000000_so-sanh-san-pham.md) ·
[Kế hoạch](../../20261116000000_ke-hoach-so-sanh-san-pham.md).
