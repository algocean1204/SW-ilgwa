// OCR 전처리용 PyO3 Rust 라이브러리 스켈레톤
//
// FastAPI 서버에서 `import lib_rust`로 불러 쓰는 Python 확장 모듈이다.
// PDF 래스터화와 이미지 전처리는 아직 구현하지 않았다.
// 지금은 크레이트가 컴파일되고 모듈이 로드되는 것까지만 한다.

use pyo3::exceptions::PyNotImplementedError;
use pyo3::prelude::*;
use pyo3::types::PyBytes;

/// PDF 바이트 → 페이지별 PNG 바이트 리스트
///
/// 공개 API 스켈레톤이다.
/// 구현할 때는 `pdfium-render`로 래스터화하고 `image` 크레이트로 PNG 인코딩한다.
#[pyfunction]
#[pyo3(signature = (pdf_bytes, dpi = 200))]
fn pdf_to_images<'py>(
    _py: Python<'py>,
    pdf_bytes: &[u8],
    dpi: u32,
) -> PyResult<Vec<Py<PyBytes>>> {
    // 아직 구현 전이라 NotImplementedError를 던진다
    let _ = (pdf_bytes, dpi);
    Err(PyNotImplementedError::new_err(
        "pdf_to_images 는 아직 구현되지 않았다. 후속 스프린트에서 pdfium-render 로 채운다.",
    ))
}

/// 단일 이미지 바이트 → OCR 입력 규격으로 전처리된 PNG 바이트
///
/// 공개 API 스켈레톤이다.
/// 구현할 때는 `image` 크레이트로 디코드 → 리사이즈·정규화 → PNG 인코딩한다.
#[pyfunction]
fn preprocess_for_ocr<'py>(
    _py: Python<'py>,
    image_bytes: &[u8],
) -> PyResult<Py<PyBytes>> {
    let _ = image_bytes;
    Err(PyNotImplementedError::new_err(
        "preprocess_for_ocr 는 아직 구현되지 않았다. 후속 스프린트에서 image 크레이트로 채운다.",
    ))
}

/// `import lib_rust` 때 호출되는 PyO3 모듈 진입점
#[pymodule]
fn lib_rust(m: &Bound<'_, PyModule>) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(pdf_to_images, m)?)?;
    m.add_function(wrap_pyfunction!(preprocess_for_ocr, m)?)?;
    Ok(())
}
