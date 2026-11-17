import styles from "./AttendanceRow.module.css";

export type AttendanceMark = "PRESENT" | "LATE" | "NO_SHOW";

type AttendanceRowProps = {
  /** 최근 기록. 오래된 것부터. 비어 있으면 "신규"로 표시한다 */
  records: readonly AttendanceMark[];
  /** 출석률(0~100). 기록이 없으면 쓰지 않는다 */
  rate?: number;
};

const SHAPE: Record<AttendanceMark, string> = {
  PRESENT: "■",
  LATE: "▲",
  NO_SHOW: "✕",
};

/**
 * 출석 기록 줄 (DESIGN 3장). 신뢰를 점수 하나가 아니라 기록으로 보여 준다.
 * 모양과 색을 함께 쓰고, 화면 낭독기에는 숫자를 글로 준다.
 */
export function AttendanceRow({ records, rate }: AttendanceRowProps) {
  if (records.length === 0) {
    return <span className={styles.newcomer}>신규</span>;
  }
  const count = (mark: AttendanceMark) =>
    records.filter((record) => record === mark).length;
  const label =
    `최근 ${records.length}회 중 출석 ${count("PRESENT")}회, ` +
    `지각 ${count("LATE")}회, 노쇼 ${count("NO_SHOW")}회`;
  return (
    <span className={styles.row}>
      {rate !== undefined && (
        <span className={`${styles.rate} num`}>출석률 {rate}%</span>
      )}
      <span className={styles.marks} role="img" aria-label={label}>
        {records.map((record, index) => (
          <span key={index} className={styles.mark} data-mark={record}>
            {SHAPE[record]}
          </span>
        ))}
      </span>
    </span>
  );
}
