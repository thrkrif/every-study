"use client";

import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/Button";
import { isApiError } from "@/lib/api/errors";
import { fetchPublicProfile } from "@/lib/api/profile";
import { useMetaOptions } from "@/lib/session";
import { codeNames, formatDay, nicknameOf } from "@/lib/studyFormat";
import { ProfileMetrics } from "../ProfileMetrics";
import styles from "./ProfileView.module.css";

/** 공개 프로필 (PRD 4.1, API 3장). 비회원도 본다. 이메일 같은 개인정보는 응답에 없다 */
export function ProfileView({ id }: { id: number }) {
  const meta = useMetaOptions();
  const profile = useQuery({
    queryKey: ["public-profile", id],
    queryFn: ({ signal }) => fetchPublicProfile(id, signal),
    retry: false,
  });

  if (profile.isPending) {
    return (
      <main className={styles.page}>
        <p className={styles.note}>불러오는 중…</p>
      </main>
    );
  }
  if (profile.isError) {
    const missing = isApiError(profile.error, "NOT_FOUND");
    return (
      <main className={styles.page}>
        <div className={styles.empty} role="alert">
          <p>
            {missing
              ? "찾을 수 없는 사용자예요."
              : "프로필을 불러오지 못했어요."}
          </p>
          {!missing && (
            <Button onClick={() => void profile.refetch()}>다시 시도</Button>
          )}
        </div>
      </main>
    );
  }

  const data = profile.data;
  const jobs = meta.data ? codeNames(meta.data.jobs) : undefined;
  const regions = meta.data ? codeNames(meta.data.regions) : undefined;
  const facts = [
    data.jobCode ? (jobs?.get(data.jobCode) ?? data.jobCode) : null,
    data.districtCode
      ? (regions?.get(data.districtCode) ?? data.districtCode)
      : null,
    `${formatDay(data.joinedAt)} 가입`,
  ].filter((fact): fact is string => fact !== null);

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>{nicknameOf(data.nickname)}</h1>
        <p className={styles.facts}>{facts.join(" · ")}</p>
      </header>
      <ProfileMetrics attendance={data.attendance} hostStats={data.hostStats} />
    </main>
  );
}
