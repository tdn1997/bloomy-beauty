"""Create the individual Favorite Products assignment with real Pixel_6 evidence."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from PIL import Image
import runpy

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'docs/bai-tap/san-pham-yeu-thich'
EVIDENCE = ROOT/'docs/kiem-thu/pixel6-favorites-2026-11-09'
SCREENS = [
 ('01-tai-khoan.png', 'Lối vào từ Tài khoản', 'Nút Sản phẩm yêu thích mở danh sách riêng của tài khoản đang đăng nhập.'),
 ('09-luu-tu-the-san-pham.png', 'Lưu từ thẻ sản phẩm', 'Nhấn tim tại danh mục; tim đổi trạng thái và badge được cập nhật.'),
 ('10-luu-tu-chi-tiet.png', 'Lưu từ chi tiết', 'Sau bỏ lưu và lưu lại ở trang chi tiết, tim phản ánh trạng thái SQLite.'),
 ('11-dong-bo-tab-yeu-thich.png', 'Đồng bộ danh sách', 'Quay về tab Yêu thích hiển thị đúng một sản phẩm vừa lưu.'),
 ('02-yeu-thich-them-gio.png', 'Thêm nhanh vào giỏ', 'Bộ lọc trang chủ không che danh sách; thêm giỏ thành công, vẫn giữ mục yêu thích.'),
 ('03-het-hang.png', 'Sản phẩm hết hàng', 'Tinh chất vẫn xuất hiện, hiển thị Hết hàng và vô hiệu hóa nút thêm giỏ.'),
 ('04-chi-tiet-da-luu.png', 'Chi tiết đã lưu', 'Mở sản phẩm từ Yêu thích; tim có mô tả bỏ khỏi yêu thích.'),
 ('05-chi-tiet-bo-luu.png', 'Bỏ lưu tại chi tiết', 'Tim chuyển rỗng sau thao tác, danh sách cập nhật khi quay lại.'),
 ('06-danh-sach-rong.png', 'Đã bỏ lưu toàn bộ', 'Thông báo rỗng hướng dẫn nhấn tim và có nút Khám phá sản phẩm.'),
 ('07-bo-loc-khong-khop.png', 'Tìm kiếm không khớp', 'Danh sách vẫn có dữ liệu nhưng bộ lọc không khớp; có nút xóa bộ lọc.'),
 ('08-khoi-phuc-danh-sach.png', 'Xóa bộ lọc', 'Hai sản phẩm đã lưu xuất hiện lại, không mất dữ liệu yêu thích.'),
]

def make():
    runpy.run_path(str(ROOT/'scripts/render-favorites-code.py'), run_name='__main__')
    result = json.loads((EVIDENCE/'acceptance.environment.json').read_text())
    assert result['passed'] and result['avd'] == 'Pixel_6' and result['passedCases'] == result['tests']
    suites = [ET.parse(p).getroot() for p in (EVIDENCE/'jvm').glob('TEST-*.xml')]
    counts = {k: sum(int(s.get(k, 0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    assert counts['tests'] and not counts['failures'] and not counts['errors']
    issues = ET.parse(EVIDENCE/'lint-results-debug.xml').getroot().findall('issue')
    lint = {s:sum(i.get('severity') == s for i in issues) for s in ('Error','Fatal','Warning','Information')}
    assert lint['Error'] == lint['Fatal'] == 0
    OUT.mkdir(parents=True, exist_ok=True)
    sections = [
      ('1. Yêu cầu bài tập', [
        'Bài tập cá nhân môn Lập trình di động: xây dựng và hoàn thiện tính năng Sản phẩm yêu thích trong ứng dụng Bloomy Beauty. Người dùng có thể lưu/bỏ lưu sản phẩm, xem danh sách riêng theo tài khoản và thêm sản phẩm vào giỏ. Dữ liệu dùng SQLite trên thiết bị, không cần backend.',
        'Nền đã có: bảng favorites với khóa ghép tài khoản/sản phẩm, tab Yêu thích và nút tim trên thẻ/chi tiết. Phần hoàn thiện trong bài tập: lối vào từ Tài khoản, bộ lọc riêng, trạng thái rỗng phù hợp, tồn kho trên thẻ, thêm nhanh giỏ và lọc badge theo sản phẩm còn hoạt động.']),
      ('2. Thiết kế và triển khai tính năng', [
        'Bảng favorites gồm user_id và product_id, khóa chính ghép chống trùng và khóa ngoại xóa theo tài khoản/sản phẩm. CatalogRepository xử lý dữ liệu; HomeViewModel cung cấp trạng thái cho HomeScreen. Tận dụng schema 11; không migration, không xóa dữ liệu demo. Lưu/bỏ lưu trong transaction và kiểm tra phiên đăng nhập. Không thêm mục không tồn tại/ngừng bán; vẫn cho bỏ lưu mục cũ. Đếm các mục đang bán, giữ sản phẩm hết hàng trong danh sách.',
        'Luồng lưu/bỏ lưu: HomeScreen nhận thao tác tim → HomeViewModel.toggleFavorite → CatalogRepository kiểm tra SessionAccess trong transaction → xóa dòng nếu đã lưu hoặc thêm dòng nếu sản phẩm còn hoạt động → tải lại snapshot và cập nhật UI. Luồng thêm giỏ gọi CartViewModel.add và giữ nguyên dữ liệu yêu thích.',
        'Tìm kiếm/danh mục/sắp xếp của Yêu thích được lưu riêng theo tài khoản bằng rememberSaveable. Tìm kiếm ở trang chủ không che danh sách đã lưu. Danh sách rỗng có nút Khám phá; kết quả lọc rỗng có nút xóa lọc. Thêm giỏ qua CartViewModel/CartRepository hiện có, không bỏ lưu sản phẩm.']),
      ('3. Minh chứng màn hình theo thứ tự sử dụng', [
        '11 ảnh mới được chụp bằng UI Automation khi chạy giao diện Compose trên AVD Pixel_6, API 36, ngày 09/11/2026. Dữ liệu tài khoản và tồn kho là fixture kiểm thử trong favorites_flow_test.db; thao tác dùng repository và SQLite thật. Ảnh sắp xếp theo luồng báo cáo, gồm nhiều ca kiểm thử độc lập; không phải một phiên mua hàng duy nhất.',
        'Kiểm thử dùng database riêng và dọn sau mỗi ca; cài đè bằng adb install -r, không xóa dữ liệu gói demo. Chỉ mở lại AVD Pixel_6 đã có, không tạo máy ảo mới.']),
      ('4. Kiểm thử và kết quả bài tập', [
        f"Android: {result['tests']}/{result['tests']} ca đạt, {result['assumedOrIgnoredCases']} bỏ qua. Phạm vi: FavoritesFlowTest, CatalogRepositoryTest, SessionIsolationTest, HomeFlowTest, CartRepositoryTest, CartFlowTest. Kiểm tra lưu qua mở lại database, tách tài khoản, phiên cũ, sản phẩm xóa/ngừng bán/hết hàng, giao diện và hồi quy giỏ. Đây là bộ liên quan, không phải chạy lại toàn bộ ứng dụng.",
        f"JVM: {counts['tests']} ca, {counts['failures']} lỗi xác nhận, {counts['errors']} lỗi chạy, {counts['skipped']} bỏ qua. Lint debug: 0 error/fatal, {lint['Warning']} warning, {lint['Information']} information. Các cảnh báo còn tồn tại được giữ trong báo cáo lint đính kèm.",
        'Đã kiểm tra thao tác lưu từ thẻ/chi tiết, đồng bộ danh sách, bộ lọc độc lập, bỏ lưu và thêm giỏ; sản phẩm hết hàng không thể thêm từ thẻ. Kiểm tra tồn tại dữ liệu sử dụng đóng/mở helper SQLite và đăng nhập lại, không gọi là kiểm tra khởi động lại hệ điều hành.',
        'Thiết bị thật để đợt sau theo yêu cầu. Không kiểm thử backend, thanh toán online hoặc đồng bộ nhiều thiết bị; các mục này nằm ngoài phạm vi demo. Bài nộp tập trung vào tính năng Sản phẩm yêu thích; các ca giỏ hàng và bảo mật chỉ là hồi quy liên quan.']),
    ]
    d = Document()
    d.core_properties.title = 'Bài tập cá nhân: Sản phẩm yêu thích'
    d.core_properties.subject = 'Lập trình di động — Bloomy Beauty'
    d.core_properties.author = ''
    for section in d.sections:
        section.top_margin = section.bottom_margin = Cm(2)
        section.left_margin = section.right_margin = Cm(2)
        section.header.paragraphs[0].text = "Lập trình di động • Bài tập cá nhân"
        section.footer.paragraphs[0].text = "Sản phẩm yêu thích • 09/11/2026"
    d.styles['Normal'].font.name='Times New Roman'; d.styles['Normal'].font.size=Pt(12)
    d.add_paragraph('BÀI TẬP CÁ NHÂN', 'Title').alignment=WD_ALIGN_PARAGRAPH.CENTER
    d.add_paragraph('TÍNH NĂNG: SẢN PHẨM YÊU THÍCH', 'Subtitle').alignment=WD_ALIGN_PARAGRAPH.CENTER
    d.add_paragraph('Môn học: Lập trình di động\nỨng dụng: Bloomy Beauty\nNgày thực hiện: 09/11/2026')
    d.add_paragraph('Họ và tên: [Điền họ và tên]\nMã số sinh viên: [Điền MSSV]\nLớp: [Điền lớp]')
    markdown=['# Bài tập cá nhân: Sản phẩm yêu thích','', 'Môn Lập trình di động · Bloomy Beauty · Ngày 09/11/2026','',
        'Họ và tên: **[Điền họ và tên]** · MSSV: **[Điền MSSV]** · Lớp: **[Điền lớp]**','',
        '[Bài nộp Word](25TX810025_NguyenDuyThuan.docx) · [Kế hoạch](../../ke-hoach-yeu-thich.md) · [Kiểm thử](../../kiem-thu/pixel6-favorites-2026-11-09/README.md)','']
    records=[]
    for title, paragraphs in sections:
        d.add_heading(title,1);markdown += ['## '+title,'']
        for text in paragraphs:d.add_paragraph(text);markdown += [text,'']
        if title.startswith('2.'):
            code_records=json.loads((OUT/'code/manifest.json').read_text())
            note='Các ảnh dưới được kết xuất trực tiếp từ mã nguồn Kotlin hiện tại, có số dòng và đường dẫn file; đây là ảnh trích code phục vụ bài tập, không phải ảnh chụp IDE. Các dòng dài chỉ được xuống hàng khi hiển thị, không sửa mã nguồn.'
            d.add_paragraph(note);markdown += [note,'']
            for index,record in enumerate(code_records,1):
                d.add_page_break()
                caption=f"2.{index}. {record['caption']}"
                d.add_heading(caption,2)
                d.add_paragraph(f"Nguồn: {record['source']} — dòng {record['start_line']}–{record['end_line']}")
                d.add_picture(str(OUT/record['file']),width=Cm(16))
                d.add_paragraph(record['description'])
                source_link='../../../'+record['source']
                markdown += ['### '+caption,'',f"![{record['caption']}]({record['file']})",'',
                    f"Nguồn: [{Path(record['source']).name}, dòng {record['start_line']}–{record['end_line']}]({source_link})",'',record['description'],'']
                records.append(dict(record,kind='source-code'))
            d.add_page_break()
        if title.startswith('3.'):

            for start in range(0,len(SCREENS),2):
                d.add_page_break()
                table=d.add_table(rows=1,cols=2)
                for cell,(name,caption,description) in zip(table.rows[0].cells,SCREENS[start:start+2]):
                    source=EVIDENCE/name; assert source.is_file(),source
                    shutil.copy2(source,OUT/name)
                    cell.paragraphs[0].add_run(caption).bold=True
                    cell.add_paragraph().add_run().add_picture(str(source),width=Cm(7))
                    cell.add_paragraph(description)
                    markdown += ['### '+caption,'',f'![{caption}]({name})','',description,'']
                    with Image.open(source) as image: size=list(image.size)
                    records.append({'file':name,'caption':caption,'description':description,'source':str(source.relative_to(ROOT)),'dimensions':size,'sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
            d.add_page_break()
    d.save(OUT/'25TX810025_NguyenDuyThuan.docx')
    (OUT/'README.md').write_text('\n'.join(markdown))
    (OUT/'danh-muc-anh.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
    print('Created individual Favorite Products assignment with 11 UI screenshots and 5 code illustrations.')

if __name__=='__main__':make()
