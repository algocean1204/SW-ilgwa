# lib-rust

FastAPI 프로세스 안에서 함수로 호출하는 PDF·이미지 전처리용 PyO3 Rust 라이브러리다. pip으로 설치하는 Python 확장 모듈(wheel)로 배포한다.

---

## 이 레포의 역할

| 구분 | 내용 |
|---|---|
| IN | FastAPI 프로세스에서 전달받은 PDF 바이트 / 이미지 바이트 |
| 처리 | PDF → 이미지 래스터화, OCR 모델 입력 규격에 맞춘 이미지 전처리 |
| OUT | 페이지별 이미지 (numpy array 또는 PNG 바이트), 전처리된 이미지 바이트 |

순수 Python보다 빨라야 하는 구간에만 Rust를 쓴다. OCR은 FastAPI의 OCR_V1(Marker, MinerU)에서 하고, 이 라이브러리는 전처리만 한다.

---

## 하지 않는 것

- OCR 모델 추론은 하지 않는다 (FastAPI OCR_V1에서 처리)
- HTTP 서버나 gRPC 서버를 띄우지 않는다 (라이브러리다)
- 채점 로직은 없다 (FastAPI 채점 파이프라인에서 처리)
- 비즈니스 로직, 유저 관리, 결제는 다루지 않는다 (Spring Boot에서 처리)

---

## 공개 API

```python
import lib_rust

# PDF → 페이지별 이미지
images = lib_rust.pdf_to_images(pdf_bytes, dpi=200)

# 이미지 → OCR 입력용 전처리 이미지
preprocessed = lib_rust.preprocess_for_ocr(image_bytes)
```

현재 스켈레톤이라 두 함수 모두 `NotImplementedError`를 던진다. `pdfium-render`와 `image` 크레이트로 구현할 예정이다.

---

## 기술 스택

| 항목 | 버전 / 비고 |
|---|---|
| Language | Rust (stable) |
| Python ABI | 3.12 고정 (abi3-py312) |
| 바인딩 | PyO3 0.22 |
| 빌드 도구 | maturin 1.7+ |
| PDF 래스터화 | pdfium-render 0.8 |
| 이미지 처리 | image 0.25 |

---

## 설치 및 개발

### 개발 환경 (editable install)

```bash
# maturin 설치
pip install maturin

# 현재 Python 환경에 editable install
maturin develop

# Python 에서 확인
python -c "import lib_rust; print(lib_rust.__doc__)"
```

### 릴리즈 wheel 빌드

```bash
# 로컬 플랫폼용 wheel
maturin build --release

# 생성물 확인
ls target/wheels/
```

---

## 지원 플랫폼

| 플랫폼 | 용도 | 상태 |
|---|---|---|
| macOS arm64 (Apple Silicon) | 개발 환경 | 지원 |
| Linux x86_64 (manylinux2014) | 배포 환경 | 지원 |
| macOS x86_64 | 선택 | 가능 |
| Linux arm64 | 선택 | 가능 |

Python 버전은 3.12로 고정한다 (backend-fastapi `pyproject.toml` 기준).

---

## 관련 저장소

| 레포 | 역할 |
|---|---|
| [backend-spring](https://github.com/SW-EZ-Team/backend-spring) | 메인 백엔드 (REST API · 비즈니스 로직 · 인증 · 결제) |
| [backend-fastapi](https://github.com/SW-EZ-Team/backend-fastapi) | AI 추론 · OCR · 채점 (lib-rust를 호출하는 쪽) |
| [infra](https://github.com/SW-EZ-Team/infra) | 로컬 DB 서버 묶음 (PostgreSQL · Redis · Qdrant) |

---

## 관련 문서

- PyO3 경계 설계: [`../Docs/01_아키텍처/lib-rust_PyO3_경계.md`](../Docs/01_아키텍처/lib-rust_PyO3_경계.md)
