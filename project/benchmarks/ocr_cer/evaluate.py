# Marker → 품질 게이트(서비스의 OCR_v1 코드) → 실패 페이지만 MinerU 순서로 CER을 잰다.
import glob, html, json, os, re, sys, unicodedata
from rapidfuzz.distance import Levenshtein
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import ocr_v1_loader; ocr_v1_loader.load()
from ocr_v1.quality.char_corruption import score_char_corruption
from ocr_v1.quality.reading_order import score_reading_order
from ocr_v1.quality.table_integrity import score_table_integrity
from ocr_v1.quality.formula_check import score_formula_preservation
from ocr_v1.quality.aggregate import aggregate_page_quality

B = os.environ.get("WORK", os.path.join(os.path.dirname(os.path.abspath(__file__)), "work"))
SFX = os.environ.get("SFX", "")
G = json.load(open(f"{B}/data{SFX}/gt.json")); GT, SCAN = G["gt"], set(G["scan"])
N = len(GT)

def norm(s):  # 한글·영문·숫자만 남긴 문자 단위 비교 (공백·기호·마크다운·HTML·LaTeX 명령 제외)
    s = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", s)
    s = re.sub(r"<[^>]+>", "", html.unescape(s))
    s = re.sub(r"\\[a-zA-Z]+", "", unicodedata.normalize("NFKC", s))
    return "".join(re.findall(r"[가-힣A-Za-z0-9]", s))

def cer(gt, hyp):
    g, h = norm(gt), norm(hyp)
    return Levenshtein.distance(g, h) / max(len(g), 1)

# OCR_v1 marker_engine의 _tables_from_md, _formulas_from_md와 같은 규칙
def tables_md(md):
    out, cur = [], []
    for l in md.splitlines() + [""]:
        s = l.strip()
        if s.startswith("|") and s.endswith("|"): cur.append(l)
        else:
            if len(cur) >= 2: out.append({"md": "\n".join(cur), "json": {}})
            cur = []
    return out

def formulas_md(md):
    return [m.group(1).strip() for m in re.finditer(r"\$\$(.+?)\$\$", md, re.DOTALL)] + \
           [m.group(1).strip() for m in re.finditer(r"(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)", md)]

def gate(md):
    c, _ = score_char_corruption(md); r, _ = score_reading_order(md)
    t, _ = score_table_integrity(md, tables_md(md)); f, _ = score_formula_preservation(md, formulas_md(md))
    return aggregate_page_quality(c, r, t, f)

def marker_pages():
    md = open(glob.glob(f"{B}/out{SFX}/marker/**/*.md", recursive=True)[0]).read()
    parts = re.split(r"\n*\{(\d+)\}-{20,}\n*", md)
    pages = [""] * N
    for i in range(1, len(parts) - 1, 2): pages[int(parts[i])] = parts[i + 1]
    return pages

def mineru_pages():
    items = json.load(open(glob.glob(f"{B}/out{SFX}/mineru/**/*content_list.json", recursive=True)[0]))
    pages = [[] for _ in range(N)]
    for it in items:
        txt = it.get("text") or it.get("table_body") or ""
        if it.get("type") == "table": txt = "\n".join(it.get("table_caption", [])) + txt
        pages[it["page_idx"]].append(txt)
    return ["\n\n".join(p) for p in pages]

def mean(xs): return round(sum(xs) / len(xs), 3) if xs else None

def report(name, pages, idx=None):
    idx = range(N) if idx is None else idx
    c = [cer(GT[i], pages[i]) for i in idx]
    return {"name": name, "mean_cer": mean(c), "pages": len(c),
            "corpus_cer": round(sum(Levenshtein.distance(norm(GT[i]), norm(pages[i])) for i in idx) / sum(len(norm(GT[i])) for i in idx), 3)}

if __name__ == "__main__":
    mk, mu = marker_pages(), mineru_pages()
    g = [gate(p) for p in mk]
    failed = [i for i, (_, ok) in enumerate(g) if not ok]
    pipe = [mu[i] if i in failed else mk[i] for i in range(N)]
    json.dump({"marker": mk, "mineru": mu, "pipe": pipe, "failed": failed, "gate": g}, open(f"{B}/out{SFX}/stage1.json", "w"), ensure_ascii=False)
    scan = sorted(SCAN); dig = [i for i in range(N) if i not in SCAN]
    rows = []
    for nm, pg in (("marker", mk), ("mineru", mu), ("pipeline", pipe)):
        rows += [report(nm + "/all", pg), report(nm + "/scan", pg, scan), report(nm + "/digital", pg, dig)]
    print(json.dumps({"failed_pages": failed, "fail_ratio": round(len(failed) / N, 3),
                      "per_page": [(i, i in SCAN, round(cer(GT[i], mk[i]), 3), round(cer(GT[i], mu[i]), 3), g[i][0]) for i in range(N)],
                      "rows": rows}, ensure_ascii=False, indent=0))
