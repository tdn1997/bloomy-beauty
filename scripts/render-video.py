"""Assemble native screen recordings into a subtitled demonstration video."""
from pathlib import Path
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'.local/recordings'
parts=[source/f'part{i}.mp4' for i in range(1,4)]
if not all(p.exists() for p in parts):raise SystemExit('Need native part1/part2/part3 recordings in .local/recordings.')
chapters=json.loads((source/'chapters.json').read_text())
out=ROOT/'docs/trinh-dien'
out.mkdir(exist_ok=True)
speed=1.6
# Remove the idle interval 10–60 seconds from part 1; other native frames stay ordered.
def timeline(value):return max(0,(value-(50 if value>=60 else 0))/speed)
def timestamp(seconds):
    n=round(seconds*1000)
    return f'{n//3600000:02}:{n//60000%60:02}:{n//1000%60:02},{n%1000:03}'
lines=[]
for i,chapter in enumerate(chapters):
    start=timeline(chapter['time'])
    end=timeline(chapters[i+1]['time']) if i+1<len(chapters) else 250
    if end>start:lines.append(f'{len(lines)+1}\n{timestamp(start)} --> {timestamp(end)}\n{chapter["text"]}\n')
(out/'demo.srt').write_text('\n'.join(lines))
filters="[0:v]fps=30,tpad=stop_mode=clone:stop_duration=180,trim=start=0:end=10,setpts=PTS-STARTPTS[a];[0:v]fps=30,tpad=stop_mode=clone:stop_duration=180,trim=start=60:end=180,setpts=PTS-STARTPTS[b];[1:v]fps=30,tpad=stop_mode=clone:stop_duration=180,trim=duration=180,setpts=PTS-STARTPTS[c];[2:v]fps=30,tpad=stop_mode=clone:stop_duration=90,trim=duration=90,setpts=PTS-STARTPTS[d];[a][b][c][d]concat=n=4:v=1:a=0,setpts=PTS/1.6,subtitles=docs/trinh-dien/demo.srt:force_style='FontName=Arial,FontSize=6,Outline=1,BorderStyle=3,BackColour=&H80000000,MarginV=12'[v]"
args=['ffmpeg','-y','-loglevel','warning','-nostats']
for p in parts:args+=['-i',str(p)]
args+=['-filter_complex',filters,'-map','[v]','-an','-r','30','-c:v','libx264','-preset','veryfast','-crf','24','-pix_fmt','yuv420p','-movflags','+faststart',str(out/'bloomy-beauty-demo.mp4')]
subprocess.run(args,cwd=ROOT,check=True)
print(out/'bloomy-beauty-demo.mp4')
