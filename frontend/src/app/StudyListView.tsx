"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import buttonStyles from "@/components/Button.module.css";
import { Button } from "@/components/Button";
import { CheckChips } from "@/components/CheckChips";
import { ChoiceChips } from "@/components/ChoiceChips";
import { StudyCard } from "@/components/StudyCard";
import { districtsOf } from "@/lib/api/meta";
import {
  fetchStudies,
  type StudyFilters,
  type StudySort,
} from "@/lib/api/studies";
import { useMetaOptions, useSession } from "@/lib/session";
import { codeNames, regionChoices } from "@/lib/studyFormat";
import styles from "./StudyListView.module.css";

const ALL = "";
const MY_REGION = "MY_REGION";
const MY_JOB = "myJob";
const INCLUDE_CLOSED = "includeClosed";

const SORTS = [
  { value: "latest", label: "최신순" },
  { value: "deadline", label: "마감임박순" },
] as const;

type ListViewProps = {
  /** 지금 시각. 테스트에서 바꿔 끼운다 */
  now?: () => Date;
};

/**
 * 스터디 목록 (PRD 4.3, DESIGN 6장). 비회원도 본다.
 * 휴대폰은 "필터" 버튼으로 조건을 펼치고, 태블릿은 목록 위에, PC 는 왼쪽 열에 늘 펼쳐 둔다.
 * 조건이 바뀌면 쿼리 키가 바뀌어 첫 페이지부터 다시 부른다.
 */
export function StudyListView({ now = () => new Date() }: ListViewProps) {
  const session = useSession();
  const meta = useMetaOptions();
  const me =
    session.status === "member" && session.me.onboarded
      ? session.me
      : undefined;

  const [region, setRegion] = useState(ALL);
  const [topic, setTopic] = useState(ALL);
  const [job, setJob] = useState(ALL);
  const [toggles, setToggles] = useState<string[]>([]);
  const [sort, setSort] = useState<StudySort>("latest");
  const [filtersOpen, setFiltersOpen] = useState(false);

  // "내 지역"은 회원의 활동 지역(시·도) 그대로다 (PRD 4.3)
  const myRegion = me?.districtCode ?? undefined;

  const filters: StudyFilters = {
    regionCode: region === MY_REGION ? myRegion : region || undefined,
    topicCode: topic || undefined,
    jobCode: job || undefined,
    // 내 직군 필터는 온보딩을 마친 회원만 쓸 수 있다 (API 4장)
    myJob: me !== undefined && toggles.includes(MY_JOB),
    includeClosed: toggles.includes(INCLUDE_CLOSED),
    sort,
  };
  const activeCount =
    [region, topic, job].filter(Boolean).length +
    (filters.myJob ? 1 : 0) +
    (filters.includeClosed ? 1 : 0);

  const studies = useInfiniteQuery({
    queryKey: ["studies", filters],
    queryFn: ({ pageParam, signal }) =>
      fetchStudies(filters, pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 로그인 상태를 알기 전에 내 직군 조건 없이 부르는 일을 막는다
    enabled: session.status !== "loading",
  });

  const names = useMemo(
    () =>
      meta.data
        ? codeNames([
            ...meta.data.topics,
            ...meta.data.jobs,
            ...meta.data.regions,
          ])
        : new Map<string, string>(),
    [meta.data],
  );
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

  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = studies;
  useEffect(() => {
    const target = sentinel.current;
    if (!target || !hasNextPage || typeof IntersectionObserver === "undefined")
      return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !isFetchingNextPage)
        void fetchNextPage();
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const clearFilters = () => {
    setRegion(ALL);
    setTopic(ALL);
    setJob(ALL);
    setToggles([]);
  };

  const items = studies.data?.pages.flatMap((page) => page.items) ?? [];
  const current = now();

  return (
    <main className={styles.page}>
      <div className={styles.head}>
        <h1 className={styles.title}>스터디 찾기</h1>
        <Button
          className={styles.filterToggle}
          aria-expanded={filtersOpen}
          aria-controls="study-filters"
          onClick={() => setFiltersOpen((open) => !open)}
        >
          필터{activeCount > 0 ? ` ${activeCount}` : ""}
        </Button>
      </div>

      <div className={styles.layout}>
        <aside
          id="study-filters"
          className={styles.filters}
          data-open={filtersOpen}
          aria-label="필터"
        >
          {meta.data && (
            <>
              <ChoiceChips
                legend="지역"
                choices={[
                  { value: ALL, label: "전체" },
                  ...(myRegion ? [{ value: MY_REGION, label: "내 지역" }] : []),
                  ...regionChoices(meta.data.regions).map((choice) => ({
                    value: choice.code,
                    label: choice.label,
                  })),
                ]}
                value={region}
                onChange={setRegion}
              />
              <ChoiceChips
                legend="주제"
                choices={[
                  { value: ALL, label: "전체" },
                  ...meta.data.topics.map((option) => ({
                    value: option.code,
                    label: option.name,
                  })),
                ]}
                value={topic}
                onChange={setTopic}
              />
              <ChoiceChips
                legend="희망 직군"
                choices={[
                  { value: ALL, label: "전체" },
                  ...meta.data.jobs.map((option) => ({
                    value: option.code,
                    label: option.name,
                  })),
                ]}
                value={job}
                onChange={setJob}
              />
              <CheckChips
                legend="더 보기"
                choices={[
                  ...(me
                    ? [{ value: MY_JOB, label: "내가 신청할 수 있는 스터디" }]
                    : []),
                  { value: INCLUDE_CLOSED, label: "마감 포함" },
                ]}
                values={toggles}
                onChange={setToggles}
              />
              {activeCount > 0 && (
                <Button variant="text" onClick={clearFilters}>
                  조건 지우기
                </Button>
              )}
            </>
          )}
        </aside>

        <section className={styles.results} aria-labelledby="study-results">
          <h2 id="study-results" className="visually-hidden">
            스터디 목록
          </h2>
          <ChoiceChips
            legend="정렬"
            hideLegend
            choices={SORTS}
            value={sort}
            onChange={(value) => setSort(value as StudySort)}
          />

          {studies.isPending && <p className={styles.note}>불러오는 중…</p>}

          {studies.isError && (
            <div className={styles.empty} role="alert">
              <p>목록을 불러오지 못했어요.</p>
              <Button onClick={() => void studies.refetch()}>다시 시도</Button>
            </div>
          )}

          {studies.isSuccess && items.length === 0 && (
            <div className={styles.empty}>
              <p>
                {activeCount > 0
                  ? "조건에 맞는 모집 중인 스터디가 없어요."
                  : "아직 모집 중인 스터디가 없어요."}
              </p>
              {activeCount > 0 ? (
                <Button onClick={clearFilters}>조건 지우기</Button>
              ) : (
                <Link
                  href={me ? "/studies/new" : "/login"}
                  className={buttonStyles.button}
                  data-variant="primary"
                >
                  {me ? "스터디 만들기" : "로그인하고 스터디 만들기"}
                </Link>
              )}
            </div>
          )}

          {items.length > 0 && (
            <ul className={styles.list}>
              {items.map((study) => (
                <li key={study.id}>
                  <StudyCard
                    study={study}
                    now={current}
                    names={names}
                    districtLabel={(code) =>
                      districtLabels.get(code) ?? names.get(code) ?? code
                    }
                  />
                </li>
              ))}
            </ul>
          )}

          {hasNextPage && (
            <div ref={sentinel} className={styles.more}>
              <Button
                onClick={() => void fetchNextPage()}
                pending={isFetchingNextPage}
                pendingLabel="불러오는 중"
              >
                더 보기
              </Button>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
