# 교재형 PDF: 장·절 구조, 쪽마다 머리글(교재명)과 쪽 번호, 문단이 쪽을 넘어 이어짐. 4쪽마다 1쪽은 스캔본(약 25%).
# 정답은 문단 목록(gt.json)이다. 사용: python build_book.py <출력 폴더> [본문으로 쓸 md 폴더 ...]  (기본: project/Docs)
import glob, html, io, json, os, random, re, subprocess, sys
import numpy as np, pymupdf
from PIL import Image, ImageFilter

OUT = sys.argv[1]
SRC_DIRS = sys.argv[2:] or [os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "Docs")]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
rng = random.Random(7)

def clean(s):
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
    s = re.sub(r"[*`>#]", "", s).strip()
    s = re.sub(r"^([-•]|\d+(\.\d+)*\.?)\s+", "", s)
    s = re.sub(r"[^가-힣\x20-\x7e·~‘’“”…]", "", s)
    return re.sub(r"\s{2,}", " ", s).strip()

paras, heads = [], []
for f in sorted(f for d in SRC_DIRS for f in glob.glob(d + "/**/*.md", recursive=True)):
    for l in open(f, encoding="utf-8").read().splitlines():
        if l.startswith("## "):
            h = clean(l)
            if 4 <= len(h) <= 28 and re.search("[가-힣]", h): heads.append(h)
        elif not l.startswith("|"):
            c = clean(l)
            if len(re.findall("[가-힣]", c)) >= 40 and len(re.findall(r"[/_\\{}<>=]", c)) < 3: paras.append(c)
paras, heads = list(dict.fromkeys(paras)), list(dict.fromkeys(heads))
rng.shuffle(paras); rng.shuffle(heads)
print("available", len(paras)); paras = paras[:480]

E = html.escape
TITLE = "AI 서비스 개발 실무 · 실습 교재"
css = ("@page{size:A4;margin:22mm 20mm;@top-center{content:\"%s\";font:9pt Pretendard;color:#444}"
       "@bottom-center{content:\"- \" counter(page) \" -\";font:9pt Pretendard;color:#444}}"
       "body{font:10.5pt/1.6 Pretendard} h1{font-size:17pt;margin:18pt 0 8pt} h2{font-size:13pt;margin:12pt 0 6pt} p{margin:0 0 7pt;text-align:justify}") % TITLE
body, gt, pi, sec = [], [], 0, 0
for ch in range(8):
    body.append(f"<h1>{ch + 1}장. {E(heads[ch])}</h1>")
    for s in range(10):
        title = f"{ch + 1}.{s + 1} {heads[8 + sec]}"; sec += 1
        body.append(f"<h2>{E(title)}</h2>")
        for _ in range(6):
            p = paras[pi]; pi += 1
            body.append(f"<p>{E(p)}</p>"); gt.append({"id": len(gt), "section": title, "text": p})
open(f"{OUT}/book.html", "w").write(f"<!doctype html><meta charset=utf-8><style>{css}</style>" + "".join(body))
subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer", f"--print-to-pdf={OUT}/book_digital.pdf", f"file://{OUT}/book.html"], capture_output=True, check=True)

dig = pymupdf.open(f"{OUT}/book_digital.pdf"); out = pymupdf.open(); scan = []
for n, p in enumerate(dig):
    if n % 4 != 3: out.insert_pdf(dig, from_page=n, to_page=n); continue
    scan.append(n)
    pix = p.get_pixmap(dpi=150, colorspace=pymupdf.csGRAY)
    im = Image.frombytes("L", (pix.width, pix.height), pix.samples).rotate(rng.uniform(-0.6, 0.6), fillcolor=255).filter(ImageFilter.GaussianBlur(0.6))
    a = np.asarray(im, dtype=np.float32) + np.random.default_rng(n).normal(0, 7, (im.height, im.width))
    buf = io.BytesIO(); Image.fromarray(a.clip(0, 255).astype(np.uint8)).save(buf, "JPEG", quality=60)
    q = out.new_page(width=p.rect.width, height=p.rect.height); q.insert_image(q.rect, stream=buf.getvalue())
out.save(f"{OUT}/book.pdf")
json.dump({"paras": gt, "scan_pages": scan, "pages": len(dig), "title": TITLE}, open(f"{OUT}/gt.json", "w"), ensure_ascii=False)
print("pages", len(dig), "scan", len(scan), "paras", len(gt))
