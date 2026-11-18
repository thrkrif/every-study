import Link from "next/link";
import type { StudySummary } from "@/lib/api/studies";
import {
  entryCriteriaTexts,
  formatWhen,
  hostFact,
  nicknameOf,
  phaseOf,
} from "@/lib/studyFormat";
import { formatDuration } from "@/app/studies/new/rules";
import { SeatRow } from "./SeatRow";
import { StatusChip } from "./StatusChip";
import styles from "./StudyCard.module.css";

type StudyCardProps = {
  study: StudySummary;
  now: Date;
  /** 코드 → 화면 이름 (지역·주제·직군) */
  names: ReadonlyMap<string, string>;
  /** 구 코드 → "수원시 장안구" 같은 표시 이름 */
  districtLabel: (code: string) => string;
};

/**
 * 목록 카드 (DESIGN 6장, PRD 4.3 카드 표시). 카드 전체가 상세로 가는 링크다.
 * 시각을 가장 크게 쓰고, 입장 기준 배지는 오른쪽 위에 글로 둔다.
 */
export function StudyCard({
  study,
  now,
  names,
  districtLabel,
}: StudyCardProps) {
  const jobs = study.targetJobCodes.map((code) => names.get(code) ?? code);
  return (
    <Link href={`/studies/${study.id}`} className={styles.card}>
      <div className={styles.top}>
        <StatusChip phase={phaseOf(study, now)} />
        <span className={styles.criteria}>
          {entryCriteriaTexts(study.entryCriteria).join(" · ")}
        </span>
      </div>
      <p className={`${styles.when} num`}>{formatWhen(study.startAt, now)}</p>
      <h2 className={styles.title}>{study.title}</h2>
      <p className={styles.meta}>
        {districtLabel(study.districtCode)} ·{" "}
        {names.get(study.topicCode) ?? study.topicCode} ·{" "}
        {formatDuration(study.durationMin)}
      </p>
      <SeatRow capacity={study.capacity} memberCount={study.memberCount} />
      <ul className={styles.tags} aria-label="희망 직군">
        {jobs.length === 0 ? (
          <li className={styles.tag}>직군 무관</li>
        ) : (
          jobs.map((job) => (
            <li key={job} className={styles.tag}>
              {job}
            </li>
          ))
        )}
      </ul>
      <p className={styles.host}>
        주최 {nicknameOf(study.host.nickname)} · {hostFact(study.host.stats)}
      </p>
    </Link>
  );
}
