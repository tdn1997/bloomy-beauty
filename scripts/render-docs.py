"""Render the report and editable SVG diagrams with Python's standard library."""
from pathlib import Path
import html
import re

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/bao-cao'
OUT = DOCS / 'so-do'
OUT.mkdir(exist_ok=True)

def svg(name, title, nodes, edges, width=1200, height=800):
    pieces = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-labelledby="title"><title id="title">{html.escape(title)}</title>', '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10Z" fill="#98506a"/></marker></defs>', f'<rect width="{width}" height="{height}" fill="#fffafc"/>', f'<text x="35" y="35" font-family="Arial,sans-serif" font-size="23" font-weight="bold" fill="#502239">{html.escape(title)}</text>']
    for a,b in edges:
        ax,ay,aw,ah,*_=nodes[a]; bx,by,bw,bh,*_=nodes[b]
        acx,acy=ax+aw/2,ay+ah/2;bcx,bcy=bx+bw/2,by+bh/2
        dx,dy=bcx-acx,bcy-acy
        ta=min(aw/2/abs(dx) if dx else float('inf'),ah/2/abs(dy) if dy else float('inf'))
        tb=min(bw/2/abs(dx) if dx else float('inf'),bh/2/abs(dy) if dy else float('inf'))
        pieces.append(f'<path d="M{acx+dx*ta:.1f} {acy+dy*ta:.1f} L{bcx-dx*tb:.1f} {bcy-dy*tb:.1f}" stroke="#98506a" stroke-width="2" fill="none" marker-end="url(#arrow)"/>')
    for x,y,w,h,heading,lines in nodes.values():
        pieces.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="15" fill="#fff" stroke="#d5a9bb" stroke-width="2"/>')
        pieces.append(f'<text x="{x+16}" y="{y+31}" font-family="Arial,sans-serif" font-size="21" font-weight="bold" fill="#79334f">{html.escape(heading)}</text>')
        for i,line in enumerate(lines):
            pieces.append(f'<text x="{x+16}" y="{y+60+i*25}" font-family="Arial,sans-serif" font-size="17" fill="#45343b">{html.escape(line)}</text>')
    pieces.append('</svg>')
    (OUT/name).write_text(''.join(pieces))

svg('kien-truc.svg','Kiến trúc Android / SQLite',{
 'ui':(40,100,235,130,'Compose / BloomyApp',['Màn hình, điều hướng','Trạng thái theo phiên']),
 'vm':(330,100,230,130,'ViewModel',['StateFlow / lifecycle','Coroutines']),
 'repo':(615,100,230,130,'Repository',['Phiên, role, nghiệp vụ','Transaction']),
 'db':(900,100,255,130,'SQLiteOpenHelper',['Schema v5 / migration','Ảnh + seed cục bộ'])},[('ui','vm'),('vm','repo'),('repo','db')],height=290)
svg('use-case.svg','Use case — một thiết bị, hai vai trò',{
 'g':(40,70,260,110,'Chưa đăng nhập',['Thông tin xác thực']),
 'c':(40,240,260,110,'Khách hàng',['Phiên đúng chủ dữ liệu']),
 'a':(40,470,260,110,'Quản trị viên',['Role ADMIN hiện hành']),
 'auth':(390,70,740,110,'Tài khoản',['Đăng ký / đăng nhập / khôi phục phiên']),
 'buy':(390,230,740,175,'Mua hàng và hồ sơ',['Tìm / lọc / yêu thích / giỏ / giao hàng / COD','Xem đơn của mình / hủy PENDING','Hồ sơ / đăng xuất']),
 'admin':(390,470,740,135,'Quản trị',['Thêm / sửa / ẩn / mở sản phẩm','Xử lý trạng thái / xem lịch sử đơn'])},[('g','auth'),('c','buy'),('a','admin')],height=650)
svg('du-lieu.svg','SQLite v5 — các quan hệ chính (chi tiết FK trong Mermaid)',{
 'users':(430,65,315,140,'users',['PK _id / UNIQUE email','role / hash / salt / iterations']),
 'session':(40,65,315,140,'auth_session',['PK _id = 1','FK user_id']),
 'shipping':(825,65,330,140,'shipping_details',['PK/FK user_id','recipient / phone / address']),
 'request':(40,270,315,150,'checkout_requests',['PK request_id / UNIQUE user_id','fingerprint / payload']),
 'cart':(430,270,315,150,'cart_items',['PK user_id + product_id','quantity / accepted_price_vnd']),
 'favorites':(825,270,330,150,'favorites',['PK user_id + product_id','FK users / products']),
 'orders':(40,490,315,150,'orders',['PK _id / FK user_id','UNIQUE request_id / snapshot']),
 'products':(430,490,315,150,'products',['PK id / FK category_id','price / stock / active / revision']),
 'categories':(825,490,330,150,'categories',['PK id','name / sort_order']),
 'lines':(40,710,315,140,'order_items',['PK order_id + product_id','Tên / giá / số lượng snapshot']),
 'events':(430,710,315,140,'order_events',['FK order_id / actor_id','Trạng thái / người / thời gian'])},[('users','session'),('users','shipping'),('users','request'),('users','cart'),('users','favorites'),('request','orders'),('products','cart'),('products','favorites'),('categories','products'),('orders','lines'),('orders','events')],height=890)
# Request -> orders is a logical link, not a declared foreign key.
svg('dat-cod.svg','Đặt COD — thao tác trong transaction',{
 'prepare':(40,75,330,135,'1. Kiểm tra trước đặt',['Lưu giao hàng / UUID','Fingerprint + payload SQLite']),
 'begin':(430,75,330,135,'2. Bắt đầu transaction',['Đúng phiên / request / giỏ','Giá / tồn / địa chỉ khớp']),
 'replay':(830,75,330,135,'3a. Đã có kết quả',['Cùng mã + cùng nội dung','Trả đơn cũ / giữ giỏ mới']),
 'write':(430,285,330,160,'3b. Chưa có, hợp lệ',['Trừ tồn / tăng revision','Ghi đơn + item snapshot + audit','Dọn giỏ lần đặt']),
 'commit':(830,285,330,160,'4. Commit và hiển thị',['Một đơn / một lần trừ tồn','Cold start đọc lại kết quả']),
 'rollback':(40,285,330,160,'Lỗi / xung đột',['Rollback mọi thay đổi','Báo lỗi để sửa và xác nhận lại'])},[('prepare','begin'),('begin','replay'),('begin','write'),('begin','rollback'),('write','commit')],height=495)
svg('trang-thai.svg','Trạng thái đơn — khách chỉ hủy PENDING',{
 'p':(35,90,250,120,'PENDING',['Chờ xác nhận','Tồn đã trừ khi tạo']),
 'c':(325,90,250,120,'CONFIRMED',['Admin xác nhận','Không đổi tồn']),
 's':(615,90,250,120,'SHIPPING',['Admin bắt đầu giao','Không đổi tồn']),
 'd':(905,90,250,120,'DELIVERED',['Đã giao / kết thúc','Không cho hủy']),
 'x':(325,330,420,135,'CANCELLED',['PENDING: chủ đơn hoặc admin','CONFIRMED: admin; hoàn tồn một lần'])},[('p','c'),('c','s'),('s','d'),('p','x'),('c','x')],height=510)

def inline(value):
    value=html.escape(value)
    value=re.sub(r'!\[([^\]]*)\]\(([^)]+)\)',r'<img alt="\1" src="\2">',value)
    value=re.sub(r'\[([^\]]+)\]\(([^)]+)\)',r'<a href="\2">\1</a>',value)
    value=re.sub(r'`([^`]+)`',r'<code>\1</code>',value)
    value=re.sub(r'\*\*([^*]+)\*\*',r'<strong>\1</strong>',value)
    return value

def render(markdown):
    lines=markdown.splitlines();out=[];paragraph=[];i=0
    def flush():
        if paragraph:out.append('<p>'+inline(' '.join(paragraph))+'</p>');paragraph.clear()
    while i<len(lines):
        line=lines[i]
        if not line.strip():flush();i+=1;continue
        if line.startswith('#'):
            flush();n=len(line)-len(line.lstrip('#'));out.append(f'<h{n}>{inline(line[n:].strip())}</h{n}>');i+=1;continue
        if line.startswith('|'):
            flush();rows=[]
            while i<len(lines) and lines[i].startswith('|'):
                cells=[c.strip() for c in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?',c) for c in cells):rows.append(cells)
                i+=1
            out.append('<table><thead><tr>'+''.join('<th>'+inline(c)+'</th>' for c in rows[0])+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+inline(c)+'</td>' for c in row)+'</tr>' for row in rows[1:])+'</tbody></table>');continue
        if line.startswith('- '):
            flush();out.append('<ul>')
            while i<len(lines) and lines[i].startswith('- '):out.append('<li>'+inline(lines[i][2:])+'</li>');i+=1
            out.append('</ul>');continue
        paragraph.append(line);i+=1
    flush();return '\n'.join(out)

source=DOCS/'bao-cao-de-tai.md'
body=render(source.read_text())
body=body.replace('<h2>4. Thiết kế dữ liệu SQLite</h2>','<h2>4. Thiết kế dữ liệu SQLite</h2><img class="diagram" alt="Quan hệ SQLite" src="so-do/du-lieu.svg">')
body=body.replace('<h2>3. Công nghệ và kiến trúc</h2>','<h2>3. Công nghệ và kiến trúc</h2><img class="diagram" alt="Kiến trúc" src="so-do/kien-truc.svg">')
body=body.replace('<h3>5.3. Giao hàng và COD</h3>','<h3>5.3. Giao hàng và COD</h3><img class="diagram" alt="Transaction COD" src="so-do/dat-cod.svg">')
style='''@page{size:A4;margin:18mm 16mm}*{box-sizing:border-box}body{font:11pt/1.55 Arial,sans-serif;color:#30232a;max-width:190mm;margin:24px auto}h1{font-size:24pt;color:#79334f;line-height:1.2}h2{font-size:17pt;color:#79334f;margin-top:24pt}h3{font-size:13pt;color:#79334f}h1,h2,h3{break-after:avoid}p{orphans:3;widows:3}table{width:100%;border-collapse:collapse;font-size:9pt;margin:12pt 0}th,td{border:1px solid #ddc6d0;padding:7px;text-align:left;vertical-align:top}th{background:#f8e9ef}tr{break-inside:avoid}code{font-size:9pt;overflow-wrap:anywhere;background:#f8f1f4}img{display:block;margin:12pt auto;max-width:100%;max-height:105mm;object-fit:contain;break-inside:avoid}.diagram{width:100%;max-height:135mm}a{color:#79334f;overflow-wrap:anywhere}@media screen{body{padding:30px;box-shadow:0 4px 40px #e5d0da}}@media print{body{margin:0}.screen-only{display:none}}'''
(DOCS/'bao-cao-de-tai.html').write_text('<!doctype html><html lang="vi"><meta charset="utf-8"><title>Báo cáo — Bloomy Beauty</title><style>'+style+'</style><body><p class="screen-only"><button onclick="window.print()">In / lưu PDF</button> · Bản kỹ thuật, thông tin học phần còn chờ điền.</p>'+body+'</body></html>')
print('Rendered report HTML and five offline SVG diagrams.')
