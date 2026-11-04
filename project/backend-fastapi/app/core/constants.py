"""모듈 공용 상수."""

import os

# PDF 업로드 상한 1.5GB(1.5 * 2^30 bytes). 대용량 교재 때문에 크게 잡았다.
# MAX_PDF_UPLOAD_SIZE_BYTES 환경변수로 바꿀 수 있다.
MAX_PDF_UPLOAD_SIZE_BYTES: int = int(
    os.getenv("MAX_PDF_UPLOAD_SIZE_BYTES", str(1_610_612_736))
)
