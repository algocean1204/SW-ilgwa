#!/usr/bin/env python3
"""시연계정 S3 오디오(클린, 240슬라이드)를 받아 MP3 변환 + manifest 생성. 합성 안 함."""
import os, asyncio, asyncpg, urllib.request, subprocess, json, concurrent.futures
from pathlib import Path
SHOW="usr_01KV7PSA1PF8CERX7FRE2FYMKS"
COURSES={"파이썬 완전정복":"python","Rust언어 완전정복":"rust","운영체제 완전 정복":"os"}
OUT=Path("public/audio")
async def get_rows():
    c=await asyncpg.connect(os.environ["DATABASE_URL"]); rows=[]
    for cn,subj in COURSES.items():
        cid=await c.fetchval("SELECT id FROM course WHERE user_id=$1 AND course_name=$2 AND status='COMPLETED'",SHOW,cn)
        rs=await c.fetch("SELECT ch.order_index oi,s.slide_idx si,s.audio_url au,s.duration_sec dur FROM slide s JOIN chapter ch ON s.chapter_id=ch.id WHERE ch.course_id=$1 AND s.audio_url IS NOT NULL ORDER BY ch.order_index,s.slide_idx",cid)
        for r in rs: rows.append((subj,int(r['oi']),int(r['si']),r['au'],float(r['dur'])))
    await c.close(); return rows
def fetch_one(item):
    subj,oi,si,au,dur=item
    d=OUT/subj/f"ch{oi}"; d.mkdir(parents=True,exist_ok=True)
    mp3=d/f"slide_{si}.mp3"
    if mp3.exists() and mp3.stat().st_size>2000: return (subj,oi,si,dur,True)
    wav=f"/tmp/_dl_{subj}_{oi}_{si}.wav"
    try:
        urllib.request.urlretrieve(au,wav)
        subprocess.run(["ffmpeg","-y","-i",wav,"-ac","1","-b:a","64k",str(mp3)],capture_output=True,check=True)
        os.remove(wav); return (subj,oi,si,dur,True)
    except Exception as e:
        return (subj,oi,si,dur,False)
rows=asyncio.run(get_rows())
print(f"대상 {len(rows)}슬라이드 다운로드+MP3 시작",flush=True)
manifest=[]; ok=0; fail=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
    for subj,oi,si,dur,success in ex.map(fetch_one,rows):
        if success:
            ok+=1; manifest.append({"subject":subj,"chapter_index":oi,"slide_idx":si,"duration_sec":round(dur,1),"path":f"audio/{subj}/ch{oi}/slide_{si}.mp3"})
        else: fail.append(f"{subj}/ch{oi}/s{si}")
        if (ok+len(fail))%40==0: print(f"  진행 {ok+len(fail)}/{len(rows)} (성공{ok})",flush=True)
manifest.sort(key=lambda x:(x['subject'],x['chapter_index'],x['slide_idx']))
Path("src/mocks/data/audio-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=1),encoding="utf-8")
import glob
tot=sum(os.path.getsize(p) for p in glob.glob("public/audio/**/*.mp3",recursive=True))
print(f"완료 성공{ok}/{len(rows)} 실패{len(fail)} | manifest {len(manifest)}건 | 총 {tot/1024/1024:.0f}MB",flush=True)
if fail: print("실패:",fail[:10],flush=True)
