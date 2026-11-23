# Kế hoạch nâng cấp gợi ý sản phẩm bằng AI

Ngày lập 23/11/2026. Ưu tiên thử **Gemini 3.5 Flash-Lite qua Firebase AI Logic** trên một dự án Spark riêng cho demo nội bộ. Người dùng chọn mục tiêu mua sắm để AI xếp hạng sản phẩm còn hàng. Gợi ý ngoại tuyến hiện tại vẫn phục vụ người dùng khi AI hết hạn mức hoặc mất kết nối.

## API có gói miễn phí

| API | Hạn mức hoặc cách tính | Cách tích hợp vào Bloomy Beauty |
|---|---|---|
| **Gemini Developer API** qua Firebase AI Logic, `gemini-3.5-flash-lite` | Đầu vào và đầu ra miễn phí trong hạn mức Free Tier. Dự án Spark không cần liên kết thanh toán. Hạn mức thực tế xem trong AI Studio của dự án. | **Ưu tiên cho demo nội bộ.** Việt Nam nằm trong vùng hỗ trợ. Model có đầu ra JSON theo schema. SDK Android và App Check dùng cổng Firebase quản lý. [Giá của Google](https://ai.google.dev/gemini-api/docs/pricing#gemini-3.5-flash-lite) · [Hạn mức](https://ai.google.dev/gemini-api/docs/rate-limits) · [Khu vực](https://ai.google.dev/gemini-api/docs/available-regions) · [Firebase Spark](https://firebase.google.com/docs/ai-logic/pricing) · [Model](https://firebase.google.com/docs/ai-logic/models) |
| **GroqCloud**, `openai/gpt-oss-20b` | Bảng Free Plan công bố 30 yêu cầu/phút, 1.000 yêu cầu/ngày, 8.000 token/phút và 200.000 token/ngày theo tổ chức. Lượt dùng thực tế có thể chạm giới hạn token trước giới hạn yêu cầu. | Dùng khi cần JSON schema ở chế độ strict và đã có điểm gọi phía máy chủ để giữ khóa Groq. Kiểm tra hạn mức thực trong Groq Console. [Hạn mức](https://console.groq.com/docs/rate-limits) · [Structured Outputs](https://console.groq.com/docs/structured-outputs) |
| **Cloudflare Workers AI**, `@cf/meta/llama-3.1-8b-instruct` | Workers Free cấp 10.000 Neurons/ngày, với mức tiêu hao phụ thuộc model và số token. Yêu cầu vượt giới hạn trả lỗi trên gói Free. | Worker gọi model qua binding và cung cấp endpoint cho app. Model có JSON Mode. Đây là phương án dự phòng khi cần giữ quyền kiểm soát endpoint mà vẫn dùng gói miễn phí. [Giá](https://developers.cloudflare.com/workers-ai/platform/pricing/) · [JSON Mode](https://developers.cloudflare.com/workers-ai/features/json-mode/) · [Workers bindings](https://developers.cloudflare.com/workers-ai/get-started/workers-wrangler/) |

Chọn Gemini cho demo nội bộ dành cho người trưởng thành. Điều khoản Gemini Developer API giới hạn việc dùng trong ứng dụng hướng tới hoặc có khả năng được người dưới 18 tuổi truy cập. Nếu định phát hành Bloomy Beauty cho đối tượng rộng hơn, đánh giá điều khoản của GroqCloud và Workers AI trước khi chọn nhà cung cấp. Google có thể dùng nội dung gửi và phản hồi ở gói miễn phí để cải thiện dịch vụ. Giới hạn dữ liệu của demo ở thông tin catalog công khai và mã mục tiêu mua sắm chung. [Điều khoản Gemini](https://ai.google.dev/gemini-api/terms)

## Luồng trên Android

1. Gõ tên sản phẩm hoặc một nhu cầu như **làm sạch da mặt**, **dưỡng môi** hay **chăm sóc tóc** trong ô tìm kiếm của Trang chủ hoặc Khám phá. Sản phẩm còn hàng hiện ngay dưới ô tìm kiếm. Chạm một dòng để mở chi tiết hoặc nhấn Tìm trên bàn phím để xem danh sách tìm kiếm.
2. Trong bản demo AI, các nhu cầu đã nhận diện kèm nút **Gợi ý bằng AI** nếu còn ứng viên phù hợp. Chỉ khi người dùng bấm nút, app gửi mã mục tiêu, mã sản phẩm và mã thuộc tính đã kiểm tra từ catalog SQLite. Nội dung gõ, tên tài khoản và Yêu thích được xử lý tại chỗ.
3. AI xếp hạng tối đa 4 ứng viên. Android hiện lý do tiếng Việt từ mã thuộc tính hợp lệ và đọc tên, ảnh, giá từ catalog hiện tại.
4. Lỗi mạng, hết quota, timeout hoặc phản hồi không hợp lệ đưa panel về gợi ý tại chỗ. Gõ truy vấn khác hoặc đóng panel sẽ hủy yêu cầu đang chờ.

## Bật bản demo AI

1. Dùng một dự án Firebase Spark riêng, thêm app Android có package `com.example.bloomybeauty.ai` và chọn Gemini Developer API ở mục **AI Logic**. Tải cấu hình Android của app đó vào `.local/ai-google-services.json`.
2. Build và cài APK riêng bằng JDK 21. Có thể truyền `-PaiFirebaseConfig=/đường-dẫn/google-services.json` để dùng cấu hình ở vị trí khác.

   ```sh
   ./gradlew -PaiRecommendations=true :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Đăng ký debug token của thiết bị thử trong **Firebase → App Check → Manage debug tokens**. Dùng Logcat cục bộ để lấy token khi thử yêu cầu đầu tiên, rồi nhập vào console của dự án Spark. Debug token thuộc thiết bị thử và được giữ trong môi trường nội bộ. Mở lại app, gõ **làm sạch da mặt** và bấm **Gợi ý bằng AI**. [App Check cho Android debug](https://firebase.google.com/docs/app-check/android/debug-provider)
4. Build thông thường hoặc bản `.demo` cung cấp gợi ý tại chỗ. APK thử nghiệm `.ai` là bản debug dành cho thiết bị thử đã đăng ký.

Dự án thử nghiệm trong workspace là [Bloomy Beauty AI Demo](https://console.firebase.google.com/project/bloomy-ai-demo-070408/overview). Cloud Billing đang tắt. Cấu hình Android đã được lưu vào `.local/ai-google-services.json` và App Check đã đăng ký cho Pixel 6 giả lập dùng trong lượt kiểm thử. Thiết bị thử khác cần đăng ký debug token của riêng nó.

## Các bước triển khai

1. **Xác nhận gói và SDK.** Dùng dự án Spark riêng, đăng ký app Android cho bản thử nghiệm bật bằng cấu hình build và chọn Gemini Developer API. Thử một yêu cầu JSON bằng catalog mẫu, xác nhận model và hạn mức thực của dự án trong AI Studio. Thử `firebase-ai` theo BoM `34.7.0` và Kotlin `2.0.21` hiện tại trước khi cân nhắc nâng dependency. Bật App Check cho AI Logic, dùng debug provider riêng cho máy thử và kiểm tra Play Integrity trước khi phân phối bản có AI. Firebase AI Logic dùng tài khoản dịch vụ do Google quản lý để gọi Gemini. [Xác thực với Gemini](https://firebase.google.com/docs/ai-logic/faq-and-troubleshooting?api=dev) · [Thiết lập Firebase AI Logic](https://firebase.google.com/docs/ai-logic/get-started) · [Đầu ra JSON](https://firebase.google.com/docs/ai-logic/generate-structured-output) · [App Check](https://firebase.google.com/docs/ai-logic/app-check) · [Firebase Android BoM](https://firebase.google.com/support/release-notes/android#bom_v34-7-0)
2. **Nối bộ xếp hạng vào màn hình hiện có.** Truyền bộ xếp hạng vào màn hình tìm kiếm để kiểm thử bằng phản hồi giả. Dùng `CatalogRepository` làm nguồn sản phẩm. Định nghĩa các mục tiêu và thuộc tính từ thông tin catalog đã kiểm chứng. Bỏ thuộc tính nếu catalog thay đổi và thông tin hiện tại không còn hỗ trợ thuộc tính đó.
3. **Kiểm tra phản hồi trên thiết bị.** Yêu cầu JSON có `items` gồm `productId` và `reasonCode`. Kiểm tra schema, giới hạn số lượng và loại ID trùng. Đối chiếu mã tiếp nhận với đúng danh sách đã gửi và catalog hiện tại. Chỉ hiện mã lý do được hỗ trợ bởi thuộc tính của sản phẩm. Bỏ phản hồi cũ khi đổi mục tiêu, phiên hoặc danh sách sản phẩm. Các lựa chọn mua hàng sử dụng luồng giỏ hiện có.
4. **Giữ lượt gọi trong hạn mức.** Gửi tối đa một yêu cầu đang chạy, đặt thời gian chờ ban đầu 5 giây và dùng bộ nhớ tạm cho cùng mục tiêu và cùng dữ liệu catalog trong phiên màn hình. Hạ giới hạn Firebase AI Logic theo quota đo được và đặt ngưỡng lượt gọi cho buổi demo. Khi gặp lỗi quota, trả về gợi ý ngoại tuyến và để người dùng chủ động thử lại. Chỉ ghi số lượt gọi, độ trễ, trạng thái và lý do chuyển về ngoại tuyến. Dùng cấu hình tắt AI để quay về luồng SQLite. [Hạn mức Firebase AI Logic](https://firebase.google.com/docs/ai-logic/quotas)

## Kiểm thử và nghiệm thu

- **Tính đúng.** Dùng phản hồi giả để kiểm tra ID lạ, trùng, sai lý do, hết hàng hoặc ngừng bán trong lúc chờ. Kiểm tra kết quả đến muộn sau đổi mục tiêu hoặc đăng xuất. Mọi thẻ hiển thị phải mở đúng sản phẩm trong catalog.
- **Độ phù hợp.** Chuẩn bị 12 tình huống catalog và mục tiêu có đáp án được rà tay. Đo tỷ lệ sản phẩm được đánh dấu phù hợp trong tối đa ba kết quả đầu. Dùng cùng bộ tình huống và ứng viên để đo cách xếp hạng theo quy tắc làm mốc so sánh. Chỉ bật mặc định trong bản thử nếu AI cải thiện chất lượng xếp hạng trên bộ tình huống và lời giải thích khớp dữ liệu sản phẩm.
- **Trải nghiệm.** Chạy trên Pixel 6 giả lập với dữ liệu mẫu và Spark. Kiểm tra một lần gọi thật, bấm liên tiếp, mất mạng, timeout và quota bằng phản hồi giả. Danh sách SQLite luôn có thể mở ngay. Xác nhận bản `.demo` ngoại tuyến và luồng chi tiết, Yêu thích, thêm giỏ vẫn hoạt động trong kiểm thử hồi quy.
- **Bàn giao.** Ghi model, quota đã xác nhận và kết quả so sánh vào hồ sơ kiểm thử. Chụp màn hình một lượt gợi ý AI và một lượt quay về ngoại tuyến. Quyết định giữ Gemini hay chuyển API dựa trên độ phù hợp, tỷ lệ lỗi và hạn mức thực đo được.

### Kết quả kiểm thử trên Pixel 6 API 34

- Gemini 3.5 Flash-Lite đã trả hai sản phẩm hợp lệ cho **làm sạch da mặt**. Panel hiển thị kết quả và lý do lấy từ catalog. Lượt thử trước khi đăng ký App Check hiển thị gợi ý tại chỗ. Ảnh hai trạng thái được lưu cục bộ trong `.local`.
- Kiểm thử tự động bao phủ gợi ý khi gõ, mở chi tiết, yêu cầu AI theo thao tác bấm, lỗi phía nhà cung cấp và đổi truy vấn trong khi chờ phản hồi.
- Quota của dự án và chất lượng xếp hạng trên bộ tình huống rộng hơn cần đánh giá trước khi bật AI mặc định cho người dùng.
