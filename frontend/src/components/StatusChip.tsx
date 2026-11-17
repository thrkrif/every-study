import styles from "./StatusChip.module.css";

export type StudyPhase =
  "RECRUITING" | "CLOSED" | "WAITLIST" | "IN_PROGRESS" | "ENDED";

const LABEL: Record<StudyPhase, string> = {
  RECRUITING: "모집 중",
  CLOSED: "마감",
  WAITLIST: "대기 가능",
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
};

/** 상태 칩 (DESIGN 5장). 상태 이름을 항상 글로 쓰고 색은 보조다 */
export function StatusChip({ phase }: { phase: StudyPhase }) {
  return (
    <span className={styles.chip} data-phase={phase}>
      {LABEL[phase]}
    </span>
  );
}
