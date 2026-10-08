"""Package the reviewed demo artifacts and allowlisted source; exclude local secrets."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import shutil

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'deliverables'
DEST.mkdir(exist_ok=True)
required=['bloomy-beauty-demo.apk','bloomy-beauty-release-qa.apk']
for name in required:
    if not (DEST/name).is_file():
        raise SystemExit(f'Missing {name}; build/copy the APK first. See docs/huong-dan-chay.md')

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

source=[]
for name in ['app/src','gradle','scripts','docs']:
    for p in (ROOT/name).rglob('*'):
        if p.is_file() and not p.is_symlink() and '__pycache__' not in p.parts and p.suffix not in {'.mp4','.srt'}:
            source.append(p)
for name in ['README.md','.gitignore','gradlew','gradlew.bat','gradle.properties','settings.gradle.kts','build.gradle.kts','app/build.gradle.kts','app/proguard-rules.pro']:
    p=ROOT/name
    if p.is_file():source.append(p)
source=sorted(set(source),key=lambda p:p.relative_to(ROOT).as_posix())
records=[]
for p in source:
    relative=p.relative_to(ROOT).as_posix()
    if '.local' in p.parts or p.name=='local.properties' or relative.startswith('deliverables/'):
        raise SystemExit(f'Unexpected private/generated path: {relative}')
    records.append({'path':relative,'bytes':p.stat().st_size,'sha256':digest(p)})
manifest={'application':'Bloomy Beauty','version':'1.0','schema':5,'prepared':'2026-10-08','git_commit':None,'note':'No Git repository in this workspace; hashes identify the source snapshot.','files':records}
(DEST/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
with ZipFile(DEST/'bloomy-beauty-source.zip','w',ZIP_DEFLATED) as z:
    for p in source:z.write(p,p.relative_to(ROOT))
    z.write(DEST/'source-manifest.json','source-manifest.json')
# Refresh the document copy so removal/rename does not leave stale evidence.
staging=DEST/'docs'
if staging.exists():shutil.rmtree(staging)
shutil.copytree(ROOT/'docs',staging,ignore=shutil.ignore_patterns('__pycache__'))
if (ROOT/'README.md').exists():
    introduction='BỘ BÀN GIAO: APK ở ngay thư mục này; tài liệu trong docs/. Giải nén bloomy-beauty-source.zip vào thư mục riêng trước khi chạy các lệnh build.\n\n'
    (DEST/'README.md').write_text(introduction+(ROOT/'README.md').read_text().replace('deliverables/','./'))
# Preserve HTML test reports and their supporting assets from the actual runs.
reports=DEST/'ket-qua-kiem-thu'
if reports.exists():shutil.rmtree(reports)
reports.mkdir()
for path,label in [('app/build/reports/tests/testDebugUnitTest','jvm'),('app/build/reports/androidTests/connected/debug','android')]:
    src=ROOT/path
    if src.exists():shutil.copytree(src,reports/label)
for label in ['debug','release']:
    for ext in ['html','xml']:
        src=ROOT/f'app/build/reports/lint-results-{label}.{ext}'
        if src.exists():shutil.copy2(src,reports/src.name)
items=sorted(p for p in DEST.rglob('*') if p.is_file() and p.name not in {'SHA256SUMS.txt','bloomy-beauty-ban-giao.zip'})
(DEST/'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.relative_to(DEST).as_posix()}\n' for p in items))
with ZipFile(DEST/'bloomy-beauty-ban-giao.zip','w',ZIP_DEFLATED) as z:
    for p in items+[DEST/'SHA256SUMS.txt']:z.write(p,p.relative_to(DEST))
# Verify archives and source identity after writing, without extracting over the workspace.
for name in ['bloomy-beauty-source.zip','bloomy-beauty-ban-giao.zip']:
    with ZipFile(DEST/name) as z:
        assert z.testzip() is None
        assert not any('.local/' in n or n.endswith('/local.properties') or '..' in Path(n).parts for n in z.namelist())
with ZipFile(DEST/'bloomy-beauty-source.zip') as z:
    for item in records:assert hashlib.sha256(z.read(item['path'])).hexdigest()==item['sha256']
print(f'Packaged {len(source)} source files and verified checksums/ZIP integrity.')
print(DEST/'bloomy-beauty-ban-giao.zip')
