"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import { ChoiceChips } from "@/components/ChoiceChips";
import { SeatRow } from "@/components/SeatRow";
import { districtsOf } from "@/lib/api/meta";
import {
  fetchMyStudies,
  type MyStudy,
  type MyStudyState,
} from "@/lib/api/myStudies";
import { useMetaOptions, useSession } from "@/lib/session";
import { formatWhen } from "@/lib/studyFormat";
import styles from "./MyStudiesView.module.css";

const ALL = "";

type ViewProps = {
  /** 지금 시각. 테스트에서 바꿔 끼운다 */
  now?: () => Date;
};

/**
 * 내 스터디 목록 (PRD 4.10, API 3장). 다가오는 모임과 지난 모임을 나누고, 주최·참여로 거른다. 주최하는 진행 중인
 * 스터디는 바로 관리 화면으로 갈 수 있다 (T-026).
 */
export function MyStudiesView({ now = () => new Date() }: ViewProps) {
  const session = useSession();
  const meta = useMetaOptions();
  const [state, setState] = useState<MyStudyState>("upcoming");
  const [role, setRole] = useState<string>(ALL);

  const roleFilter = role === ALL ? undefined : (role as "host" | "member");
  const studies = useInfiniteQuery({
    queryKey: ["my-studies", state, roleFilter],
    queryFn: ({ pageParam, signal }) =>
      fetchMyStudies(state, roleFilter, pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: session.status === "member",
  });

  const districtLabels = useMemo(
    () =>
      new Map(
        (meta.data ? districtsOf(meta.data.regions) : []).map((district) => [
          district.code,
          district.label,
        ]),
      ),
    [meta.data],
  );

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
          <p>로그인하면 내 스터디를 볼 수 있어요.</p>
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

  const items = studies.data?.pages.flatMap((page) => page.items) ?? [];
  const current = now();

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>내 스터디</h1>
      <ChoiceChips
        legend="모임"
        hideLegend
        choices={[
          { value: "upcoming", label: "다가오는 모임" },
          { value: "past", label: "지난 모임" },
        ]}
        value={state}
        onChange={(value) => setState(value as MyStudyState)}
      />
      <ChoiceChips
        legend="역할"
        hideLegend
        choices={[
          { value: ALL, label: "전체" },
          { value: "host", label: "주최" },
          { value: "member", label: "참여" },
        ]}
        value={role}
        onChange={setRole}
      />

      {studies.isPending && <p className={styles.note}>불러오는 중…</p>}

      {studies.isError && (
        <div className={styles.empty} role="alert">
          <p>내 스터디를 불러오지 못했어요.</p>
          <Button onClick={() => void studies.refetch()}>다시 시도</Button>
        </div>
      )}

      {studies.isSuccess && items.length === 0 && (
        <div className={styles.empty}>
          <p>
            {state === "upcoming"
              ? "참여 중인 스터디가 없어요."
              : "지난 스터디가 없어요."}
          </p>
          {state === "upcoming" && (
            <Link
              href="/"
              className={buttonStyles.button}
              data-variant="primary"
            >
              스터디 찾아보기
            </Link>
          )}
        </div>
      )}

      {items.length > 0 && (
        <ul className={styles.list}>
          {items.map((study) => (
            <li key={study.id}>
              <MyStudyCard
                study={study}
                now={current}
                districtLabel={
                  districtLabels.get(study.districtCode) ?? study.districtCode
                }
              />
            </li>
          ))}
        </ul>
      )}

      {studies.hasNextPage && (
        <div className={styles.more}>
          <Button
            disabled={studies.isFetchingNextPage}
            onClick={() => void studies.fetchNextPage()}
          >
            {studies.isFetchingNextPage ? "불러오는 중…" : "더 보기"}
          </Button>
        </div>
      )}
    </main>
  );
}

function MyStudyCard({
  study,
  now,
  districtLabel,
}: {
  study: MyStudy;
  now: Date;
  districtLabel: string;
}) {
  const roleText = study.role === "HOST" ? "주최" : "참여";
  const statusText =
    study.status === "CANCELLED"
      ? "취소됨"
      : study.status === "ENDED"
        ? "종료"
        : new Date(study.startAt).getTime() <= now.getTime()
          ? "진행 중"
          : null;
  return (
    <div className={styles.card}>
      <Link href={`/studies/${study.id}`} className={styles.main}>
        <span className={styles.badges}>
          <span className={styles.role} data-role={study.role}>
            {roleText}
          </span>
          {statusText && <span className={styles.status}>{statusText}</span>}
        </span>
        <span className={`${styles.when} num`}>
          {formatWhen(study.startAt, now)}
        </span>
        <span className={styles.name}>{study.title}</span>
        <span className={styles.meta}>{districtLabel}</span>
        <SeatRow capacity={study.capacity} memberCount={study.memberCount} />
      </Link>
      {study.role === "HOST" && study.status === "ACTIVE" && (
        <Link
          href={`/studies/${study.id}/manage`}
          className={`${buttonStyles.button} ${styles.manage}`}
          data-variant="secondary"
        >
          관리
        </Link>
      )}
    </div>
  );
}
