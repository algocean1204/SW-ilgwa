// 출시 범위 플래그. false인 기능은 프로덕션과 개발 환경 모두에서 UI에 보이지 않는다.
// 출시 준비가 끝난 기능만 true로 바꾼다.

/** 관리자 대시보드. API 연동과 권한 확인이 들어가 있다. */
export const ENABLE_ADMIN = true;

/** 강사 레슨 에디터. 전부 하드코딩 목업이라 출시에서 뺐다. */
export const ENABLE_EDITOR = false;

/**
 * 결제 CTA 플래그. false이면 구독/코인 구매 버튼을 "준비 중" 비활성으로 보인다.
 * 플랜 목록(Free/Pro/Premium)과 코인 팩 카드는 계속 보인다.
 *
 * true면 토스페이먼츠 결제가 켜진다. 백엔드는 토스 테스트키를 쓰므로
 * 실제 카드 청구 없이 샌드박스 결제로 동작한다. 라이브 키로 바꾸면 실결제가 된다.
 */
export const ENABLE_PAYMENT = true;
