"""Build the reorder assignment from recorded evidence and current source excerpts.
Run with .local/report-venv/bin/python scripts/create-reorder-assignment.py.
"""
from pathlib import Path
from collections import Counter
import hashlib
import json
import runpy
import shutil
import xml.etree.ElementTree as ET
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/bai-tap/mua-lai-don-cu'
EVIDENCE = ROOT / 'docs/kiem-thu/reorder-2026-11-16'
NAME = '25TX810025_NguyenDuyThuan.docx'
SCREENS = [
 ('01-order-action.png', 'Lối vào Mua lại từ chi tiết đơn', 'Đơn đã hủy có nút Mua lại. Nút cũng được hỗ trợ cho đơn đã giao của chính tài khoản.'),
 ('02-preview.png', 'Xem trước trước khi xác nhận', 'Hiển thị số lượng yêu cầu, thêm được, lượng đang có trong giỏ và giá hiện tại. Hủy không làm thay đổi giỏ.'),
 ('03-cart-merged.png', 'Giỏ hàng sau khi gộp', 'Thêm các dòng đủ điều kiện, giữ mặt hàng đã có và báo kết quả. Chưa tạo đơn hay trừ tồn kho.'),
 ('04-new-order.png', 'Đặt đơn COD mới', 'Khách tiếp tục kiểm tra giao hàng và xác nhận COD qua luồng checkout hiện có. Snapshot đơn cũ được giữ nguyên.'),
 ('05-partial-preview.png', 'Thiếu tồn, đổi giá và ngừng bán', 'Bản xem trước giải thích phần số lượng bị giảm, giá cũ so với giá hiện tại và sản phẩm không còn bán.'),
 ('06-partial-result.png', 'Kết quả mua lại một phần', 'Giỏ hiển thị lượng thực tế đã thêm, số mặt hàng bị bỏ qua và số mặt hàng giảm lượng.'),
 ('07-stale-preview.png', 'Dữ liệu thay đổi sau xem trước', 'Repository phát hiện giỏ đã đổi, tải lại bản xem trước và yêu cầu xác nhận lần nữa; không tự áp dụng kết quả mới.'),
 ('08-unavailable.png', 'Không còn mặt hàng mua được', 'Mọi dòng không khả dụng đều có lý do. Nút Thêm vào giỏ bị vô hiệu hóa; dữ liệu giỏ được giữ nguyên.'),
]
EXCERPTS = [
 ('01-quy-tac-gop.png', 'Quy tắc gộp số lượng', 'data/order/ReorderModels.kt', 'internal object ReorderRules {', '\n}', 0,
  'Dùng giá hiện tại, giới hạn theo tồn kho và mức 99 đơn vị. Phân biệt sản phẩm mất/ngừng bán/hết hàng, giỏ vượt tồn và giá chưa được chấp nhận.'),
 ('02-transaction.png', 'Xác nhận trong một transaction', 'data/order/ReorderRepository.kt', '    suspend fun confirm(', '    private fun buildPreview(', 0,
  'Đọc lại và đối chiếu nguồn preview, gộp các dòng rồi xóa checkout nháp trong cùng transaction. Chỉ đánh dấu preview đã sử dụng sau khi commit thành công; lỗi ghi rollback toàn bộ.'),
 ('03-quyen-so-huu.png', 'Kiểm tra phiên và quyền sở hữu', 'data/order/ReorderRepository.kt', '    private fun buildPreview(', '        val cart =', 0,
  'Kiểm tra phiên hiện hành, tìm đơn theo cả orderId và userId, chỉ cho phép trạng thái DELIVERED/CANCELLED. Kết quả checkout đã commit phải được hoàn tất trước khi mua lại.'),
 ('04-viewmodel.png', 'Trạng thái xác nhận và dữ liệu thay đổi', 'feature/order/ReorderAction.kt', '    fun confirm(', '    class Factory(', 0,
  'Khóa nhấn lặp trong lúc xử lý. Khi dữ liệu thay đổi, tải preview mới và giữ thông báo yêu cầu xác nhận lại; lỗi lưu trữ không được tự gửi lại thao tác gộp.'),
 ('05-checkout.png', 'Đưa giỏ về bước sản phẩm', 'feature/cart/CartViewModel.kt', '    fun openAfterReorder(', '    fun reload()', 0,
  'Xóa request, coupon và trạng thái review cũ, giữ nháp giao hàng. Nếu refresh còn chạy, đặt cờ để reset và tải lại sau khi refresh kết thúc.'),
]

def make():
    result = json.loads((EVIDENCE / 'android-tests-final.environment.json').read_text())
    assert result['passed'] and result['avd'] == 'Pixel_6' and result['api'] == '35'
    assert result['tests'] == result['passedCases'] == 57 and not result['assumedOrIgnoredCases']
    suites = [ET.parse(p).getroot() for p in (EVIDENCE / 'unit-results').glob('TEST-*.xml')]
    counts = {k: sum(int(s.get(k, 0)) for s in suites) for k in ('tests', 'failures', 'errors', 'skipped')}
    assert counts == dict(tests=71, failures=0, errors=0, skipped=0), counts
    lint = Counter(i.get('severity') for i in ET.parse(EVIDENCE / 'lint-results-debug.xml').getroot().findall('issue'))
    assert not lint['Error'] and not lint['Fatal']
    OUT.mkdir(parents=True, exist_ok=True)
    renderer = runpy.run_path(str(ROOT / 'scripts/render-favorites-code.py'))
    renderer['make'].__globals__.update(OUT=OUT / 'code', EXCERPTS=EXCERPTS)
    renderer['make']()
    code = json.loads((OUT / 'code/manifest.json').read_text())
    sections = [
      ('1. Yêu cầu bài tập', [
        'Bài tập cá nhân môn Lập trình di động: xây dựng tính năng Mua lại đơn cũ trong ứng dụng Bloomy Beauty. Khách mở đơn đã giao hoặc đã hủy của chính mình, xem trước các mặt hàng có thể mua lại, xác nhận gộp vào giỏ rồi đặt đơn mới qua quy trình COD hiện có. Dữ liệu dùng SQLite trên một thiết bị, có thể chạy ngoại tuyến.',
        'Nền đã có gồm lịch sử/chi tiết đơn, giỏ hàng, kiểm tra giá và tồn, nháp giao hàng và cơ chế khôi phục checkout. Phần bổ sung gồm nút Mua lại, bản xem trước, transaction gộp nhiều dòng, kiểm tra lại dữ liệu trước khi ghi và điều hướng về đúng bước sản phẩm trong giỏ. Không thay SQLite schema 11.']),
      ('2. Thiết kế và triển khai tính năng', [
        'Luồng chính: OrdersScreen → ReorderViewModel → ReorderRepository.preview → hiển thị xem trước → ReorderRepository.confirm → CartViewModel.openAfterReorder → giỏ hàng → checkout COD. Preview chỉ đọc dữ liệu; repository xác nhận kiểm tra lại phiên đăng nhập, chủ sở hữu và trạng thái đơn trong transaction.',
        'Với số lượng c đã có trong giỏ, q của đơn cũ và giới hạn L = min(tồn hiện tại, 99), lượng thêm là min(q, max(0, L − c)) khi dòng hợp lệ. Giữ các sản phẩm khác trong giỏ. Thiếu tồn thì thêm phần còn đủ; sản phẩm bị xóa, ngừng bán, hết hàng hoặc giỏ đã đạt giới hạn được bỏ qua kèm lý do. Nếu lượng c đang vượt tồn thì giữ nguyên và yêu cầu khách điều chỉnh.',
        'Giá mua lại lấy từ catalog hiện tại, hiển thị thay đổi so với snapshot đơn cũ. Dòng đang có trong giỏ chưa chấp nhận giá mới phải được xử lý bằng luồng xác nhận giá hiện có trước khi mua lại. Tiền dùng Long và phép tính có kiểm tra tràn. Tạm tính preview chỉ là phần hàng thêm, chưa gồm phí giao hàng và ưu đãi.',
        'Confirm đọc lại đơn, giỏ, sản phẩm và checkout, đối chiếu với dữ liệu đã xem trước. Nếu thay đổi, không ghi và yêu cầu xem/xác nhận lại. Nếu khớp, gộp toàn bộ dòng đủ điều kiện và xóa checkout nháp trong cùng transaction. Lỗi ghi bất kỳ dòng nào rollback toàn bộ; chống nhấn đúp và chống dùng lại preview đã commit.',
        'Không sao chép giá, coupon, phí hoặc địa chỉ của đơn cũ. Giữ nháp giao hàng hiện tại; xóa trạng thái review/coupon cũ khi mở giỏ sau mua lại. Kết quả checkout đã tạo đơn nhưng chưa xem xong phải được hoàn tất trước. Thêm giỏ không trừ kho, không tạo đơn, không tạo thông báo đơn và không làm đổi lịch sử/PDF/đánh giá của đơn cũ.']),
      ('3. Minh chứng màn hình theo thứ tự sử dụng', [
        '8 ảnh được lấy từ lần chạy ReorderFlowTest trên Pixel_6 mới, Android 15/API 35, ARM64, ngày 16/11/2026. Ảnh thể hiện thao tác Compose với repository và SQLite thật trong database kiểm thử riêng reorder-flow-test.db; fixture được dọn sau mỗi ca. Đây là nhiều ca độc lập sắp theo luồng giải thích, không phải một phiên mua hàng liên tục.',
        'Pixel_6 được tạo lại theo yêu cầu sau nâng cấp macOS. Báo cáo dùng kết quả cuối trên API 35; không dùng lượt API 37 lỗi tương thích Espresso làm kết quả nghiệm thu. Ảnh giữ nguyên nội dung từ hồ sơ kiểm thử đã lưu, không dựng lại bằng mockup và không chụp một lượt mới khi tạo báo cáo.']),
      ('4. Kiểm thử và kết quả bài tập', [
        'Android: 57/57 ca đạt, 0 lỗi, 0 bỏ qua trong lượt cuối trên Pixel_6 API 35. Trong đó 21 ca mới trực tiếp cho Mua lại gồm ReorderRepositoryTest 14 ca và ReorderFlowTest 7 ca; 36 ca còn lại là hồi quy giỏ, đơn, refresh checkout và tích hợp thương mại. Đây là bộ liên quan, không phải toàn bộ kiểm thử ứng dụng.',
        f"JVM: {counts['tests']}/{counts['tests']} ca đạt, 0 lỗi, 0 bỏ qua; gồm 9 ca mới ReorderRulesTest. Một ca kiểm tra 2.400 tổ hợp số lượng/tồn để bảo đảm không vượt giới hạn và không tự giảm lượng hàng đang có. Lint debug: 0 error/fatal, {lint['Warning']} warning, {lint['Information']} information. Build demo và APK kiểm thử thành công.",
        'Các trường hợp đã kiểm tra: gộp giỏ rỗng/trùng sản phẩm, thiếu tồn, giới hạn 99, sản phẩm thiếu/ngừng bán/hết hàng, đổi giá, tài khoản khác/phiên cũ, dữ liệu thay đổi sau preview, xác nhận đồng thời, rollback lỗi ghi và lỗi xóa checkout, giữ dữ liệu sau mở lại DB, giữ snapshot lịch sử và đặt đơn mới. UI kiểm tra Hủy, khôi phục activity không tự thêm, thông báo kết quả và khóa xác nhận khi không còn dòng hợp lệ.',
        'Trong quá trình kiểm thử, hai assertion của báo cáo thông báo được sửa để so khớp chuỗi con; helper checkout cũ được bổ sung đóng bàn phím và chờ idle trước khi bấm nút lưu. Lượt cuối OK (57 tests); các thiết lập animation đã được khôi phục. Các lỗi/lượt trước được giữ trong hồ sơ kiểm thử, không cộng vào kết quả cuối.',
        'Phạm vi nghiệm thu là bản demo SQLite trên một thiết bị. Chưa kiểm thử thiết bị thật, thanh toán online hoặc đồng bộ nhiều thiết bị. Bản xem trước không giữ chỗ tồn kho; quy trình tạo đơn vẫn kiểm tra lại giá/tồn trước khi trừ kho. Không mô tả việc đóng/mở helper SQLite hoặc recreate activity thành kiểm thử khởi động lại hệ điều hành.']),
    ]
    d = Document()
    d.core_properties.title = 'Bài tập cá nhân: Mua lại đơn cũ'
    d.core_properties.subject = 'Lập trình di động — Bloomy Beauty — Nhóm 4'
    d.core_properties.author = 'Nguyễn Duy Thuân'
    for s in d.sections:
        s.top_margin = s.bottom_margin = s.left_margin = s.right_margin = Cm(2)
        s.header.paragraphs[0].text = 'Lập trình di động • Bài tập cá nhân'
        s.footer.paragraphs[0].text = 'Mua lại đơn cũ • 16/11/2026'
        p=s.footer.paragraphs[0];p.add_run(' • Trang ')
        field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE');p._p.append(field)
    d.styles['Normal'].font.name = 'Times New Roman'; d.styles['Normal'].font.size = Pt(12)
    d.styles['Normal'].paragraph_format.space_after = Pt(6)
    d.add_paragraph('BÀI TẬP CÁ NHÂN', 'Title').alignment = WD_ALIGN_PARAGRAPH.CENTER
    d.add_paragraph('TÍNH NĂNG: MUA LẠI ĐƠN CŨ', 'Subtitle').alignment = WD_ALIGN_PARAGRAPH.CENTER
    d.add_paragraph('Môn học: Lập trình di động\nỨng dụng: Bloomy Beauty – Nhóm 4\nNgày thực hiện: 16/11/2026')
    d.add_paragraph('Họ và tên: Nguyễn Duy Thuân\nMã số sinh viên: 25TX810025')
    md=['# Bài tập cá nhân: Mua lại đơn cũ','', 'Môn Lập trình di động · Bloomy Beauty – Nhóm 4 · Ngày 16/11/2026','',
        'Họ và tên: **Nguyễn Duy Thuân** · MSSV: **25TX810025**','',
        f'[Bài nộp Word]({NAME}) · [Kế hoạch](../../20261116000000_ke-hoach-mua-lai-don-cu.md) · [Kiểm thử](../../kiem-thu/reorder-2026-11-16/README.md)','']
    records=[]
    for title, paragraphs in sections:
        d.add_heading(title,1);md += ['## '+title,'']
        for text in paragraphs:d.add_paragraph(text);md += [text,'']
        if title.startswith('2.'):
            note='Các ảnh code sau được kết xuất từ mã Kotlin hiện tại, có đường dẫn và số dòng; đây là ảnh trích mã nguồn, không phải ảnh chụp IDE. Chỉ xuống hàng khi hiển thị, không sửa nội dung code.'
            d.add_paragraph(note);md += [note,'']
            for i,r in enumerate(code,1):
                d.add_page_break();d.add_heading(f"2.{i}. {r['caption']}",2)
                d.add_paragraph(f"Nguồn: {r['source']} — dòng {r['start_line']}–{r['end_line']}")
                w,h=r['dimensions'];width=min(16,18*w/h)
                d.add_picture(str(OUT/r['file']),width=Cm(width));d.add_paragraph(r['description'])
                md += ['### '+r['caption'],'',f"![{r['caption']}]({r['file']})",'',f"Nguồn: [{r['source']}](../../../{r['source']}) — dòng {r['start_line']}–{r['end_line']}",'',r['description'],'']
                records.append(dict(r,kind='source-code'))
            d.add_page_break()
        if title.startswith('3.'):
            for start in range(0,len(SCREENS),2):
                d.add_page_break();table=d.add_table(rows=1,cols=2)
                for cell,(name,caption,description) in zip(table.rows[0].cells,SCREENS[start:start+2]):
                    source=EVIDENCE/'pixel6-reorder'/name;assert source.is_file(),source
                    shutil.copy2(source,OUT/name)
                    cell.paragraphs[0].add_run(caption).bold=True
                    cell.add_paragraph().add_run().add_picture(str(source),width=Cm(7))
                    cell.add_paragraph(description)
                    md += ['### '+caption,'',f'![{caption}]({name})','',description,'']
                    with Image.open(source) as im:size=list(im.size)
                    records.append(dict(file=name,caption=caption,description=description,source=str(source.relative_to(ROOT)),dimensions=size,sha256=hashlib.sha256(source.read_bytes()).hexdigest(),kind='runtime-screenshot'))
            d.add_page_break()
    d.add_paragraph('Tài liệu đối chiếu: docs/20261116000000_mua-lai-don-cu.md; docs/20261116000000_ke-hoach-mua-lai-don-cu.md; docs/kiem-thu/reorder-2026-11-16/README.md.')
    d.save(OUT/NAME)
    (OUT/'README.md').write_text('\n'.join(md).rstrip()+'\n')
    (OUT/'danh-muc-anh.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
    print('Created',OUT/NAME,'with',len(d.inline_shapes),'images')

if __name__ == '__main__':make()
