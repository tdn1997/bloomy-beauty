"""Create progress report 2 using the actual first Word report as the style template."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import shutil
import runpy
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/bao-cao/tien-do-lan-2"
PREVIOUS = ROOT / "docs/bao-cao/tien-do-lan-1/Bao-cao-tien-do-lan-1-Nhom-4.docx"
EVIDENCE = ROOT / "docs/kiem-thu/pixel6-care-2026-11-07"
SHARED = runpy.run_path(str(ROOT / "scripts/create-progress-report.py"))
table = SHARED["table"]
MEMBERS = SHARED["MEMBERS"]
SCREENS = [
    ("01-bieu-mau-them", "Thêm lịch chăm sóc", "Nhập tên lịch, ghi chú tùy chọn, sản phẩm tham khảo, giờ nhắc và ngày lặp trước khi lưu."),
    ("02-lich-da-luu", "Lịch đã lưu", "Lịch Serum buổi tối xuất hiện trong danh sách, giờ 21:00, lặp hằng ngày, trạng thái bật nhắc. Dữ liệu được lưu qua ContentResolver và Provider vào SQLite."),
    ("03-sua-lich", "Sửa lịch chăm sóc", "Mở lịch hiện có và đổi tên thành Chăm sóc buổi tối; ca kiểm thử kiểm tra tên mới sau khi lưu và revision tăng."),
    ("04-tat-nhac", "Tắt nhắc nhưng giữ lịch", "Công tắc nhắc đã tắt; lịch vẫn còn trong danh sách để có thể chỉnh sửa hoặc bật lại."),
    ("05-xac-nhan-xoa", "Xác nhận xóa lịch", "Hộp thoại nêu rõ lịch và nhắc hẹn tương ứng sẽ bị xóa, cho phép xác nhận hoặc giữ lại."),
    ("06-da-xoa-lich", "Danh sách sau khi xóa", "Sau xác nhận, ca kiểm thử đối chiếu danh sách không còn lịch và giao diện hiển thị trạng thái rỗng."),
    ("07-khoi-phuc-nhap-va-loi-ngay", "Khôi phục bản nháp và kiểm tra ngày lặp", "Bản nháp còn sau khôi phục saved state. Khi bỏ chọn toàn bộ ngày lặp, nút Lưu lịch bị vô hiệu hóa và hiển thị yêu cầu chọn ít nhất một ngày."),
]


def firebase_section(d):
    d.add_page_break()
    d.add_heading("2.1. Xử lý Firebase trong ứng dụng", 1)
    paragraph(d, "Firebase được bổ sung cho dữ liệu ưu đãi dùng chung và thông báo đẩy. Đăng nhập, vai trò quản trị, sản phẩm, giỏ, lịch chăm sóc và đơn hàng vẫn dùng SQLite; chưa tích hợp Firebase Authentication hoặc chuyển nghiệp vụ đặt hàng lên Firestore.")
    d.add_heading("a. Cấu hình và phạm vi theo biến thể", 2)
    paragraph(d, "Project Firebase: bloomy-beauty, Firestore database mặc định. Gradle dùng google-services.json.template và FIREBASE_API_KEY từ môi trường hoặc .env.local để tạo cấu hình trong thư mục build; Google Services plugin tạo resource khởi tạo Firebase. SDK Analytics, Firestore và Cloud Messaging dùng Firebase BoM. Báo cáo không chứa giá trị API key, registration token hay service-account key.")
    table(d, ["Biến thể", "Cách xử lý"], [
        ("Bản debug/release gốc", "Bật Firebase; đọc ưu đãi qua Firestore và nhận FCM khi thiết bị có mạng/Google Play services và cấu hình phù hợp."),
        ("Bản trình diễn .demo và release QA .qa", "FIREBASE_ENABLED=false; dùng DisabledPromotionsRepository, không khởi tạo Firebase. Nhắc chăm sóc cục bộ và toàn bộ luồng SQLite vẫn hoạt động."),
    ])
    d.add_heading("b. Firestore: dữ liệu và luồng realtime", 2)
    table(d, ["Collection", "Dữ liệu chính", "Xử lý trong app"], [
        ("flash_sales", "published, title, productId, salePriceVnd, startsAt, endsAt.", "Hiển thị chương trình còn hiệu lực/sắp bắt đầu; chỉ hiện với sản phẩm còn bán và giá sale thấp hơn giá catalog."),
        ("coupon_campaigns", "published, title, code, discountType, discountValue, minOrderVnd, maxDiscountVnd, startsAt, endsAt.", "Chỉ hiện mã trong thời gian phát hành; hiển thị điều kiện và cho sao chép khi dữ liệu không đến từ cache."),
    ])
    paragraph(d, "Luồng xử lý: Firebase Console → Firestore → snapshot listener của FirestorePromotionsRepository → Flow → PromotionsViewModel → StateFlow → giao diện Trang chủ. Hai truy vấn lọc published == true, dùng MetadataChanges.INCLUDE để theo dõi cả nguồn cache. Thời gian nhận ở dạng Firestore Timestamp, tiền là số nguyên VND; decoder kiểm tra schema và bỏ qua riêng document sai để các chương trình hợp lệ vẫn hiển thị.")
    paragraph(d, "ViewModel kết hợp feed với đồng hồ cục bộ mỗi giây để đếm ngược và tự cập nhật ranh giới bắt đầu/hết hạn; không truy vấn Firestore mỗi giây. Khoảng đang chạy là startsAt ≤ now < endsAt. Khi không còn UI theo dõi, ứng dụng vào nền hoặc đăng xuất, Flow hủy listener bằng remove(); quay lại trang chủ sẽ tạo kết nối theo dõi mới.")
    d.add_heading("c. Mất mạng, lỗi và kiểm soát truy cập", 2)
    table(d, ["Tình huống", "Cách xử lý"], [
        ("Dữ liệu từ cache / mất mạng", "Hiển thị nhãn dữ liệu đã lưu và khóa sao chép mã; SDK đồng bộ lại khi kết nối được khôi phục. Các chức năng SQLite vẫn dùng được."),
        ("Listener lỗi hoặc thiếu quyền", "Xóa ưu đãi cũ của collection bị lỗi, báo trạng thái, cho tải lại để tạo listener mới."),
        ("Ẩn hoặc hết hạn chương trình", "published=false hoặc xóa document sẽ gỡ khỏi feed khi đồng bộ; ViewModel tự lọc theo thời gian hiệu lực."),
        ("Rules trong mã nguồn", "Chỉ cho đọc document published=true của hai collection ưu đãi; chặn ghi từ client và chặn các collection khác. Dữ liệu được quản trị bằng Console/Admin SDK theo IAM."),
    ])
    paragraph(d, "File firestore.rules đã có trong dự án, nhưng không coi việc có file là đã deploy rules lên project thật. Phiên/role SQLite không cấp danh tính Firebase hoặc quyền ghi Firestore. Mã published=true là dữ liệu công khai, kể cả trước giờ UI hiển thị; mã cần giữ kín phải giữ published=false cho đến lúc phát hành. Không đưa thông tin khách hàng vào các collection ưu đãi công khai.")
    d.add_heading("d. Firebase Cloud Messaging (FCM)", 2)
    paragraph(d, "Ở biến thể bật Firebase, PushNotifications tạo kênh bloomy_updates, lấy registration token khi khởi động và lưu lại khi FCM đổi token qua onNewToken. Token lưu trong SharedPreferences riêng của app, không ghi log; không đưa token hoặc khóa gửi vào báo cáo/APK nguồn. Token hiện chưa liên kết với tài khoản khách hàng ở backend.")
    paragraph(d, "Khi app đang mở, BloomyMessagingService xử lý notification payload hoặc data payload có title/body; ưu tiên nội dung notification, bổ sung tiêu đề mặc định khi thiếu và bỏ qua payload không có nội dung hiển thị. Khi app ở nền, notification payload do FCM hiển thị; data-only tùy điều kiện hệ điều hành nên không cam kết giao ngay trong Doze. Android 13+ cần POST_NOTIFICATIONS; nếu quyền/kênh bị tắt thì không hiển thị, app vẫn hoạt động. Chạm FCM mở app, chưa điều hướng tới một đơn hàng cụ thể.")
    paragraph(d, "FCM nhận tin từ máy chủ khác với alarm nhắc chăm sóc cục bộ và broadcast nội bộ đổi đơn. Firestore listener không tự gửi FCM khi app đóng. Hiện chưa có backend tự gửi tin khi trạng thái đơn thay đổi; quy trình gửi thử là Firebase Console → thiết bị bản thường → cho phép notification → gửi test message đến token và quan sát foreground/background. Đợt tạo báo cáo này không gửi FCM hoặc cập nhật dữ liệu remote.")
    d.add_heading("e. Kết quả đã ghi nhận và phần còn lại", 2)
    paragraph(d, "Hồ sơ Firestore trực tiếp ngày 21/10/2026 ghi nhận 6 hạng mục đạt: đọc dữ liệu thật, hiển thị ưu đãi, cập nhật realtime khi đổi tiêu đề, khôi phục dữ liệu, sao chép mã và mở đúng chi tiết sản phẩm. Thay đổi tiêu đề mẫu trong lần kiểm thử đó đã được khôi phục. Đây là kết quả lưu từ đợt trước, không phải kiểm thử live chạy lại ngày 07/11.")
    paragraph(d, "Dự án có kiểm thử rules trên Emulator Suite, kiểm thử repository/listener và kiểm thử UI ưu đãi; các ca Firestore live/emulator phải bật cờ và có môi trường tương ứng. Trong lần chạy Pixel_6 gói demo, 4 ca Firestore bị bỏ qua; ca service FCM chỉ xác nhận bỏ qua tin đúng cấu hình demo tắt Firebase. Vì vậy 98 ca đạt không được dùng để khẳng định đã nghiệm thu Firebase qua mạng ở lượt này.")
    paragraph(d, "Phần tiếp theo nếu mở rộng: Firebase Authentication hoặc danh tính backend đáng tin cậy; API nhận mã có requestId, kiểm tra giờ máy chủ/quota theo người dùng trong transaction; backend tính lại giá/giảm giá/tồn khi checkout và gửi FCM theo sự kiện đơn. Hiện giá flash sale và mã được công bố để tham khảo/sao chép, chưa được áp dụng vào giỏ hoặc tổng đơn COD SQLite.")
    d.add_page_break()
    d.add_heading("2.2. Minh chứng Firebase từ đợt kiểm thử trước", 1)
    paragraph(d, "Hai ảnh dưới lấy nguyên bản từ hồ sơ 21/10/2026. Không phải ảnh mới trên Pixel_6 của lượt 07/11; không dùng làm minh chứng FCM. Luồng chăm sóc ở mục 3 vẫn dùng bộ 7 ảnh Pixel_6 mới nhất.")
    pictures = [
        ("01-uu-dai-firestore.png", "firebase-01-uu-dai-firestore.png", "FB01", "Hiển thị ưu đãi từ Firestore", "Màn hình hiển thị flash sale và mã khuyến mãi công khai trong đợt kiểm tra ngày 21/10/2026."),
        ("02-cap-nhat-realtime.png", "firebase-02-cap-nhat-realtime.png", "FB02", "Cập nhật realtime qua listener", "Tiêu đề chương trình thay đổi trên giao diện sau khi sửa trong Firestore; lần kiểm thử đã khôi phục tiêu đề gốc."),
    ]
    t = d.add_table(rows=1, cols=2)
    t.autofit = False
    t.rows[0]._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))
    result = []
    for index, (source_name, name, figure, title, desc) in enumerate(pictures):
        source = ROOT / "docs/kiem-thu/firestore-live-2026-10-21" / source_name
        target = OUT / name
        shutil.copyfile(source, target)
        cell = t.rows[0].cells[index]
        cell.width = Cm(8.3)
        cell.paragraphs[0].add_run(f"Hình {figure}. {title}").bold = True
        p = cell.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.add_run().add_picture(str(target), height=Cm(14.3))
        for run in cell.add_paragraph(desc).runs:
            run.font.size = Pt(10)
        with Image.open(target) as im:
            dimensions = list(im.size)
            im.verify()
        result.append({"figure": figure, "file": name, "title": title, "description": desc,
                       "source": str(source.relative_to(ROOT)), "evidence_date": "2026-10-21",
                       "dimensions": dimensions, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    return result


def paragraph(doc, text):
    doc.add_paragraph(text)


def make():
    result = json.loads((EVIDENCE / "regression.json").read_text())["details"]
    assert (result["tests"], result["passed"], result["skipped"], result["failures"]) == (105, 98, 7, 0)
    OUT.mkdir(parents=True, exist_ok=True)
    d = Document(PREVIOUS)
    # Preserve the real previous report's styles, page setup, headers and footer fields.
    body = d._element.body
    for element in list(body):
        if element.tag != qn("w:sectPr"):
            body.remove(element)
    for relationship in list(d.part.rels.values()):
        if relationship.reltype.endswith(("/image", "/hyperlink")):
            d.part.drop_rel(relationship.rId)
    for section in d.sections:
        for area in (section.header, section.footer):
            for p in area.paragraphs:
                for run in p.runs:
                    run.text = run.text.replace("LẦN 1", "LẦN 2").replace("lần 1", "lần 2")
        footer = section.footer.paragraphs[0]
        footer.clear()
        footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
        footer.add_run("Nhóm 4 • Tiến độ lần 2 • Trang ").font.size = Pt(9)
        field = OxmlElement("w:fldSimple")
        field.set(qn("w:instr"), "PAGE")
        footer._p.append(field)
    d.styles["Normal"].font.name = "Times New Roman"
    d.styles["Normal"].font.size = Pt(12)
    for text, style in [
        ("MÔN HỌC: LẬP TRÌNH DI ĐỘNG", None),
        ("BÁO CÁO TIẾN ĐỘ - LẦN 2", "Title"),
        ("ĐỀ TÀI: ỨNG DỤNG BÁN MỸ PHẨM\nBLOOMY BEAUTY", "Subtitle"),
        ("NHÓM 4", None),
    ]:
        d.add_paragraph(text, style).alignment = WD_ALIGN_PARAGRAPH.CENTER
    table(d, ["Họ và tên", "Mã số sinh viên"], MEMBERS)
    d.add_paragraph("Ngày báo cáo: 07/11/2026\nTrạng thái chung: Đang kiểm thử và hoàn thiện").alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph(d, "Kế thừa báo cáo tiến độ lần 1 ngày 14/10/2026. Ảnh minh chứng lần 2 lấy từ đợt kiểm thử mới nhất trên Pixel_6, Android API 36, ngày 07/11/2026.")
    d.add_page_break()

    d.add_heading("1. Mục tiêu và phạm vi báo cáo", 1)
    paragraph(d, "Báo cáo lần 2 cập nhật kết quả từ mốc lần 1 đến ngày 07/11/2026, tập trung vào chức năng lịch chăm sóc da, nhắc hẹn, Content Provider và Broadcast Receiver; đồng thời kiểm thử hồi quy để duy trì luồng mua hàng và quản trị đã trình bày ở lần trước.")
    paragraph(d, "Giữ nền tảng Kotlin, Jetpack Compose, Material 3, ViewModel, Coroutines/StateFlow và SQLite cục bộ. Schema được nâng từ 5 lên 6 bằng migration; bổ sung bảng care_routines, giữ tài khoản, giỏ, đơn và tồn kho cũ. Đơn COD vẫn là dữ liệu mô phỏng trên một thiết bị; chưa có backend xử lý đơn hoặc thanh toán trực tuyến.")
    d.add_heading("Đối chiếu với báo cáo lần 1", 2)
    table(d, ["Nội dung", "Lần 1 — 14/10/2026", "Lần 2 — 07/11/2026"], [
        ("Phạm vi", "Tài khoản, sản phẩm, giỏ, COD, hồ sơ, quản trị và xử lý đơn.", "Giữ phạm vi cũ; bổ sung lịch chăm sóc cá nhân, nhắc hẹn và broadcast đổi đơn."),
        ("Dữ liệu", "SQLite schema 5.", "Schema 6, có care_routines và migration giữ dữ liệu."),
        ("Minh chứng", "30 ảnh theo luồng mua hàng và quản trị.", "7 ảnh bổ sung cho lịch chăm sóc trên Pixel_6; không trình bày lại toàn bộ ảnh cũ."),
        ("Kiểm thử", "Trích hồ sơ lưu sẵn: 24 JVM, 81 Android; không chạy lại toàn bộ trong lượt chụp lần 1.", "Chạy lại: 46 JVM đạt; 98 Android đạt, 7 bỏ qua theo điều kiện trong 105 ca; không có lỗi cuối đợt."),
        ("Thiết bị", "Ảnh trên API 36; API 28 còn cần kiểm tra.", "Đã có kết quả API 28/33/35/36 ở đợt mở rộng; lượt mới nhất chỉ dùng Pixel_6 do người dùng mở."),
    ])
    paragraph(d, "Cách phân biệt trạng thái được giữ như lần 1: đã triển khai không đồng nghĩa đã nghiệm thu toàn bộ; ca bỏ qua không được tính là ca đạt. Chưa có thông tin xác nhận phân công hay tỷ lệ đóng góp thực tế nên chỉ giữ danh sách thành viên, không tự gán công việc cho từng người.")

    d.add_page_break()
    d.add_heading("2. Tiến độ thực hiện lần 2", 1)
    table(d, ["Hạng mục", "Kết quả đến lần 2", "Theo dõi tiếp"], [
        ("Luồng bán hàng và quản trị", "Giữ chức năng lần 1; chạy lại kiểm thử tài khoản, giỏ/COD, quyền, tồn kho, đơn và khôi phục.", "Tiếp tục nghiệm thu trên thiết bị thật."),
        ("F1 — Lịch chăm sóc", "Thêm, xem, sửa, xóa, bật/tắt lịch cá nhân; liên kết sản phẩm tùy chọn; biểu mẫu kiểm tra ngày lặp và dữ liệu.", "Rà soát trải nghiệm thực tế và cỡ chữ trên thiết bị nộp."),
        ("Content Provider", "URI theo application ID, CRUD qua Resolver, Cursor, MIME type, kiểm tra phiên/chủ sở hữu và revision; observer cập nhật UI.", "Provider cá nhân để exported=false; chia sẻ danh mục sang app khác chưa triển khai."),
        ("F2 — Nhắc chăm sóc", "AlarmManager và receiver tĩnh; chặn lịch cũ/nhắc lặp; hủy nhắc khi tắt/xóa/đăng xuất; kiểm tra quyền notification.", "Giờ nhắc có thể trễ do Android; chưa kết luận về ngủ tự nhiên nhiều giờ."),
        ("Khôi phục lịch", "Khôi phục sau boot, đổi giờ/múi giờ và mở lại app; notification mở đúng lịch sau kiểm tra tài khoản.", "Kiểm tra chính sách tiết kiệm pin từng hãng sau."),
        ("F3 — Broadcast đổi đơn", "Phát tín hiệu sau commit; receiver động làm mới dữ liệu theo vòng đời. Rollback và retry không phát thay đổi giả.", "Hoạt động cùng thiết bị, chưa đồng bộ sang điện thoại khác."),
        ("Firestore và FCM", "Bản thường đã có nguồn ưu đãi realtime và nhận FCM. Demo/QA tắt Firebase; nhắc chăm sóc vẫn chạy cục bộ.", "Chưa áp coupon vào tổng đơn; chưa có backend tự gửi FCM theo trạng thái đơn."),
        ("Báo cáo, ảnh, sơ đồ", "Cập nhật báo cáo kỹ thuật Markdown/HTML, sơ đồ schema 6 và 7 ảnh Pixel_6; lập Word lần 2 theo mẫu lần 1.", "Hoàn thiện thông tin học phần và đóng gói bàn giao theo yêu cầu nộp."),
    ])
    d.add_heading("Áp dụng hai nội dung học phần", 2)
    paragraph(d, "content.pdf: xây ContentProvider với URI danh sách/từng lịch, query/insert/update/delete/getType, Resolver/Cursor và kiểm soát truy cập. Với Compose, dùng coroutine và ContentObserver/StateFlow thay cho việc đưa CursorLoader vào màn hình.")
    paragraph(d, "broadcast.pdf: dùng receiver tĩnh cho alarm/khôi phục lịch, receiver động cho tín hiệu đổi đơn, đăng ký/hủy theo vòng đời và giữ onReceive ngắn. Không dùng LocalBroadcastManager đã deprecated; broadcast là tín hiệu tải lại, không thay dữ liệu nghiệp vụ hoặc transaction.")

    firebase_manifest = firebase_section(d)

    d.add_page_break()
    d.add_heading("3. Minh chứng màn hình theo thứ tự sử dụng", 1)
    paragraph(d, "Trình tự: nhập lịch → lưu → sửa → tắt nhắc → xác nhận xóa → danh sách rỗng. Nhánh bổ sung: khôi phục bản nháp và kiểm tra trường hợp chưa chọn ngày lặp. Bảy ảnh chụp từ giao diện Compose chạy trên AVD Pixel_6, package com.example.bloomybeauty.demo; các ca gọi Repository/Resolver/Provider/SQLite thật, dùng tài khoản tạm rồi dọn dữ liệu và phục hồi phiên.")
    paragraph(d, "Ảnh được tái sử dụng từ đợt kiểm thử ngày 07/11/2026, không phải ảnh chụp lại trong lượt tạo Word này. Ảnh giao diện không thay bằng chứng giao alarm thật; các kết quả alarm/Doze được nêu riêng ở mục 4. Các trường hợp khôi phục saved state không được mô tả là đã khởi động lại hệ điều hành.")
    manifest = list(firebase_manifest)
    for index, (key, title, desc) in enumerate(SCREENS, 1):
        source = EVIDENCE / "anh" / (key + ".png")
        target = OUT / source.name
        shutil.copyfile(source, target)
        with Image.open(target) as im:
            dimensions = list(im.size)
            im.verify()
        manifest.append({"step": index, "file": target.name, "title": title, "description": desc,
                         "source": str(source.relative_to(ROOT)), "dimensions": dimensions,
                         "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    for offset in range(0, len(SCREENS), 2):
        d.add_page_break()
        span = f"Các bước {offset + 1:02d}–{offset + 2:02d}" if offset + 1 < len(SCREENS) else f"Bước {offset + 1:02d}"
        d.add_heading(f"3.{offset // 2 + 1}. {span}", 1)
        t = d.add_table(rows=1, cols=2)
        t.autofit = False
        for col in t.columns:
            col.width = Cm(8.3)
        t.rows[0]._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))
        for j, (key, title, desc) in enumerate(SCREENS[offset:offset + 2]):
            cell = t.rows[0].cells[j]
            cell.paragraphs[0].add_run(f"Hình {offset + j + 1:02d}. {title}").bold = True
            p = cell.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.add_run().add_picture(str(OUT / (key + ".png")), height=Cm(14.3))
            for run in cell.add_paragraph(desc).runs:
                run.font.size = Pt(10)
    d.add_page_break()
    d.add_heading("4. Kết quả kiểm tra và công việc tiếp theo", 1)
    paragraph(d, "Lượt mới nhất chỉ sử dụng Pixel_6 do người dùng đã mở, API 36/ARM64. Cài đè app demo và APK instrumentation bằng ADB, không dùng Gradle connected test tự gỡ app. Không tạo hoặc mở AVD khác trong lượt này; dữ liệu gói app chính không bị xóa.")
    table(d, ["Nhóm kiểm tra", "Kết quả và phạm vi"], [
        ("JVM trên máy build", "46/46 ca đạt, không có lỗi."),
        ("Android — nhóm F1–F3", "12 đạt, 1 bỏ qua theo điều kiện, 0 lỗi trong 13 ca."),
        ("Android — toàn app, lần chạy cuối", "98 đạt, 7 bỏ qua theo điều kiện, 0 lỗi trong 105 ca, trên gói demo ở Pixel_6."),
        ("Các ca bỏ qua", "4 ca Firestore cần môi trường riêng; 2 ca từ chối quyền cần thu hồi quyền trước khi chạy; 1 fixture hệ thống chỉ chạy khi script yêu cầu."),
        ("Build/lint demo", "Build đạt; lint 0 lỗi, 38 cảnh báo, 1 thông tin. Cảnh báo chưa được coi là đã xử lý hết."),
        ("Alarm và khôi phục — đợt trước", "Có alarm thật sau reboot/process death và khi ngoại tuyến; deep Doze cưỡng bức đạt trên API 28/33/35/36. Đổi giờ/múi giờ và Force stop đạt trên API 33/35/36. Đây không phải các lần chạy lại trên Pixel_6 ở lượt chụp ảnh mới nhất."),
    ])
    paragraph(d, "Nhóm 13 ca là tập con có giao với bộ 105 ca, không cộng thành 118 ca độc lập. Các kết quả nhiều API ở đợt trước cũng không cộng dồn với kết quả Pixel_6. Ca FCM trong demo xác nhận dịch vụ bỏ qua tin theo cấu hình tắt Firebase, không phải nghiệm thu giao nhận FCM qua mạng.")
    d.add_heading("Các vấn đề đã xử lý sau kiểm thử", 2)
    for text in [
        "Fixture migration dùng DROP COLUMN không chạy trên SQLite API 28: thay bằng dựng/copy/đổi tên bảng legacy trong database kiểm thử.",
        "NotificationManager cập nhật bất đồng bộ: bổ sung chờ theo điều kiện trước khi xác nhận hai thông báo cùng giờ.",
        "Lần hồi quy demo ban đầu có ba lỗi fixture: package assertion cố định, package IME kiểm thử cố định và kỳ vọng FCM hoạt động trong demo tắt Firebase. Đã sửa theo biến thể và chạy lại toàn bộ; lần cuối không còn lỗi. Log lần đầu được giữ để đối chiếu.",
        "Ảnh minh chứng được chụp sau khi giao diện ổn định và hiệu ứng bàn phím kết thúc; cập nhật báo cáo và sơ đồ schema 6.",
    ]:
        d.add_paragraph(text, "List Bullet")
    d.add_heading("Việc ưu tiên ở giai đoạn tiếp theo", 2)
    for text in [
        "Tiếp tục dùng Pixel_6 đã có cho các lượt QA sau; thiết bị thật sẽ nghiệm thu sau theo thống nhất.",
        "Kiểm tra thiết bị ngủ tự nhiên nhiều giờ và chính sách pin của thiết bị nộp; Doze cưỡng bức có thời hạn chưa thay phần này.",
        "Chuẩn bị môi trường riêng nếu cần chạy lại Firestore live/emulator và kiểm tra FCM qua mạng trên biến thể bật Firebase.",
        "Rà soát APK/ZIP bàn giao cũ và đóng gói lại sau khi chốt phạm vi; xác nhận thông tin học phần, phân công thực tế và góp ý của giảng viên.",
    ]:
        d.add_paragraph(text, "List Bullet")
    paragraph(d, "Giới hạn còn lại: dữ liệu tài khoản/đơn/kho vẫn cục bộ, chưa có backend xử lý đơn nhiều thiết bị, thanh toán online hoặc giao vận. Chia sẻ danh mục liên ứng dụng (F4) còn là mở rộng; chưa áp mã khuyến mãi vào checkout. Dự án giữ trạng thái Đang kiểm thử và hoàn thiện, chưa coi là nghiệm thu cuối cùng.")
    d.add_heading("Hồ sơ đối chiếu", 2)
    for text in [
        "Báo cáo lần 1: docs/bao-cao/tien-do-lan-1/Bao-cao-tien-do-lan-1-Nhom-4.docx.",
        "Pixel_6: docs/kiem-thu/pixel6-care-2026-11-07/README.md, features.json, regression.json, instrumentation.txt và regression.txt.",
        "Các API/Doze đợt trước: docs/kiem-thu/content-broadcast-emulator-2026-11-07/README.md; alarm thật: docs/kiem-thu/content-broadcast-2026-11-07/README.md.",
        "Báo cáo kỹ thuật: docs/bao-cao/bao-cao-de-tai.md và .html; kế hoạch: docs/ke-hoach-content-provider-broadcast.md.",
        "Firebase: docs/firestore-realtime.md, docs/thong-bao-day.md, firestore.rules và docs/kiem-thu/firestore-live-2026-10-21/README.md; mã PromotionsRepository/PromotionsViewModel và BloomyMessagingService/PushNotifications.",
    ]:
        paragraph(d, text)
    d.core_properties.title = "Báo cáo tiến độ lần 2 – Bloomy Beauty – Nhóm 4"
    d.core_properties.author = "Nhóm 4"
    d.core_properties.subject = "Lập trình di động – 07/11/2026"
    d.core_properties.revision = 2
    d.core_properties.created = datetime.now(timezone.utc)
    d.core_properties.modified = datetime.now(timezone.utc)
    target = OUT / "Bao-cao-tien-do-lan-2-Nhom-4.docx"
    d.save(target)
    (OUT / "danh-muc-anh.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(target)
    print(f"{len(manifest)} embedded screenshots; {target.stat().st_size:,} bytes")


if __name__ == "__main__":
    make()
