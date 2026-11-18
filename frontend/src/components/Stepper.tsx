import { useId } from "react";
import styles from "./Stepper.module.css";

/** 숫자를 단위만큼 줄이고 늘리는 입력 (정원, 진행 시간). 범위 밖으로는 버튼이 막힌다. */
export function Stepper({
  label,
  value,
  min,
  max,
  step = 1,
  format = String,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  format?: (value: number) => string;
  onChange: (value: number) => void;
}) {
  const id = useId();
  return (
    <div className={styles.field}>
      <span id={id} className={styles.label}>
        {label}
      </span>
      <div className={styles.row} role="group" aria-labelledby={id}>
        <button
          type="button"
          className={styles.button}
          onClick={() => onChange(Math.max(min, value - step))}
          disabled={value - step < min}
          aria-label={`${label} 줄이기`}
        >
          −
        </button>
        <output className={`${styles.value} num`} aria-live="polite">
          {format(value)}
        </output>
        <button
          type="button"
          className={styles.button}
          onClick={() => onChange(Math.min(max, value + step))}
          disabled={value + step > max}
          aria-label={`${label} 늘리기`}
        >
          +
        </button>
      </div>
    </div>
  );
}
