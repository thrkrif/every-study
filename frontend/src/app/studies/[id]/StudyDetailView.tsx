"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, type ReactNode } from "react";
import buttonStyles from "@/components/Button.module.css";
import { PlaceMap, kakaoMapLink } from "@/components/PlaceMap";
import { SeatRow } from "@/components/SeatRow";
import { StatusChip, type StudyPhase } from "@/components/StatusChip";
import { isApiError } from "@/lib/api/errors";
import { districtsOf } from "@/lib/api/meta";
import { fetchStudy, type StudyDetail } from "@/lib/api/studies";
import { useMetaOptions, useSession } from "@/lib/session";
import {
  codeNames,
  entryCriteriaTexts,
  formatClockOf,
  formatLongWhen,
  hostFact,
  nicknameOf,
  phaseOf,
  trustText,
} from "@/lib/studyFormat";
import { formatDuration } from "../new/rules";
import styles from "./StudyDetailView.module.css";

type DetailViewProps = {
  /** 주소의 id. 숫자가 아니면 없는 스터디로 본다 */
  id: string;
  /** 지금 시각. 테스트에서 바꿔 끼운다 */
  now?: () => Date;
};

/**
 * 스터디 상세 (PRD 4.3, DESIGN 6장 상세). 비회원도 본다.
 * 휴대폰은 한 열에 하단 고정 안내, PC 는 왼쪽 본문 + 오른쪽 따라다니는 카드(일시·좌석 줄·안내).
 * 참여·대기·나가기 버튼은 PLAN T-024 에서 이 자리에 더한다.
 */
export function StudyDetailView({
  id,
  now = () => new Date(),
}: DetailViewProps) {
  const studyId = /^\d+$/.test(id) ? Number(id) : undefined;
  const session = useSession();
  const meta = useMetaOptions();
  const study = useQuery({
    queryKey: ["study", studyId],
    queryFn: ({ signal }) => fetchStudy(studyId!, signal),
    enabled: studyId !== undefined,
    retry: (count, error) => !isApiError(error, "NOT_FOUND") && count < 2,
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
  const districtLabel = (code: string) =>
    (meta.data ? districtsOf(meta.data.regions) : []).find(
      (district) => district.code === code,
    )?.label ??
    names.get(code) ??
    code;

  if (studyId === undefined || isApiError(study.error, "NOT_FOUND")) {
    return (
      <Message text="스터디를 찾을 수 없어요. 주소를 확인하거나 목록에서 다시 골라 주세요." />
    );
  }
  if (study.isError) {
    return (
      <Message text="스터디를 불러오지 못했어요. 잠시 뒤 새로고침해 주세요." />
    );
  }
  if (!study.data) {
    return (
      <main className={styles.page}>
        <p className={styles.sub}>불러오는 중…</p>
      </main>
    );
  }

  const data = study.data;
  const current = now();
  const phase = phaseOf(data, current);
  const jobs = data.targetJobCodes.map((code) => names.get(code) ?? code);
  const guidance = guidanceOf(data, phase, session.status === "anonymous");

  return (
    <main className={styles.page}>
      <div className={styles.layout}>
        <article className={styles.body}>
          <header className={styles.top}>
            <div className={styles.chips}>
              <StatusChip phase={phase} />
              <span className={styles.sub}>
                {names.get(data.topicCode) ?? data.topicCode} ·{" "}
                {districtLabel(data.districtCode)}
              </span>
            </div>
            <h1 className={styles.title}>{data.title}</h1>
            <p className={`${styles.when} num`}>
              {formatLongWhen(data.startAt)}
            </p>
            <p className={styles.sub}>
              {formatDuration(data.durationMin)} 진행 · 신청 마감{" "}
              {formatClockOf(data.checkinOpensAt)}
            </p>
            <div className={styles.seats}>
              <SeatRow
                capacity={data.capacity}
                memberCount={data.memberCount}
              />
            </div>
          </header>

          {data.description && (
            <Section title="소개">
              <p className={styles.description}>{data.description}</p>
            </Section>
          )}

          <Section title="참여 조건">
            <ul className={styles.conditions}>
              {entryCriteriaTexts(data.entryCriteria).map((text) => (
                <li key={text} className={styles.condition}>
                  {text === "누구나" ? "입장 기준 없음" : text}
                </li>
              ))}
              {jobs.length === 0 ? (
                <li className={styles.condition}>직군 무관</li>
              ) : (
                jobs.map((job) => (
                  <li key={job} className={styles.condition}>
                    {job}
                  </li>
                ))
              )}
            </ul>
          </Section>

          <Section title="장소">
            <p className={styles.placeName}>{data.place.name}</p>
            <p className={styles.sub}>{data.place.address}</p>
            <PlaceMap place={data.place} />
            <a
              className={styles.link}
              href={kakaoMapLink(data.place)}
              target="_blank"
              rel="noopener noreferrer"
            >
              카카오맵에서 보기
            </a>
          </Section>

          <Section title={`참여자 ${data.memberCount}명`}>
            <ul className={styles.members}>
              {data.members.map((member) => (
                <li key={member.userId} className={styles.member}>
                  <span className={styles.nickname}>
                    {nicknameOf(member.nickname)}
                    {member.userId === data.host.userId && (
                      <span className={styles.hostMark}>주최</span>
                    )}
                  </span>
                  <span className={styles.sub}>{trustText(member.trust)}</span>
                </li>
              ))}
            </ul>
            <p className={styles.fact}>{hostFact(data.host.stats)}</p>
          </Section>
        </article>

        <aside className={styles.panel} aria-label="참여">
          <div className={styles.panelSummary}>
            <p className={`${styles.panelWhen} num`}>
              {formatLongWhen(data.startAt)}
            </p>
            <SeatRow capacity={data.capacity} memberCount={data.memberCount} />
          </div>
          {guidance}
        </aside>
      </div>
    </main>
  );
}

/** 참여 영역의 안내. 버튼이 생기기 전(T-024)까지는 비회원 로그인 안내와 상태 설명만 둔다 */
function guidanceOf(
  study: StudyDetail,
  phase: StudyPhase,
  anonymous: boolean,
): ReactNode {
  if (phase === "ENDED") {
    return (
      <p className={styles.guide}>
        {study.status === "CANCELLED"
          ? "열리지 않은 스터디예요."
          : "끝난 스터디예요."}
      </p>
    );
  }
  if (phase === "IN_PROGRESS") {
    return <p className={styles.guide}>지금 진행 중인 스터디예요.</p>;
  }
  if (anonymous) {
    return (
      <Link
        href="/login"
        className={buttonStyles.button}
        data-variant="primary"
      >
        로그인하고 참여하기
      </Link>
    );
  }
  if (study.myState === "HOST")
    return <p className={styles.guide}>내가 주최하는 스터디예요.</p>;
  if (study.myState === "MEMBER")
    return <p className={styles.guide}>참여 중인 스터디예요.</p>;
  if (phase === "CLOSED") {
    return (
      <p className={styles.guide}>
        {study.memberCount >= study.capacity
          ? "정원이 찼어요."
          : "신청이 마감됐어요."}
      </p>
    );
  }
  const left = study.capacity - study.memberCount;
  return <p className={styles.guide}>{left}자리 남았어요.</p>;
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>{title}</h2>
      {children}
    </section>
  );
}

function Message({ text }: { text: string }) {
  return (
    <main className={styles.page}>
      <div className={styles.message}>
        <p>{text}</p>
        <Link href="/" className={buttonStyles.button} data-variant="secondary">
          목록으로
        </Link>
      </div>
    </main>
  );
}
