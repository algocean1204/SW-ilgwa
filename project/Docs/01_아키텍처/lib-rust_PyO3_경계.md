# lib-rust PyO3 경계 명세

lib-rust와 FastAPI 사이의 PyO3 wheel 인터페이스를 정한다.

> 최종 업데이트: 2026-06-07  
> 관련 문서:
> - [`AI_커넥터_레지스트리.md`](./AI_커넥터_레지스트리.md): AI 커넥터 레지스트리 패턴
> - [부록 A](#부록-a-초기-설계-이력-구-러스트라이브러리md): Actix-web 서버 폐기 이력과 구 OCR 경로 폐기 이력

---

## 1. 2026-04-17 결정 맥락

처음에는 `backend-rust`라는 이름의 Axum 기반 gRPC 독립 서버로 설계했다. 2026-04-17에 역할을 바꿔 PyO3 wheel 라이브러리로 다시 정의했고, 레포 이름도 `lib-rust`로 바꿨다.

FastAPI VM 안에서 함수 호출로 끝낸다. 별도 서버도 subprocess도 없다.

### 변경 전후 비교

| 항목 | 변경 전 (Axum gRPC) | 변경 후 (PyO3 wheel) |
|---|---|---|
| 통신 방식 | gRPC (네트워크) | 함수 호출 (~μs) |
| 프로세스 | 독립 서버 | FastAPI VM in-process |
| 배포 단위 | 서비스 2개 | FastAPI 단일 이미지 |
| 장애 포인트 | 서버 2개 + 네트워크 | FastAPI VM 하나 |

---

## 2. 불변 원칙 (변경 불가)

lib-rust는 PyO3 wheel 라이브러리다. 아래 형태로는 만들지 않는다.

- 별도 서버 (HTTP, gRPC, Unix socket 모두 포함)
- subprocess 실행 (`subprocess.run`, `Popen` 호출)
- 독립 프로세스 (FastAPI와 별개 프로세스)

빌드 산출물은 Python wheel(`lib_rust-*.whl`)이며, `uv pip install`로 FastAPI venv에 설치된다. FastAPI 코드가 `import lib_rust` 한 줄로 사용한다.

---

## 3. lib-rust 범위

### 하는 일

- PDF → RGB ndarray 래스터화 (pypdfium2 또는 Rust 자체 pdfium 바인딩)
- 이미지 리사이즈, 대비 조정, 디스큐(deskew)
- (향후) 오디오 전처리 — 필요성 확인 시 추가

### 하지 않는 일 (Python/FastAPI에서 처리)

- OCR 모델 추론 (FastAPI OCR_V1의 Marker, MinerU, Kanana-2 교정)
- 네트워크 I/O (HTTP, gRPC 포함)
- HTTP 서버 역할
- zip 파일 파싱 및 보안 검증
- FastAPI 라우팅, 비즈니스 로직

---

## 4. Python 향 API 시그니처

FastAPI Python 코드가 직접 호출하는 함수다.

### 4-1. `rasterize_pdf`

```python
def rasterize_pdf(pdf_bytes: bytes, dpi: int = 300) -> list[numpy.ndarray]:
    ...
```

| 항목 | 내용 |
|---|---|
| 입력 | `pdf_bytes`: 원본 PDF 바이트 |
| 입력 | `dpi`: 래스터화 해상도. 기본값 300. 권장 범위 150~400 |
| 출력 | 페이지별 numpy ndarray 리스트. 각 배열 shape `(H, W, 3)`, dtype `uint8`, 색공간 RGB |
| 예외 | PDF가 유효하지 않으면 `LibRustError(INVALID_PDF)` 발생 |
| 스레드 | 내부 `py.allow_threads` 적용. GIL 해제 상태로 실행 |

### 4-2. `normalize_image`

```python
def normalize_image(img: numpy.ndarray, target_h: int = 640) -> numpy.ndarray:
    ...
```

| 항목 | 내용 |
|---|---|
| 입력 | `img`: shape `(H, W, 3)`, dtype `uint8`, RGB |
| 입력 | `target_h`: 정규화 후 목표 높이(px). 가로세로 비율 유지 |
| 출력 | shape `(target_h, W', 3)`, dtype `uint8` |
| 예외 | 빈 배열이나 지원하지 않는 shape이면 `LibRustError(UNSUPPORTED_FORMAT)` 발생 |

### 4-3. `deskew_image`

```python
def deskew_image(img: numpy.ndarray, max_angle_deg: float = 15.0) -> numpy.ndarray:
    ...
```

| 항목 | 내용 |
|---|---|
| 입력 | `img`: shape `(H, W, 3)`, dtype `uint8` |
| 입력 | `max_angle_deg`: 보정 허용 최대 각도. 이 범위를 벗어난 기울기는 무보정 반환 |
| 출력 | 기울기 보정된 ndarray. shape 동일 |
| 참고 | 기울기가 `max_angle_deg` 이내면 보정하고, 넘으면 오보정을 피하려고 원본을 반환한다 |

### 4-4. `preprocess_image`

```python
def preprocess_image(img: numpy.ndarray, target_h: int = 640) -> numpy.ndarray:
    ...
```

normalize_image와 deskew_image를 차례로 적용하는 편의 함수다. FFI 호출을 한 번으로 줄여 오버헤드를 낮춘다.

| 항목 | 내용 |
|---|---|
| 출력 | deskew 적용 후 정규화된 ndarray |

---

## 5. 데이터 타입 규약

- 모든 이미지 ndarray: shape `(H, W, C)`, C=3 (RGB), dtype `uint8`
- 그레이스케일은 lib-rust가 직접 노출하지 않는다. OCR 엔진에 넣기 전에 필요하면 Python에서 변환한다
- numpy 배열은 `pyo3-numpy` crate의 zero-copy 인터페이스로 넘겨 복사를 줄인다

---

## 6. 에러 처리 계약

lib-rust에서 Rust 패닉(panic)이 PyO3 경계를 넘어오면 안 된다. 모든 에러는 Rust `Result` → Python 예외 변환으로 처리한다.

### LibRustError 예외 클래스

```python
class LibRustError(Exception):
    code: str   # 아래 에러 코드 중 하나
    message: str
```

### 에러 코드

| 코드 | 의미 | 대표 원인 |
|---|---|---|
| `INVALID_PDF` | PDF 파싱 실패 | 손상된 파일, 암호화된 PDF |
| `OOM` | 메모리 부족 | 고해상도 DPI, 과도한 페이지 수 |
| `UNSUPPORTED_FORMAT` | 지원하지 않는 입력 | 빈 배열, 잘못된 dtype |
| `INTERNAL` | 그 외 내부 오류 | Rust 내부 예외 발생 |

FastAPI 쪽은 `except LibRustError as e`로 잡아 에러 코드에 맞는 HTTP 응답을 만든다. `INTERNAL`은 서버 로그에 남기고 500으로 반환한다.

---

## 7. 성능 SLA (예상치 — 실측 전 기준값)

> 아래 수치는 Apple M 시리즈 기준 예상치다. 실제 측정 후 갱신한다.

| 작업 | 목표 시간 | 조건 |
|---|---|---|
| A4 1페이지 PDF 래스터화 | < 200ms | DPI 300 |
| 이미지 normalize | < 50ms/페이지 | target_h 640 |
| 메모리 사용 | < 100MB/동시 호출 | 단일 요청 기준 |

---

## 8. 버전 호환성

| 항목 | 제약 |
|---|---|
| Python | `>=3.11` |
| numpy | `>=1.26, <2.0` (ClearerVoice 의존성 제약과 정렬) |
| 빌드 도구 | `maturin build --release` |
| Rust edition | 2021 |

wheel 버전이 올라가면 `backend-fastapi/pyproject.toml`에서 정확한 버전을 핀 고정한다. `uv sync`가 재설치를 처리한다.

---

## 9. 테스트 계약

| 위치 | 내용 |
|---|---|
| `lib-rust/tests/` | Rust 단위 테스트. cargo test로 실행. 각 함수의 정상 케이스 + 에러 케이스 포함 |
| `backend-fastapi/tests/test_lib_rust_integration.py` | Python 통합 테스트. 실제 wheel을 import해 함수 호출 검증 |
| `Test_FastAPI_Module/` | 한국어 HTML 테스트 페이지로 end-to-end 동작 확인 |

통합 테스트는 golden fixture(고정 입력 → 고정 출력)로 결정론적으로 검증한다. 같은 PDF와 같은 DPI에서 출력 ndarray가 항상 동일해야 한다.

---

## 10. FastAPI에서 사용하는 시퀀스 흐름

```
FastAPI route (POST /ocr)
    │
    │  pdf_bytes 수신
    │
    ├─► lib_rust.rasterize_pdf(pdf_bytes, dpi=300)
    │       │  GIL 해제 (py.allow_threads)
    │       │  Rust에서 PDF → RGB ndarray 리스트 반환
    │       ▼
    │   pages: list[numpy.ndarray]  (각 H×W×3, uint8)
    │
    ├─► lib_rust.preprocess_image(page, target_h=640)  [페이지별]
    │       │  deskew + normalize
    │       ▼
    │   preprocessed: numpy.ndarray
    │
    ├─► OCR_V1 엔진 (Marker, 게이트 실패 페이지는 MinerU + Kanana-2)  [Python]
    │       ▼
    │   ocr_text: str
    │
    └─► HTTP response  {text: ocr_text, page_count: N}
```

2026-04-19 이전에는 OCR 단계에서 ONNX Runtime(CPU)으로 PaddleOCR PP-OCRv4 det/rec를 돌렸다. 이 경로는 폐기했다.

---

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-04-17 | 최초 작성. backend-rust → lib-rust 전환 맥락, PyO3 wheel 불변 원칙, API 시그니처 4개, 에러 계약, 성능 SLA 초안. |
| 2026-11-02 | 포트폴리오 정리: 시퀀스 흐름의 OCR 단계를 OCR_V1로 맞추고, 구 `러스트라이브러리.md`를 부록 A로 합침. |

---

## 부록 A. 초기 설계 이력 (구 러스트라이브러리.md)

2026-06-07 기준 현행 설계는 이 문서 본문이다. 아래 두 이력만 남긴다.

1. Actix-web 독립 서버 폐기: `ocr.ilkwa.com` 서브도메인에서 돌던 Actix-web 독립 Rust 서버(`src-rust/`)는 2026-04-17에 폐기했다. PyO3 wheel 라이브러리(`lib_rust`)로 전환해 FastAPI VM 안에서 함수 호출로 동작한다. 별도 서버·포트·소켓 통신이 없다.
2. PaddleOCR ONNX + Rust 전처리 경로 폐기: `ilkwa_rust_ext.ocr_preprocessor`(리사이즈·그레이스케일·이진화) + PaddleOCR ONNX Runtime CPU 단일 경로는 2026-04-19에 폐기했다. 교재 OCR은 OCR_V1(Marker + MinerU) 단일 경로로, 과제 채점은 Sonnet VLM 직접 처리로 바꿨다.

현행 lib-rust 역할은 PDF 래스터화(`rasterize_pdf`)와 이미지 정규화(`normalize_image`, `preprocess_image`) 보조로 한정한다.
