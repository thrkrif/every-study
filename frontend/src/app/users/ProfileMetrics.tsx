import type { HostStats, Trust } from "@/lib/api/studies";
import { hostFact, trustSummaryText } from "@/lib/studyFormat";
import styles from "./ProfileMetrics.module.css";

type ProfileMetricsProps = {
  attendance: Trust;
  hostStats: HostStats;
};

/**
 * 출석 지표와 주최 지표 (PRD 4.7·4.13D). 공개 프로필과 마이페이지가 같은 문구를 쓴다.
 * 신규는 숫자 대신 배지로, 주최 지표는 5회 미만이면 건수만 보인다 (문구는 `studyFormat` 이 정한다).
 */
export function ProfileMetrics({ attendance, hostStats }: ProfileMetricsProps) {
  const newcomer = attendance.isNewcomer || attendance.attendanceRate === null;
  return (
    <section className={styles.metrics} aria-label="지표">
      <div className={styles.block}>
        <h2 className={styles.heading}>출석</h2>
        {newcomer ? (
          <p className={styles.line}>
            <span className={styles.badge}>신규</span>
            <span className={styles.sub}>
              아직 기록이 쌓이는 중이에요 (최근 {attendance.windowCount}회)
            </span>
          </p>
        ) : (
          <p className={`${styles.line} num`}>{trustSummaryText(attendance)}</p>
        )}
      </div>
      <div className={styles.block}>
        <h2 className={styles.heading}>주최</h2>
        <p className={`${styles.line} num`}>{hostFact(hostStats)}</p>
      </div>
    </section>
  );
}
