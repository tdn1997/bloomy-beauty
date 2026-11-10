"""Render exact source excerpts as readable, numbered code illustrations for the assignment."""
from pathlib import Path
import hashlib
import json
import textwrap
from PIL import Image, ImageDraw, ImageFont
from pygments import lex
from pygments.lexers import KotlinLexer
from pygments.styles import get_style_by_name

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'docs/bai-tap/san-pham-yeu-thich/code'
BASE = 'app/src/main/java/com/example/bloomybeauty/'
EXCERPTS = [
    ('01-sqlite-favorites.png', 'Bảng lưu sản phẩm yêu thích', 'data/catalog/CatalogSchema.kt',
     '            """CREATE TABLE favorites (', '        CatalogSeed.categories', -1,
     'Khóa chính ghép chống lưu trùng; hai khóa ngoại gắn mục yêu thích với tài khoản và sản phẩm, hỗ trợ ON DELETE CASCADE.'),
    ('02-luu-bo-luu.png', 'Lưu và bỏ lưu trong transaction', 'data/catalog/CatalogRepository.kt',
     '    suspend fun toggleFavorite(', '    private inline fun <T> transaction', 0,
     'Kiểm tra phiên trước khi ghi. Xóa nếu đã lưu; nếu chưa lưu, chỉ thêm sản phẩm còn hoạt động. Transaction bảo đảm thao tác hoàn tất hoặc được rollback.'),
    ('03-viewmodel.png', 'Cập nhật trạng thái giao diện', 'feature/home/HomeViewModel.kt',
     '    fun toggleFavorite(', '    class Factory', 0,
     'Chặn nhấn lặp khi đang lưu, chạy coroutine, tải lại snapshot và cập nhật StateFlow. Khi phiên không hợp lệ, xóa dữ liệu riêng khỏi trạng thái màn hình.'),
    ('04-bo-loc-rieng.png', 'Bộ lọc riêng của Yêu thích', 'feature/home/HomeScreen.kt',
     '    var favoriteQuery by rememberSaveable', '    val selectedProduct =', 0,
     'Lưu bộ lọc theo tài khoản bằng rememberSaveable. Khi vào tab Yêu thích, dùng query, danh mục và sắp xếp riêng thay vì bộ lọc trang chủ.'),
    ('05-ton-kho-them-gio.png', 'Tồn kho và thêm nhanh vào giỏ', 'feature/home/HomeScreen.kt',
     '                Text(if (product.stock == 0)', '@Composable\nprivate fun FavoriteIcon', 0,
     'Thẻ hiển thị Còn hàng/Hết hàng. Chỉ bật thêm giỏ khi sản phẩm đang bán, còn tồn và giỏ không bận; testTag giúp kiểm thử đúng nút của sản phẩm.'),
]


def make():
    OUT.mkdir(parents=True, exist_ok=True)
    font_path='/System/Library/Fonts/Menlo.ttc'
    code_font=ImageFont.truetype(font_path,22)
    label_font=ImageFont.truetype(font_path,18)
    style=get_style_by_name('friendly')
    char_width=code_font.getlength('M')
    records=[]
    for name,title,relative,start,end,offset,description in EXCERPTS:
        path=ROOT/BASE/relative
        source=path.read_text()
        first=source.index(start)
        start_line=source[:first].count('\n')+offset
        stop=source.index(end,first)
        end_line=source[:stop].count('\n')
        lines=source.splitlines()[start_line:end_line]
        while lines and not lines[-1].strip(): lines.pop()
        # Only wrap visually; source and original line numbers remain unchanged.
        rows=[]
        for number,line in enumerate(lines,start_line+1):
            line=line.expandtabs(4)
            parts=textwrap.wrap(line,width=88,replace_whitespace=False,drop_whitespace=False,break_long_words=False,break_on_hyphens=False) or ['']
            for part,fragment in enumerate(parts):
                rows.append((str(number) if part==0 else '↳',fragment))
        width=1280;height=108+len(rows)*30+20
        image=Image.new('RGB',(width,height),'#ffffff');draw=ImageDraw.Draw(image)
        draw.rectangle((0,0,width,80),fill='#edf2f7')
        draw.text((24,12),title,font=code_font,fill='#21334c')
        draw.text((24,47),relative+f'  |  lines {start_line+1}–{start_line+len(lines)}',font=label_font,fill='#45566b')
        draw.rectangle((0,80,79,height),fill='#f6f8fa')
        for index,(number,line) in enumerate(rows):
            y=98+index*30
            draw.text((12,y),number,font=label_font,fill='#687582')
            x=96
            for token,value in lex(line,KotlinLexer(stripnl=False,ensurenl=False)):
                value=value.rstrip('\n')
                color=style.style_for_token(token)['color'] or '263238'
                draw.text((x,y),value,font=code_font,fill='#'+color)
                x+=char_width*len(value)
        image.save(OUT/name)
        records.append({'file':'code/'+name,'caption':title,'description':description,
            'source':BASE+relative,'start_line':start_line+1,'end_line':start_line+len(lines),
            'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'sha256':hashlib.sha256((OUT/name).read_bytes()).hexdigest(),'dimensions':[width,height]})
    (OUT/'manifest.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
    print(f'Rendered {len(records)} source-code illustrations from current Kotlin files.')

if __name__=='__main__':make()
