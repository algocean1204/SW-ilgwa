# Kanana 후처리를 게이트 실패 페이지에만 적용할 때와 전 페이지에 적용할 때의 CER, 원문에 없는 내용이 붙은 쪽 수를 잰다.
# 모델: kakaocorp/kanana-nano-2.1b-instruct (MLX). KANANA_MODEL 환경 변수로 바꿀 수 있다.
import json, os, re, sys
from mlx_lm import load, generate
sys.path.insert(0, sys.path[0])
from evaluate import GT, SCAN, N, B, SFX, norm, cer, mean

S = json.load(open(f"{B}/out{SFX}/stage1.json"))
pipe, failed = S["pipe"], S["failed"]
SYS = "너는 OCR 교정기다. 입력은 PDF에서 OCR로 뽑은 한국어 교재 텍스트다. 깨진 글자와 오탈자만 고쳐라. 문장을 추가·삭제·요약·설명하지 말고, 줄바꿈과 마크다운 구조를 유지한 채 교정한 텍스트만 출력해라."
model, tok = load(os.environ.get("KANANA_MODEL", "kakaocorp/kanana-nano-2.1b-instruct"))

def correct(page):
    out = []
    for chunk in re.split(r"\n{2,}", page):  # 단락 단위로 나눠 긴 입력에서 흐트러지지 않게
        if len(norm(chunk)) < 5: out.append(chunk); continue
        p = tok.apply_chat_template([{"role": "system", "content": SYS}, {"role": "user", "content": chunk}], add_generation_prompt=True)
        r = generate(model, tok, prompt=p, max_tokens=len(tok.encode(chunk)) * 2 + 32).strip()
        r = re.sub(r"^(물론|다음은|교정)[^\n]*[:：]\s*\n", "", r).strip().strip('"“”')  # 모델이 붙이는 머리말만 제거
        out.append(r)
    return "\n\n".join(out)

def added(gt, src, hyp, n=6):  # 교정본의 n-gram 중 원문·입력 어디에도 없는 비율
    g, s, h = norm(gt), norm(src), norm(hyp)
    grams = [h[i:i + n] for i in range(len(h) - n + 1)]
    return sum(1 for x in grams if x not in g and x not in s) / max(len(grams), 1)

fixed = {i: correct(pipe[i]) for i in range(N)}
json.dump(fixed, open(f"{B}/out{SFX}/kanana.json", "w"), ensure_ascii=False)
only_failed = [fixed[i] if i in failed else pipe[i] for i in range(N)]
res = {}
for nm, pg in (("pipeline", pipe), ("pipeline+kanana(failed only)", only_failed), ("pipeline+kanana(all)", [fixed[i] for i in range(N)])):
    res[nm] = {"all": mean([cer(GT[i], pg[i]) for i in range(N)]), "scan": mean([cer(GT[i], pg[i]) for i in sorted(SCAN)]),
               "failed": mean([cer(GT[i], pg[i]) for i in failed])}
res["added_suspect_pages(all, >5%)"] = sum(1 for i in range(N) if added(GT[i], pipe[i], fixed[i]) > 0.05)
res["per_page"] = [(i, round(cer(GT[i], pipe[i]), 3), round(cer(GT[i], fixed[i]), 3), round(added(GT[i], pipe[i], fixed[i]), 3)) for i in range(N)]
print(json.dumps(res, ensure_ascii=False, indent=0))
