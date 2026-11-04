# OCR 모델 가중치 파일 목록

처음 시도한 PaddleOCR ONNX(PP-OCR) 경로의 가중치 목록이다. 이 경로는 후처리 뒤에도 CER이 0.492에서 0.408로 거의 줄지 않아 교재 OCR 본 경로에서 빠졌다. 지금은 `app/modules/OCR_v1`이 Marker로 먼저 추출하고 품질 게이트에 걸린 페이지만 MinerU로 다시 뽑는다.

가중치 파일은 git에 커밋하지 않는다(.gitignore로 제외). 저장소에는 문자 사전(`ppocr_keys_v1.txt`, `korean_dict_v5.txt`)만 있다.

---

## 파일 구조

PP-OCRv5 공식 URL에 접근할 수 없어 PP-OCRv4 Korean 가중치를 primary 슬롯에 두었다(2026-04-17 기준). v5 URL이 복구되면 primary 슬롯을 v5로 바꾼다.

```
models/
├── det.onnx               # PP-OCRv4 Chinese Text Detection (opset 11, ~4.5MB)
│                          # 출처: PaddleOCR/ch_PP-OCRv4_det_infer (Apache 2.0)
├── rec.onnx               # PP-OCRv4 Korean Recognition, fp32 (opset 11, ~23MB)
│                          # 출처: HuggingFace cycloneboy/korean_PP-OCRv4_rec_infer (Apache 2.0)
│                          # model.onnx SHA256: bae0fa6c08d0d86aab39293b31d572749b1e98d2563d6ea532d336c5d07bcf61
├── cls.onnx               # ch_ppocr_mobile_v2.0_cls (opset 11, ~568KB)
│                          # 출처: PaddleOCR/ch_ppocr_mobile_v2.0_cls_infer (Apache 2.0)
├── ppocr_keys_v1.txt      # 인식 문자 사전 (한국어 + 영어 + 중국어)
│                          # 출처: PaddleOCR 공식 리포 (Apache 2.0)
└── fallback/              # 비어 있음 (primary 슬롯에 v4 사용)
```

### 백업 파일

| 파일 | 설명 |
|---|---|
| `rec_fp16_legacy.onnx` | 교체 전 fp16 rec 모델 (~12MB). 품질 비교 뒤 삭제 예정 |

---

## 파일별 출처 및 라이선스

| 파일 | 출처 저장소 | 라이선스 | 상업 이용 |
|---|---|---|---|
| `det.onnx` | PaddleOCR `ch_PP-OCRv4_det_infer` | Apache 2.0 | 허용 |
| `rec.onnx` | HuggingFace `cycloneboy/korean_PP-OCRv4_rec_infer` | Apache 2.0 | 허용 |
| `cls.onnx` | PaddleOCR `ch_ppocr_mobile_v2.0_cls_infer` | Apache 2.0 | 허용 |
| `ppocr_keys_v1.txt` | PaddleOCR 공식 리포 | Apache 2.0 | 허용 |

---

## SHA-256 해시

수동으로 확인할 때 아래 값과 비교한다.

| 파일 | SHA-256 |
|---|---|
| `rec.onnx` (fp32 23MB) | `bae0fa6c08d0d86aab39293b31d572749b1e98d2563d6ea532d336c5d07bcf61` |
| `det.onnx` | 미기록 |
| `cls.onnx` | 미기록 |

확인 명령:
```bash
shasum -a 256 ai_connectors/ocr/models/rec.onnx
```

---

## 다운로드 절차

### PP-OCRv4 Korean (현재 슬롯)

```bash
# rec: HuggingFace 직접 다운로드 (fp32)
curl -L --fail \
  "https://huggingface.co/cycloneboy/korean_PP-OCRv4_rec_infer/resolve/main/model.onnx" \
  -o ai_connectors/ocr/models/rec.onnx
```

### PP-OCRv5로 바꿀 때 (URL 복구 후)

```bash
# rec 모델 (Korean mobile v5)
wget https://paddleocr.bj.bcebos.com/PP-OCRv5/Korean/korean_PP-OCRv5_mobile_rec_infer.tar
tar -xf korean_PP-OCRv5_mobile_rec_infer.tar
paddle2onnx --model_dir korean_PP-OCRv5_mobile_rec_infer \
  --model_filename inference.pdmodel \
  --params_filename inference.pdiparams \
  --save_file models/rec.onnx \
  --opset_version 17
```

---

## 라이선스

| 항목 | 라이선스 | 상업 이용 |
|---|---|---|
| PaddleOCR 코드 | Apache 2.0 | 허용 |
| PP-OCRv4/v5 가중치 | Apache 2.0 | 허용 |
| paddle2onnx | Apache 2.0 | 허용 |
| onnxruntime | MIT | 허용 |

PyMuPDF(AGPL-3.0)는 의존 경로에 넣지 않는다.
PDF 래스터화는 lib-rust(pdfium/BSD) 또는 pypdfium2(Apache 2.0/BSD)로 한다.
