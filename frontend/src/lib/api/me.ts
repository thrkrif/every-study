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

export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}
