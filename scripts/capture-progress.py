"""Capture real Android demo screens for the first progress report."""
from pathlib import Path
import subprocess, sys, time, re, json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/bao-cao/tien-do-lan-1'
OUT.mkdir(parents=True, exist_ok=True)
ADB = '/Users/thuan/Library/Android/sdk/platform-tools/adb'
def adb(*args):
    return subprocess.check_output([ADB, *map(str,args)])
def nodes():
    adb('shell','uiautomator','dump','/data/local/tmp/progress.xml')
    root=ET.fromstring(adb('shell','cat','/data/local/tmp/progress.xml'))
    return list(root.iter('node'))
def dump():
    for n in nodes():
        a=n.attrib
        if a.get('text') or a.get('content-desc') or a.get('class')=='android.widget.EditText':
            print(a.get('text'), '|', a.get('content-desc'), '|', a.get('class'), '|', a.get('bounds'))
def tap(x,y): adb('shell','input','tap',x,y);time.sleep(.4)
def click(label):
    ns=nodes()
    matches=[n for n in ns if n.get('text')==label or n.get('content-desc')==label]
    if not matches: raise ValueError('Missing label '+label)
    b=list(map(int,re.findall(r'\d+',matches[0].get('bounds'))))
    tap((b[0]+b[2])//2,(b[1]+b[3])//2)
def fields():return [n for n in nodes() if n.get('class')=='android.widget.EditText']
def fill(index,value):
    b=list(map(int,re.findall(r'\d+',fields()[index].get('bounds'))))
    tap((b[0]+b[2])//2,(b[1]+b[3])//2)
    adb('shell','input','keyevent',123)
    adb('shell','input','keyevent','--longpress',67)
    adb('shell','input','text',value.replace(' ','%s'))
    adb('shell','input','keyevent',4)
def shot(name):
    time.sleep(.5)
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    print('Captured',name)

def type_slow(index,value):
    b=list(map(int,re.findall(r'\d+',fields()[index].get('bounds'))))
    tap((b[0]+b[2])//2,(b[1]+b[3])//2)
    adb('shell','input','keycombination',113,29)
    adb('shell','input','keyevent',67)
    for c in value:
        adb('shell','input','text','%s' if c==' ' else c)
        time.sleep(.18)
    adb('shell','input','keyevent',4)

if __name__=='__main__':
    exec(sys.argv[1])
