"""Build the comparison assignment from recorded evidence and current source excerpts.
Run with .local/report-venv/bin/python scripts/create-comparison-assignment.py.
"""
from pathlib import Path
from collections import Counter
import hashlib
import json
import runpy
import shutil
import xml.etree.ElementTree as ET
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/bai-tap/so-sanh-san-pham'
EVIDENCE = ROOT / 'docs/kiem-thu/comparison-2026-11-16'
NAME = '25TX610001_PhamVanBinh.docx'
SCREENS = [('01-selection.png',
  'Chọn sản phẩm từ danh mục',
  'Chọn hai hoặc ba sản phẩm. Thanh lựa chọn hiển thị số lượng; chưa đủ hai mục thì nút mở bảng bị khóa. Lựa '
  'chọn không phụ thuộc bộ lọc.'),
 ('02-table-two.png',
  'Bảng so sánh hai sản phẩm',
  'Đối chiếu giá, dung tích, tình trạng, mô tả, thành phần và cách dùng theo hàng. Ảnh là giao diện mới nhất '
  'với chữ 12–13sp và cụm nút có padding đồng đều.'),
 ('03-three-products.png',
  'So sánh ba sản phẩm và cuộn ngang',
  'Khi đủ ba mục, các sản phẩm chưa chọn không thể được thêm vào danh sách. Vuốt ngang để xem cột thứ ba; '
  'vẫn có thể bỏ từng mục.'),
 ('04-empty.png',
  'Xóa toàn bộ lựa chọn',
  'Sau Xóa tất cả, bảng hiển thị trạng thái rỗng và nút Chọn sản phẩm để quay lại danh mục.'),
 ('05-current-price-sold-out.png',
  'Giá cập nhật và sản phẩm hết hàng',
  'Giá được lấy từ catalog hiện tại. Hết hàng vẫn so sánh được, nhưng nút Thêm vào giỏ bị khóa.')]
EXCERPTS = [('01-quy-tac-chon.png',
  'Quy tắc chọn tối đa ba sản phẩm',
  'feature/home/ComparisonScreen.kt',
  'object ComparisonSelection {',
  '\n@OptIn',
  0,
  'Loại ID trùng, không tồn tại hoặc ngừng bán; giữ thứ tự chọn và tối đa ba mục. Cho phép bỏ một mục để '
  'chọn sản phẩm khác; thông tin thiếu có nhãn rõ ràng.'),
 ('02-trang-thai-dieu-huong.png',
  'Lưu lựa chọn theo tài khoản và điều hướng',
  'feature/home/HomeScreen.kt',
  '    var comparisonIds by rememberSaveable',
  '    val selectedProduct =',
  0,
  'Lưu ID bằng rememberSaveable(user.id) và listSaver, đối chiếu lại với catalog đang có. Mở bảng bằng dữ '
  'liệu hiện tại; callback thêm giỏ dùng lại luồng HomeScreen/CartViewModel. Xem chi tiết rồi quay lại đúng '
  'bảng.'),
 ('03-bo-cuc-thich-ung.png',
  'Bố cục bảng theo chiều rộng màn hình',
  'feature/home/ComparisonScreen.kt',
  '                    BoxWithConstraints(',
  '                                products.forEach',
  0,
  'Tính chiều rộng cột từ vùng hiển thị, dành 76dp cho nhãn và tối thiểu 132dp mỗi sản phẩm. Bảng dùng cuộn '
  'ngang khi cần; toàn màn hình có cuộn dọc.'),
 ('04-padding-nut.png',
  'Padding và hình dạng nút đồng đều',
  'feature/home/ComparisonScreen.kt',
  '                                            val buttonPadding',
  '                                            Button(',
  0,
  'Các nút chiếm cùng chiều rộng cột, cao tối thiểu 40dp, padding ngang 10dp/dọc 8dp và bo góc 10dp. Khoảng '
  'cách cụm nút 8dp; chiều cao được phép tăng khi chữ cần xuống hàng.'),
 ('05-thuoc-tinh.png',
  'Dữ liệu thuộc tính dùng để đối chiếu',
  'feature/home/ComparisonScreen.kt',
  '                            ComparisonRow("Thương hiệu"',
  '                    Text("Thông tin loại da',
  0,
  'Hiển thị trường có sẵn trong Product và danh mục. Giá là giá từng sản phẩm, dung tích giữ đơn vị gốc. '
  'Loại da phù hợp chưa có dữ liệu riêng nên hiện Chưa có thông tin, không tự suy đoán.')]

def make():
    for stem, expected in [('comparison-acceptance', 7), ('regression-acceptance', 12), ('recreation-acceptance', 2), ('button-padding', 7)]:
        result = json.loads((EVIDENCE / (stem + '.environment.json')).read_text())
        assert result['passed'] and result['avd'] == 'Pixel_6' and result['api'] == '35'
        assert result['tests'] == result['passedCases'] == expected and not result['assumedOrIgnoredCases']
        assert not result['restoreErrors']
    suites = [ET.parse(p).getroot() for p in (EVIDENCE / 'unit-results').glob('TEST-*.xml')]
    counts = {k: sum(int(s.get(k, 0)) for s in suites) for k in ('tests', 'failures', 'errors', 'skipped')}
    assert counts == dict(tests=76, failures=0, errors=0, skipped=0), counts
    lint = Counter(i.get('severity') for i in ET.parse(EVIDENCE / 'lint-results-debug.xml').getroot().findall('issue'))
    assert not lint['Error'] and not lint['Fatal']
    OUT.mkdir(parents=True, exist_ok=True)
    renderer = runpy.run_path(str(ROOT / 'scripts/render-favorites-code.py'))
    renderer['make'].__globals__.update(OUT=OUT / 'code', EXCERPTS=EXCERPTS)
    renderer['make']()
    code = json.loads((OUT / 'code/manifest.json').read_text())
    sections = [('1. Yêu cầu bài tập',
      ['Bài tập cá nhân môn Lập trình di động: xây dựng tính năng So sánh sản phẩm cho ứng dụng Bloomy Beauty. '
       'Người dùng chọn từ hai đến ba sản phẩm từ danh mục hoặc chi tiết, xem các thuộc tính theo hàng, bỏ từng '
       'mục, xóa toàn bộ, xem chi tiết và thêm sản phẩm phù hợp vào giỏ.',
       'Ứng dụng đã có catalog, Yêu thích, chi tiết sản phẩm và giỏ hàng dùng SQLite trên thiết bị. Tính năng bổ '
       'sung trạng thái lựa chọn và màn hình so sánh; không tạo bảng mới, không thay schema SQLite 11. Danh sách '
       'so sánh là trạng thái giao diện theo tài khoản, không phải danh sách lưu vĩnh viễn trong cơ sở dữ '
       'liệu.']),
     ('2. Thiết kế và triển khai tính năng',
      ['Luồng sử dụng: thẻ sản phẩm hoặc trang chi tiết → chọn/bỏ so sánh → thanh So sánh (n/3) → '
       'ComparisonScreen. Chỉ mở bảng từ thanh khi có ít nhất hai mục; giới hạn tối đa ba. Khi đã đủ ba, các nút '
       'chọn mục chưa có bị khóa, còn nút bỏ mục đã chọn vẫn hoạt động. Tìm kiếm, danh mục và tab Yêu thích '
       'không làm mất lựa chọn.',
       'ComparisonSelection xử lý ID: loại trùng, loại sản phẩm không còn hoạt động hoặc đã mất, giữ thứ tự '
       'người dùng chọn và giới hạn ba mục. HomeScreen lưu danh sách ID bằng rememberSaveable(user.id) với '
       'listSaver; mỗi lần hiển thị, ID được ghép lại với dữ liệu catalog hiện tại. Không lưu một bản Product cũ '
       'làm nguồn giá/tồn khi so sánh.',
       'ComparisonScreen hiển thị tên/ảnh, thương hiệu, danh mục, giá hiện tại, dung tích, tình trạng tồn, công '
       'dụng/mô tả, thành phần và cách dùng. Bảng cuộn dọc; khi ba cột vượt chiều rộng thì vuốt ngang. Vùng nhãn '
       '76dp, cột sản phẩm tối thiểu 132dp và được phân bổ theo chiều rộng hiện có để hai sản phẩm dễ xem hơn '
       'trên điện thoại portrait.',
       'Giao diện đã thu gọn và chỉnh padding theo phản hồi: chữ bảng 12–13sp, chữ nút 12sp, ảnh 64dp; nút rộng '
       'đều theo cột, cao tối thiểu 40dp, đệm ngang 10dp/dọc 8dp, cách nhau 8dp và bo góc 10dp. Hàng tên dành '
       'tối thiểu hai dòng để cụm thao tác thẳng hàng hơn. Chiều cao nút có thể tự giãn khi nội dung cần xuống '
       'dòng.',
       'Sản phẩm hết hàng vẫn được đối chiếu nhưng khóa Thêm vào giỏ. Khi catalog cập nhật, giá/tồn đổi theo '
       'nguồn và sản phẩm ngừng bán/xóa được loại khỏi lựa chọn. Đang tải hoặc lỗi thì ẩn bảng cũ và có thao tác '
       'thử lại/tải lại. Xóa còn một mục có hướng dẫn chọn thêm; xóa hết có lối quay lại danh mục.',
       'Thao tác thêm giỏ đi qua callback đã có của HomeScreen vào CartViewModel/CartRepository, tiếp tục kiểm '
       'tra phiên, tồn và giá. Màn so sánh không giữ chỗ tồn và không tự tạo đơn. Xem chi tiết từ bảng rồi quay '
       'lại sẽ về bảng; lựa chọn được giữ khi mở giỏ rồi quay lại và khi recreate activity. Chuyển tài khoản làm '
       'sạch trạng thái lựa chọn.',
       'Thông tin thiếu hiển thị Chưa có thông tin. Catalog chưa có trường loại da phù hợp riêng nên không tự '
       'phân loại từ mô tả; dung tích là văn bản nên chưa tính giá/ml hoặc giá/g. Không diễn giải việc giá một '
       'sản phẩm thấp hơn là rẻ hơn trên cùng đơn vị hoặc phù hợp hơn với mọi loại da.']),
     ('3. Minh chứng màn hình theo thứ tự sử dụng',
      ['Năm ảnh được lấy từ hồ sơ kiểm thử ngày 16/11/2026 trên Pixel_6, Android 15/API 35, ARM64, package '
       'com.example.bloomybeauty.demo. Các ảnh đã cập nhật theo phiên bản padding nút mới nhất, tương ứng lượt '
       'button-padding.log đạt 7/7 ca. Không chụp hoặc chạy lại ứng dụng khi tạo báo cáo này.',
       'Ảnh là giao diện Compose thật do UI Automation chụp trong ComparisonFlowTest. Dữ liệu '
       'HomeUiState/CatalogSeed được dựng làm fixture trong bộ nhớ, có điều khiển giá/tồn để kiểm tra phản ứng '
       'UI. Một ca ComparisonIntegrationTest riêng sử dụng AuthRepository, CatalogRepository, CartRepository và '
       'SQLite thật để xác minh thêm giỏ và điều hướng. Không gọi các ảnh fixture là ảnh của một phiên mua hàng '
       'thật liên tục.']),
     ('4. Kiểm thử và kết quả bài tập',
      ['Unit test: 76/76 ca đạt, 0 lỗi, 0 bỏ qua; trong đó ComparisonSelectionTest mới có 5 ca. Kiểm tra '
       'thêm/bỏ, giới hạn ba, loại trùng/ID không hợp lệ/ngừng bán, thứ tự, hết hàng và nhãn thông tin thiếu. '
       'Lint debug: 0 error/fatal, 71 warning, 1 information; build demo và APK kiểm thử thành công.',
       'Android: 21 ca khác nhau đạt trên Pixel_6 API 35, chia ba nhóm: ComparisonFlowTest 6 + '
       'ComparisonIntegrationTest 1; FavoritesFlowTest 3 + HomeFlowTest 2 + CartFlowTest 7; ShopRecreationTest '
       '2. Mỗi nhóm kết thúc thành công, không bỏ qua và không có lỗi khôi phục animation. Đây là bộ liên quan, '
       'không phải chạy lại toàn bộ ứng dụng hoặc một lần chạy gộp 21/21.',
       'Bộ mới kiểm tra lựa chọn từ thẻ/chi tiết, cần hai mục trước khi mở, ba cột/cuộn ngang, bỏ/xóa, xem chi '
       'tiết rồi quay lại, lọc không mất lựa chọn, recreate activity, đổi tài khoản, giá/tồn thay đổi, ẩn bảng '
       'khi loading/lỗi và retry. Integration đi qua BloomyApp: thêm cleanser từ bảng vào giỏ SQLite, kiểm tra '
       'lượng 1 và không có đơn mới, mở giỏ rồi quay lại vẫn có hai lựa chọn.',
       'Các lượt chạy gộp trước gặp timeout ở ca checkout/đổi hướng của bộ cũ và lệnh ADB phản hồi chậm. Có '
       'assertion trông chờ nội dung nằm sẵn trong viewport; helper đã được bổ sung đóng bàn phím/chờ idle và '
       'cuộn tới nội dung. Sau khởi động lại Pixel_6 ở chế độ nền, ba nhóm cuối đều đạt. Log lỗi/dừng trước đó '
       'được giữ trong hồ sơ, không tính vào số ca đạt.',
       'Sau các lần thu gọn và chỉnh padding, 7 ca UI/integration của tính năng được chạy lại trên Pixel_6 có '
       'cửa sổ và đều đạt; lần mới nhất là button-padding.log. Đây là chạy lại các ca đã có, không cộng thêm vào '
       '21 ca khác nhau. Các thiết lập animation được khôi phục thành công.',
       'Giới hạn nghiệm thu: demo ngoại tuyến trên một thiết bị, chưa kiểm thử thiết bị thật hoặc API 28. Lựa '
       'chọn là trạng thái giao diện; không mô tả recreate activity là khởi động lại hệ điều hành. Không kiểm '
       'thử đồng bộ nhiều thiết bị, backend hay thanh toán online trong bài tập này.'])]
    d = Document()
    d.core_properties.title = 'Báo cáo thực hành — So sánh sản phẩm'
    d.core_properties.subject = 'Bloomy Beauty | Lập trình di động'
    d.core_properties.author = 'Phạm Văn Bình'
    teal = '185C63'
    navy = '183344'
    gray = '52636C'
    section = d.sections[0]
    section.page_width = Cm(21); section.page_height = Cm(29.7)
    section.top_margin = section.bottom_margin = Cm(2)
    section.left_margin = Cm(2.3); section.right_margin = Cm(2)
    section.different_first_page_header_footer = True
    for name in ['Normal', 'Title', 'Subtitle', 'Heading 1', 'Heading 2', 'Caption']:
        style = d.styles[name]
        style.font.name = 'Arial'
        style.font.color.rgb = RGBColor.from_string(navy)
    normal = d.styles['Normal']
    normal.font.size = Pt(11)
    normal.paragraph_format.line_spacing = 1.15
    normal.paragraph_format.space_after = Pt(7)
    normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    for name,size in [('Heading 1',16),('Heading 2',12)]:
        style = d.styles[name];style.font.size = Pt(size)
        style.font.color.rgb = RGBColor.from_string(teal)
        style.paragraph_format.space_before = Pt(12)
        style.paragraph_format.space_after = Pt(8)
    d.styles['Caption'].font.size = Pt(9)
    d.styles['Caption'].font.color.rgb = RGBColor.from_string(gray)
    header = section.header.paragraphs[0]
    header.text = 'BLOOMY BEAUTY     /     BÁO CÁO CHỨC NĂNG'
    header.style = d.styles['Caption']
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    footer.add_run('25TX610001 • Phạm Văn Bình   |   ')
    field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE');footer._p.append(field)
    for run in footer.runs:run.font.name='Arial';run.font.size=Pt(9)
    md = ['# Báo cáo thực hành: So sánh sản phẩm', '',
          '**Phạm Văn Bình · 25TX610001** · Lập trình di động · Bloomy Beauty · 16/11/2026', '',
          f'[Báo cáo Word]({NAME}) · [Kiểm thử](../../kiem-thu/comparison-2026-11-16/README.md)', '']
    records = []

    def heading(text, level=1):
        d.add_heading(text,level);md.extend(['#'*(level+1)+' '+text,''])

    def paragraph(text):
        d.add_paragraph(text);md.extend([text,''])

    def table(headers, rows):
        t=d.add_table(rows=1, cols=len(headers));t.alignment=WD_TABLE_ALIGNMENT.CENTER
        t.style='Light Shading Accent 1'
        t.autofit=False
        widths=[Cm(4.4), Cm(12.3)]
        for column,width in zip(t.columns,widths): column.width=width
        repeat=OxmlElement('w:tblHeader');t.rows[0]._tr.get_or_add_trPr().append(repeat)
        for cell,text in zip(t.rows[0].cells,headers):
            cell.text=text
            shade=OxmlElement('w:shd');shade.set(qn('w:fill'),teal);cell._tc.get_or_add_tcPr().append(shade)
            for r in cell.paragraphs[0].runs:r.bold=True;r.font.color.rgb=RGBColor(255,255,255)
        for index,row in enumerate(rows):
            cells=t.add_row().cells
            for cell,value in zip(cells,row):
                cell.text=str(value)
                shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'F0F6F6' if index%2==0 else 'FFFFFF')
                cell._tc.get_or_add_tcPr().append(shade)
        for row in t.rows:
            row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
            for index,cell in enumerate(row.cells):
                cell.width=widths[index]
                cell.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
                margins=OxmlElement('w:tcMar')
                for side in ['top','left','bottom','right']:
                    el=OxmlElement('w:'+side);el.set(qn('w:w'),'100');el.set(qn('w:type'),'dxa');margins.append(el)
                cell._tc.get_or_add_tcPr().append(margins)
                for p in cell.paragraphs:
                    p.alignment=WD_ALIGN_PARAGRAPH.LEFT
                    p.paragraph_format.space_after=Pt(4)
                    for r in p.runs:r.font.size=Pt(10)
        md.extend(['| '+' | '.join(headers)+' |','| '+' | '.join(['---']*len(headers))+' |'])
        md.extend('| '+' | '.join(map(str,row))+' |' for row in rows);md.append('')
        return t

    # Separate cover: identity and project summary replace the previous inline heading.
    p=d.add_paragraph('LẬP TRÌNH DI ĐỘNG');p.paragraph_format.space_before=Pt(36)
    p.runs[0].font.size=Pt(12);p.runs[0].font.color.rgb=RGBColor.from_string(teal)
    p=d.add_paragraph('BÁO CÁO\nTHỰC HÀNH');p.alignment=WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_after=Pt(20)
    for r in p.runs:r.bold=True;r.font.size=Pt(30)
    p=d.add_paragraph('SO SÁNH SẢN PHẨM')
    p.runs[0].font.size=Pt(21);p.runs[0].font.color.rgb=RGBColor.from_string(teal)
    d.add_paragraph('Phân tích yêu cầu • Thiết kế giao diện • Kiểm chứng kết quả')
    d.add_paragraph('')
    table(['THÔNG TIN','NỘI DUNG'],[
        ('Sinh viên','Phạm Văn Bình'),('Mã số sinh viên','25TX610001'),
        ('Ứng dụng','Bloomy Beauty — Nhóm 4'),('Ngày thực hiện','16/11/2026'),
        ('Phạm vi','Ứng dụng Android, dữ liệu cục bộ trên một thiết bị')])
    d.add_paragraph('')
    p=d.add_paragraph('Tài liệu trình bày chức năng chọn và đối chiếu 2–3 sản phẩm, kèm mã nguồn và minh chứng kiểm thử. Các ảnh phản ánh phiên bản giao diện đã chỉnh padding.')
    p.style=d.styles['Caption']
    d.add_page_break()
    heading('NỘI DUNG BÁO CÁO')
    table(['PHẦN','NỘI DUNG'],[
        ('I','Mục tiêu và phạm vi'),('II','Giải pháp thiết kế'),('III','Kết quả kiểm chứng'),
        ('IV','Hồ sơ giao diện'),('V','Phụ lục mã nguồn'),('VI','Tài liệu đối chiếu')])
    d.add_page_break()
    heading('I. MỤC TIÊU VÀ PHẠM VI')
    for text in sections[0][1]:paragraph(text)
    table(['YÊU CẦU','QUY TẮC THỰC HIỆN'],[
        ('Lựa chọn','Chọn 2–3 mục, không trùng; bỏ một mục để thay thế khi đã đủ ba.'),
        ('Dữ liệu','Giá/tồn và nội dung từ catalog hiện tại; không tự suy đoán loại da.'),
        ('Trạng thái','Giữ lựa chọn khi điều hướng/recreate; tách theo tài khoản.'),
        ('Giỏ hàng','Thêm qua luồng kiểm tra có sẵn; không tự tạo đơn hoặc giữ chỗ tồn kho.')])
    d.add_page_break()
    heading('II. GIẢI PHÁP THIẾT KẾ')
    table(['THÀNH PHẦN','VAI TRÒ'],[
        ('ComparisonSelection','Giới hạn lựa chọn, loại trùng/ID mất hoặc ngừng bán.'),
        ('HomeScreen','Lưu ID theo tài khoản, chọn từ thẻ/chi tiết, nối điều hướng.'),
        ('ComparisonScreen','Bảng thuộc tính, bỏ/xóa, xem chi tiết và callback thêm giỏ.'),
        ('Catalog / Cart','Cung cấp dữ liệu hiện tại và kiểm tra thao tác giỏ bằng repository hiện có.')])
    for i,text in enumerate(sections[1][1],1):
        d.add_heading(['Luồng tương tác','Quản lý trạng thái','Bố cục portrait','Quy cách nút và chữ','Trạng thái dữ liệu','Tích hợp giỏ hàng','Thông tin chưa được cung cấp'][i-1],2)
        paragraph(text)
    d.add_page_break()
    heading('III. KẾT QUẢ KIỂM CHỨNG')
    table(['HẠNG MỤC','KẾT QUẢ ĐÃ GHI NHẬN'],[
        ('Unit test','76/76 đạt; gồm 5 ca ComparisonSelectionTest mới.'),
        ('Android mới','6 ca UI + 1 ca integration đạt.'),
        ('Android hồi quy','12 ca Home/Yêu thích/giỏ + 2 ca recreate đạt; tổng 21 ca khác nhau.'),
        ('Padding mới nhất','Chạy lại 7/7 UI/integration đạt; không cộng thêm vào 21 ca.'),
        ('Lint / build','0 lỗi lint, 71 cảnh báo, 1 thông tin; build demo và APK test đạt.'),
        ('Thiết bị','Pixel_6, Android 15/API 35, ARM64.')])
    for text in sections[3][1]:paragraph(text)
    d.add_page_break()
    heading('IV. HỒ SƠ GIAO DIỆN')
    for text in sections[2][1]:paragraph(text)
    for index,(name,caption,description) in enumerate(SCREENS,1):
        d.add_page_break()
        heading(f'G{index:02d} — {caption}')
        source=EVIDENCE/'pixel6-comparison'/name;assert source.is_file()
        shutil.copy2(source,OUT/name)
        p=d.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER
        p.add_run().add_picture(str(source),width=Cm(8.2))
        p=d.add_paragraph(f'Hình {index}. {caption}',style='Caption');p.alignment=WD_ALIGN_PARAGRAPH.CENTER
        paragraph(description)
        md.extend([f'![{caption}]({name})',''])
        with Image.open(source) as im:size=list(im.size)
        records.append(dict(file=name,caption=caption,description=description,source=str(source.relative_to(ROOT)),dimensions=size,sha256=hashlib.sha256(source.read_bytes()).hexdigest(),kind='runtime-screenshot'))
    d.add_page_break()
    heading('V. PHỤ LỤC MÃ NGUỒN')
    paragraph('Các hình sau được kết xuất trực tiếp từ Kotlin hiện tại, có đường dẫn và số dòng để đối chiếu; không phải ảnh chụp IDE. Việc xuống hàng chỉ phục vụ hiển thị, không sửa nội dung mã nguồn.')
    for index,r in enumerate(code,1):
        if index > 1: d.add_page_break()
        heading(f'A{index:02d} — {r["caption"]}')
        p=d.add_paragraph(f'{r["source"]}\nDòng {r["start_line"]}–{r["end_line"]}',style='Caption')
        w,h=r['dimensions'];width=min(16,17*w/h)
        p=d.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER
        p.add_run().add_picture(str(OUT/r['file']),width=Cm(width))
        paragraph(r['description'])
        md.extend([f'![{r["caption"]}]({r["file"]})','',f'Nguồn: [{r["source"]}](../../../{r["source"]}), dòng {r["start_line"]}–{r["end_line"]}.',''])
        records.append(dict(r,kind='source-code'))
    d.add_page_break()
    heading('VI. TÀI LIỆU ĐỐI CHIẾU')
    refs=[('Hướng dẫn tính năng','../../20261116000000_so-sanh-san-pham.md'),
          ('Kế hoạch triển khai','../../20261116000000_ke-hoach-so-sanh-san-pham.md'),
          ('Hồ sơ kiểm thử','../../kiem-thu/comparison-2026-11-16/README.md')]
    for label,path in refs:
        paragraph(label+': '+str((OUT/path).resolve().relative_to(ROOT)))
        md.extend([f'[{label}]({path})',''])
    d.save(OUT/NAME)
    (OUT/'README.md').write_text('\n'.join(md).rstrip()+'\n')
    (OUT/'danh-muc-anh.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
    print('Created redesigned report:', OUT/NAME, '—', len(d.inline_shapes), 'images')

if __name__ == '__main__':make()
