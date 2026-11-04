# Qdrant 적재 전 정규화 실측. 처음 로직(정규화 없음, Dense 단독)과 개선 로직(서비스 후처리 5단계, Dense+Sparse RRF)을 비교한다.
# 리랭커 단계(서비스 hybrid_search)까지 포함해 Top-1·3·5·10을 잰다.
# 같은 Marker 출력, 같은 청커(600~1,200 토큰), 같은 BGE-M3, 인메모리 Qdrant. 후처리·청커는 서비스 코드(OCR_v1)를 그대로 쓴다.
# 입력: WORK 폴더의 data/gt.json, out/marker. SRC=textlayer 이면 Marker 대신 PDF 텍스트 레이어를 쓴다.
import asyncio, glob, json, math, random, re, sys, unicodedata
from collections import Counter
from rapidfuzz import fuzz
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, PointStruct, SparseVector, SparseVectorParams, VectorParams
from FlagEmbedding import BGEM3FlagModel, FlagReranker
import os
B = os.environ.get("WORK", os.path.join(os.path.dirname(os.path.abspath(__file__)), "work"))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import ocr_v1_loader; ocr_v1_loader.load()
from ocr_v1.postprocess import run_all
from ocr_v1.chunking.section_chunker import SectionChunker

G = json.load(open(f"{B}/data/gt.json")); PARAS, TITLE = G["paras"], G["title"]
md = open(glob.glob(f"{B}/out/marker/**/*.md", recursive=True)[0]).read()
parts = re.split(r"\n*\{(\d+)\}-{20,}\n*", md)
pages = [{"page_num": int(parts[i]) + 1, "markdown": parts[i + 1], "engine": "marker"} for i in range(1, len(parts) - 1, 2)]
SRC = os.environ.get("SRC", "marker")
if SRC == "textlayer":  # 비교용 변형: Marker 대신 PDF 텍스트 레이어를 그대로 쓴 경우(머리글·쪽 번호·줄바꿈 포함)
    import pymupdf
    pages = [{"page_num": i + 1, "markdown": p.get_text(), "engine": "textlayer"} for i, p in enumerate(pymupdf.open(f"{B}/data/book_digital.pdf"))]

def norm(s): return "".join(re.findall(r"[가-힣A-Za-z0-9]", unicodedata.normalize("NFKC", s)))
def combine(ps): return "".join(f"\n\n<!-- page:{p['page_num']} -->\n\n" + p["markdown"] for p in ps)  # chunk_node._combine_pages 그대로

async def build():
    raw = pages
    clean = [dict(p, markdown=await run_all(p["markdown"])) for p in pages]  # postprocess_node: 페이지별 run_all
    ck = SectionChunker()
    return await ck.chunk(combine(raw), raw), await ck.chunk(combine(clean), clean)
raw_chunks, clean_chunks = asyncio.run(build())

# 잡음 지표
PN = re.compile(r"^\s*[-–—]?\s*\d+\s*[-–—]?\s*$", re.M)
def noise(chunks):
    t = norm(TITLE)
    return {"chunks": len(chunks), "avg_tokens": round(sum(c["token_count"] for c in chunks) / len(chunks)),
            "running_header_hits": sum(norm(c["text"]).count(t) for c in chunks),
            "page_number_lines": sum(len(PN.findall(c["text"])) for c in chunks)}
def split_paras(chunks):  # 한 청크 안에 온전히 들어가지 못한 문단 수 (앞 15자와 뒤 15자가 같은 청크에 없음)
    ns = [norm(c["text"]) for c in chunks]; n = 0
    for p in PARAS:
        q = norm(p["text"]); a, b = q[:15], q[-15:]
        if not any(fuzz.partial_ratio(a, x) >= 90 and fuzz.partial_ratio(b, x) >= 90 for x in ns): n += 1
    return n

# 질의: 문단 120개 표본, 키워드형(IDF 상위 4단어) + 문장형(가장 긴 문장에서 세 번째 단어마다 생략)
rng = random.Random(11)
words = lambda s: [w for w in re.findall(r"[가-힣A-Za-z0-9]{2,}", s)]
df = Counter(w for p in PARAS for w in set(words(p["text"])))
targets = rng.sample(PARAS, 120)
def kw_query(p):
    ws = sorted(set(words(p["text"])), key=lambda w: (-math.log(len(PARAS) / df[w]), w))
    return " ".join(ws[:4])
def sent_query(p):
    s = max(re.split(r"(?<=[.!?다])\s+", p["text"]), key=len).split()
    return " ".join(w for i, w in enumerate(s[:30]) if i % 3 != 2)
queries = [(p["id"], "키워드형", kw_query(p)) for p in targets] + [(p["id"], "문장형", sent_query(p)) for p in targets]

def relevant(chunks, pid):  # 정답 청크 = 대상 문단 가운데 30자를 포함하는 청크
    q = norm(PARAS[pid]["text"]); m = len(q) // 2; span = q[max(0, m - 15): m + 15]
    return {i for i, c in enumerate(chunks) if fuzz.partial_ratio(span, norm(c["text"])) >= 90}

model = BGEM3FlagModel("BAAI/bge-m3", use_fp16=True, devices="mps")
def emb(texts):
    o = model.encode(texts, batch_size=8, return_dense=True, return_sparse=True, return_colbert_vecs=False)
    return [{"dense": d.tolist(), "sparse": {"indices": [int(k) for k in w], "values": [float(v) for v in w.values()]}} for d, w in zip(o["dense_vecs"], o["lexical_weights"])]

client = QdrantClient(":memory:")
def index(name, chunks):
    client.create_collection(name, vectors_config={"dense": VectorParams(size=1024, distance=Distance.COSINE)}, sparse_vectors_config={"sparse": SparseVectorParams()})
    E = emb([c["text"] for c in chunks])
    client.upsert(name, [PointStruct(id=i, vector={"dense": e["dense"], "sparse": SparseVector(**e["sparse"])}) for i, e in enumerate(E)])
index("raw", raw_chunks); index("clean", clean_chunks)
QE = emb([q for _, _, q in queries])

def rrf(dense, sparse, top_k, k=60):  # qdrant_store._rrf_merge 그대로
    s = {}
    for r, h in enumerate(dense, 1): s[h.id] = s.get(h.id, 0) + 1 / (k + r)
    for r, h in enumerate(sparse, 1): s[h.id] = s.get(h.id, 0) + 1 / (k + r)
    return [i for i, _ in sorted(s.items(), key=lambda x: -x[1])[:top_k]]
def search(col, e, mode, top_k=10):
    d = client.query_points(col, query=e["dense"], using="dense", limit=top_k * (2 if mode == "hybrid" else 1)).points
    if mode == "dense": return [h.id for h in d][:top_k]
    sp = client.query_points(col, query=SparseVector(**e["sparse"]), using="sparse", limit=top_k * 2).points
    return rrf(d, sp, top_k)

# 서비스 hybrid_search와 같은 순서: RRF 상위 10개(OCR_V1_RERANK_TOP_K)를 BGE-Reranker로 다시 매긴다
reranker = FlagReranker("BAAI/bge-reranker-v2-m3", use_fp16=sys.platform != "darwin")  # bge_reranker.py와 같은 설정
def ranked(col, chunks, mode, q, e):
    ids = search(col, e, "hybrid" if mode == "rerank" else mode)
    if mode != "rerank": return ids
    s = reranker.compute_score([[q, chunks[i]["text"]] for i in ids], normalize=True)
    return [i for _, i in sorted(zip(s, ids), key=lambda x: -x[0])]

res = {}
for col, chunks in (("raw", raw_chunks), ("clean", clean_chunks)):
    rel = {pid: relevant(chunks, pid) for pid, _, _ in queries}
    for mode in ("dense", "hybrid", "rerank"):
        ranks = [next((i + 1 for i, x in enumerate(ranked(col, chunks, mode, q, e)) if x in rel[pid]), None) for (pid, _, q), e in zip(queries, QE)]
        for qt in ("키워드형", "문장형", "전체"):
            rs = [r for (_, t, _), r in zip(queries, ranks) if qt == "전체" or t == qt]; n = len(rs)
            hit = lambda k: round(sum(bool(r and r <= k) for r in rs) / n, 3)
            res[f"{col}/{mode}/{qt}"] = {"hit@1": hit(1), "hit@3": hit(3), "hit@5": hit(5), "hit@10": hit(10),
                                         "mrr@10": round(sum(1 / r for r in rs if r) / n, 3), "n": n}
out = {"pages": len(pages), "paras": len(PARAS), "queries": len(queries),
       "noise": {"raw": noise(raw_chunks), "clean": noise(clean_chunks)},
       "split_paras": {"raw": split_paras(raw_chunks), "clean": split_paras(clean_chunks)},
       "retrieval": res, "examples": queries[:3] + queries[120:123]}
json.dump(out, open(f"{B}/out/result_{SRC}.json", "w"), ensure_ascii=False, indent=1)
print(json.dumps({k: v for k, v in out.items() if k != "examples"}, ensure_ascii=False, indent=1))
