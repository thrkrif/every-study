import styles from "./ProgressSteps.module.css";

/** 단계 흐름 상단의 진행 막대 (DESIGN 5장). 단계가 있는 흐름에서만 쓴다. */
export function ProgressSteps({
  current,
  total,
}: {
  /** 1부터 */
  current: number;
  total: number;
}) {
  return (
    <div className={styles.wrap}>
      <div
        className={styles.track}
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={total}
        aria-valuenow={current}
        aria-label={`${total}단계 중 ${current}단계`}
      >
        <div
          className={styles.fill}
          style={{ width: `${(current / total) * 100}%` }}
        />
      </div>
      <span className={`${styles.count} num`} aria-hidden="true">
        {current}/{total}
      </span>
    </div>
  );
}
