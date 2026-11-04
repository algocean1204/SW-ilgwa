# 교재형 테스트셋 30쪽(텍스트 PDF 22쪽 + 스캔 8쪽)을 만든다. 정답은 디지털 원본 페이지 텍스트다.
# 사용: python build_set.py <출력 폴더> [본문으로 쓸 md 폴더 ...]  (기본: project/Docs)
import glob, html as html_mod, io, json, os, random, re, subprocess, sys
import numpy as np, pymupdf
from PIL import Image, ImageFilter

OUT = sys.argv[1]
SRC_DIRS = sys.argv[2:] or [os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "Docs")]
rng = random.Random(7)

def clean(s):
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
    s = re.sub(r"[*`>#]", "", s).strip()
    s = re.sub(r"^([-•]|\d+(\.\d+)*\.?)\s+", "", s)
    s = re.sub(r"[^가-힣\x20-\x7e·~‘’“”…]", "", s)  # Pretendard에 있는 글자만
    return re.sub(r"\s{2,}", " ", s).strip()

paras, heads, tables = [], [], []
for f in sorted(f for d in SRC_DIRS for f in glob.glob(d + "/**/*.md", recursive=True)):
    lines = open(f, encoding="utf-8").read().splitlines()
    i = 0
    while i < len(lines):
        l = lines[i]
        if l.startswith("|"):
            blk = []
            while i < len(lines) and lines[i].startswith("|"):
                blk.append(lines[i]); i += 1
            rows = [[clean(c) for c in r.strip("|").split("|")] for r in blk if not re.match(r"^\|[\s:|-]+\|$", r)]
            if 3 <= len(rows) <= 6 and 3 <= len(rows[0]) <= 4 and all(len(r) == len(rows[0]) for r in rows) \
               and all(len(c) <= 18 for r in rows for c in r) and sum(len(re.findall("[가-힣]", c)) for r in rows for c in r) > 15:
                tables.append(rows)
            continue
        if l.startswith("## "):
            h = clean(l)
            if 4 <= len(h) <= 30 and re.search("[가-힣]", h): heads.append(h)
        else:
            c = clean(l)
            if len(re.findall("[가-힣]", c)) >= 50 and len(re.findall(r"[/_\\{}<>=]", c)) < 3: paras.append(c)
        i += 1
paras, heads = list(dict.fromkeys(paras)), list(dict.fromkeys(heads))
rng.shuffle(paras); rng.shuffle(heads); rng.shuffle(tables)
FORMULAS = ["F = m * a", "y = a*x^2 + b*x + c", "P(A|B) = P(B|A) * P(A) / P(B)", "E = m*c^2", "v = v0 + a*t",
            "s = v0*t + (1/2)*a*t^2", "log2(8) = 3", "a^2 + b^2 = c^2", "W = F * s * cos(t)", "m = (x1 + x2 + x3) / 3"]

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
css = "@page{size:A4;margin:20mm} *{font-family:Pretendard;font-size:10.5pt;line-height:1.55} h2{font-size:15pt;margin:0 0 8pt}" \
      "p{margin:0 0 7pt} .f{text-align:center;margin:6pt 0} table{border-collapse:collapse;margin:6pt 0} td{border:1px solid #444;padding:3pt 6pt}"
E = html_mod.escape
digital = pymupdf.open()
pi = ti = fi = 0
for n in range(30):
    for k in (7, 6, 5, 4, 3, 2):
        body = [f"<p>{E(p)}</p>" for p in paras[pi:pi + k]]
        if n % 3 == 1: body.insert(1, "<table>" + "".join("<tr>" + "".join(f"<td>{E(c)}</td>" for c in r) + "</tr>" for r in tables[ti % len(tables)]) + "</table>")
        if n % 3 == 2: body.insert(1, f"<p class='f'>{E(FORMULAS[fi % len(FORMULAS)])}</p>")
        h = f"<!doctype html><meta charset=utf-8><style>{css}</style><h2>{n+1}. {E(heads[n % len(heads)])}</h2>" + "".join(body)
        open(OUT + "/p.html", "w").write(h)
        subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer", f"--print-to-pdf={OUT}/p.pdf", f"file://{OUT}/p.html"], capture_output=True, check=True)
        one = pymupdf.open(OUT + "/p.pdf")
        if len(one) == 1: break
    digital.insert_pdf(one)
    pi += k; ti += n % 3 == 1; fi += n % 3 == 2

SCAN = {3, 7, 11, 15, 19, 23, 26, 29}  # 8/30 = 27%
gt = [p.get_text() for p in digital]
mixed = pymupdf.open()
for n, p in enumerate(digital):
    if n not in SCAN:
        mixed.insert_pdf(digital, from_page=n, to_page=n); continue
    hard = n in (23, 29)
    pix = p.get_pixmap(dpi=110 if hard else 150, colorspace=pymupdf.csGRAY)
    im = Image.frombytes("L", (pix.width, pix.height), pix.samples).rotate(rng.uniform(-0.8, 0.8), fillcolor=255, expand=False)
    im = im.filter(ImageFilter.GaussianBlur(0.9 if hard else 0.6))
    a = np.asarray(im, dtype=np.float32) + np.random.default_rng(n).normal(0, 12 if hard else 7, (im.height, im.width))
    buf = io.BytesIO(); Image.fromarray(a.clip(0, 255).astype(np.uint8)).save(buf, "JPEG", quality=45 if hard else 60)
    q = mixed.new_page(width=595, height=842); q.insert_image(q.rect, stream=buf.getvalue())
mixed.save(OUT + "/testset.pdf"); digital.save(OUT + "/testset_digital.pdf")
json.dump({"gt": gt, "scan": sorted(SCAN)}, open(OUT + "/gt.json", "w"), ensure_ascii=False)
print(len(paras), len(heads), len(tables), "pages", len(mixed), "chars", sum(len(g) for g in gt))
