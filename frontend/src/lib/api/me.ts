import { apiFetch } from "./client";

/** `GET /me`, `PUT /me/profile` 응답 (API 3장). 온보딩 전에는 세 값이 null 이다. */
export type Me = {
  id: number;
  nickname: string | null;
  jobCode: string | null;
  districtCode: string | null;
  role: "USER" | "ADMIN";
  onboarded: boolean;
  joinedAt: string;
};

export type ProfileInput = {
  nickname: string;
  jobCode: string;
  districtCode: string;
};

export function fetchMe(signal?: AbortSignal): Promise<Me> {
  return apiFetch<Me>("/me", { signal });
}

export function updateProfile(input: ProfileInput): Promise<Me> {
  return apiFetch<Me>("/me/profile", { method: "PUT", body: input });
}

/** 회원 탈퇴 (PRD 4.1, API 3장). 서버가 로그인 쿠키를 지운다. 되돌릴 수 없다 */
export function withdraw(): Promise<void> {
  return apiFetch<void>("/me", { method: "DELETE" });
}

export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}
