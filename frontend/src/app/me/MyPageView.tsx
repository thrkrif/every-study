"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import {
  fetchMyAttendance,
  fetchPublicProfile,
  type AttendanceRecord,
} from "@/lib/api/profile";
import { useMetaOptions, useSession } from "@/lib/session";
import {
  codeNames,
  formatClockOf,
  formatDay,
  nicknameOf,
  verdictLabel,
} from "@/lib/studyFormat";
import { AppealForm } from "./AppealForm";
import { ProfileMetrics } from "../users/ProfileMetrics";
import styles from "./MyPageView.module.css";

/**
 * 마이페이지 (PRD 4.10, 화면 10). 내 지표와 지난 출석 기록을 보여 주고, 내 스터디·알림으로 가는 길을 둔다.
 * 지표는 남이 보는 공개 프로필과 같은 응답이라 내가 보는 것과 남이 보는 것이 어긋나지 않는다.
 */
export function MyPageView() {
  const session = useSession();
  const meta = useMetaOptions();
  const member = session.status === "member";
  const myId = member ? session.me.id : undefined;

  const profile = useQuery({
    queryKey: ["public-profile", myId],
    queryFn: ({ signal }) => fetchPublicProfile(myId as number, signal),
    enabled: member,
  });
  const records = useInfiniteQuery({
    queryKey: ["my-attendance"],
    queryFn: ({ pageParam, signal }) => fetchMyAttendance(pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: member,
  });

  if (session.status === "loading") {
    return (
      <main className={styles.page}>
        <p className={styles.note}>불러오는 중…</p>
      </main>
    );
  }
  if (session.status === "anonymous") {
    return (
      <main className={styles.page}>
        <div className={styles.empty}>
          <p>로그인하면 내 지표와 출석 기록을 볼 수 있어요.</p>
          <Link
            href="/login"
            className={buttonStyles.button}
            data-variant="primary"
          >
            로그인
          </Link>
        </div>
      </main>
    );
  }
  if (session.status === "error") {
    return (
      <main className={styles.page}>
        <div className={styles.empty} role="alert">
          <p>내 정보를 불러오지 못했어요.</p>
        </div>
      </main>
    );
  }

  const me = session.me;
  const jobs = meta.data ? codeNames(meta.data.jobs) : undefined;
  const regions = meta.data ? codeNames(meta.data.regions) : undefined;
  const facts = [
    me.jobCode ? (jobs?.get(me.jobCode) ?? me.jobCode) : null,
    me.districtCode ? (regions?.get(me.districtCode) ?? me.districtCode) : null,
  ].filter((fact): fact is string => fact !== null);
  const items = records.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>{nicknameOf(me.nickname)}</h1>
        {facts.length > 0 && (
          <p className={styles.facts}>{facts.join(" · ")}</p>
        )}
      </header>

      {profile.isPending && <p className={styles.note}>지표 불러오는 중…</p>}
      {profile.isError && (
        <div className={styles.empty} role="alert">
          <p>지표를 불러오지 못했어요.</p>
          <Button onClick={() => void profile.refetch()}>다시 시도</Button>
        </div>
      )}
      {profile.isSuccess && (
        <ProfileMetrics
          attendance={profile.data.attendance}
          hostStats={profile.data.hostStats}
        />
      )}

      <nav className={styles.links} aria-label="바로가기">
        <Link href="/me/studies" className={styles.link}>
          내 스터디
        </Link>
        <Link href="/notifications" className={styles.link}>
          알림
        </Link>
        <Link href="/me/withdraw" className={styles.link}>
          회원 탈퇴
        </Link>
      </nav>

      <section className={styles.section} aria-labelledby="records-heading">
        <h2 id="records-heading" className={styles.sectionTitle}>
          지난 출석 기록
        </h2>

        {records.isPending && <p className={styles.note}>불러오는 중…</p>}
        {records.isError && (
          <div className={styles.empty} role="alert">
            <p>출석 기록을 불러오지 못했어요.</p>
            <Button onClick={() => void records.refetch()}>다시 시도</Button>
          </div>
        )}
        {records.isSuccess && items.length === 0 && (
          <p className={styles.note}>아직 출석 기록이 없어요.</p>
        )}
        {items.length > 0 && (
          <ul className={styles.list}>
            {items.map((record) => (
              <li key={record.eventId}>
                <RecordRow record={record} />
              </li>
            ))}
          </ul>
        )}
        {records.hasNextPage && (
          <div className={styles.more}>
            <Button
              disabled={records.isFetchingNextPage}
              onClick={() => void records.fetchNextPage()}
            >
              {records.isFetchingNextPage ? "불러오는 중…" : "더 보기"}
            </Button>
          </div>
        )}
      </section>
    </main>
  );
}

function RecordRow({ record }: { record: AttendanceRecord }) {
  const [appealing, setAppealing] = useState(false);
  return (
    <div className={styles.recordBox}>
      <Link href={`/studies/${record.studyId}`} className={styles.record}>
        <span className={styles.verdict} data-verdict={record.type}>
          {verdictLabel(record.type)}
        </span>
        <span className={styles.recordMain}>
          <span className={styles.recordTitle}>{record.studyTitle}</span>
          <span className={`${styles.recordWhen} num`}>
            {formatDay(record.occurredAt)}
          </span>
        </span>
      </Link>
      {record.appealable && record.appealDueAt && !appealing && (
        <div className={styles.appealRow}>
          <span className={`${styles.recordWhen} num`}>
            이의 신청 {formatDay(record.appealDueAt)}{" "}
            {formatClockOf(record.appealDueAt)}까지
          </span>
          <Button onClick={() => setAppealing(true)}>이의 신청</Button>
        </div>
      )}
      {record.appealable && appealing && (
        <AppealForm
          eventId={record.eventId}
          onClose={() => setAppealing(false)}
        />
      )}
      {record.appealId !== null && (
        <p className={styles.appealRow}>
          <span className={styles.recordWhen}>이의를 신청했어요</span>
          <Link href="/me/appeals" className={styles.inlineLink}>
            이의 내역
          </Link>
        </p>
      )}
    </div>
  );
}
