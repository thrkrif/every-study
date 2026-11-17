import { apiFetch } from "./client";

/** `POST /studies` 요청 (API 4장 개설 요청). */
export type CreateStudyInput = {
  title: string;
  description: string;
  topicCode: string;
  districtCode: string;
  /** 직군 대분류. 비우면 직군 무관 */
  targetJobCodes: string[];
  place: { name: string; address: string; latitude: number; longitude: number };
  /** ISO-8601 UTC */
  startAt: string;
  durationMin: number;
  capacity: number;
  minAttendanceRate: number | null;
  allowNewcomer: boolean;
};

export function createStudy(input: CreateStudyInput): Promise<{ id: number }> {
  return apiFetch<{ id: number }>("/studies", { method: "POST", body: input });
}
