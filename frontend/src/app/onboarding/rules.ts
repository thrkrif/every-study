// 온보딩 입력 규칙. 원본은 API 3장(PUT /me/profile)과 백엔드 ProfileRequest 이고, 화면은 같은 규칙으로 미리 막는다.

export const NICKNAME_MAX = 20;
const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9_]{2,20}$/;

/** 닉네임이 쓸 수 없는 값이면 이유를, 쓸 수 있으면 undefined 를 돌려준다. 앞뒤 공백은 서버처럼 잘라서 본다. */
export function nicknameProblem(raw: string): string | undefined {
  const value = raw.trim();
  if (value.length === 0) return "닉네임을 입력해 주세요.";
  if (value.length < 2) return "2자 이상 입력해 주세요.";
  if (value.length > NICKNAME_MAX) return `${NICKNAME_MAX}자까지 쓸 수 있어요.`;
  if (!NICKNAME_PATTERN.test(value))
    return "한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.";
  return undefined;
}

export type OnboardingStep = "nickname" | "job" | "region";
export const STEPS: readonly OnboardingStep[] = ["nickname", "job", "region"];

/** 서버 필드 오류를 어느 단계에서 보여 줄지 */
export function stepOfField(field: string): OnboardingStep | undefined {
  if (field === "nickname") return "nickname";
  if (field === "jobCode") return "job";
  if (field === "districtCode") return "region";
  return undefined;
}
