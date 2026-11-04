# 스트레스 세트: 같은 30쪽에서 스캔 8쪽만 저화질(90dpi, 흐림, 잡음, JPEG 35)로 바꾼다.
import io, json, os, random, shutil, sys
import numpy as np, pymupdf
from PIL import Image, ImageFilter
B = os.environ.get("WORK", os.path.join(os.path.dirname(os.path.abspath(__file__)), "work")); rng = random.Random(11)
g = json.load(open(f"{B}/data/gt.json")); dig = pymupdf.open(f"{B}/data/testset_digital.pdf"); out = pymupdf.open()
for n, p in enumerate(dig):
    if n not in g["scan"]: out.insert_pdf(dig, from_page=n, to_page=n); continue
    pix = p.get_pixmap(dpi=90, colorspace=pymupdf.csGRAY)
    im = Image.frombytes("L", (pix.width, pix.height), pix.samples).rotate(rng.uniform(-1.5, 1.5), fillcolor=255).filter(ImageFilter.GaussianBlur(1.0))
    a = np.asarray(im, dtype=np.float32) + np.random.default_rng(n).normal(0, 18, (im.height, im.width))
    buf = io.BytesIO(); Image.fromarray(a.clip(0, 255).astype(np.uint8)).save(buf, "JPEG", quality=35)
    q = out.new_page(width=595, height=842); q.insert_image(q.rect, stream=buf.getvalue())
out.save(f"{B}/data_hard/testset.pdf"); shutil.copy(f"{B}/data/gt.json", f"{B}/data_hard/gt.json")
out[23].get_pixmap(dpi=70).save(f"{B}/data_hard/prev23.png")
