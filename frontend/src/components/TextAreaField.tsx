import { useId } from "react";
import styles from "./TextField.module.css";

/** 여러 줄 입력칸. 규칙은 TextField 와 같다 (DESIGN 5장 입력). */
export function TextAreaField({
  label,
  value,
  onChange,
  hint,
  error,
  maxLength,
  rows = 6,
  placeholder,
  optional = false,
  disabled = false,
}: {
  label: string;
  /** 비워도 되는 칸이면 이름 옆에 "(선택)"을 붙인다 */
  optional?: boolean;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  error?: string;
  maxLength?: number;
  rows?: number;
  placeholder?: string;
  disabled?: boolean;
}) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? hint;
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {optional && <span className={styles.optional}> (선택)</span>}
      </label>
      <textarea
        id={id}
        value={value}
        rows={rows}
        maxLength={maxLength}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        className={`${styles.input} ${styles.multiline}`}
      />
      <div className={styles.below}>
        {message && (
          <p
            id={messageId}
            className={error ? styles.error : styles.hint}
            role={error ? "alert" : undefined}
          >
            {error && <span aria-hidden="true">! </span>}
            {message}
          </p>
        )}
        {maxLength !== undefined && (
          <span className={`${styles.count} num`} aria-hidden="true">
            {value.length}/{maxLength}
          </span>
        )}
      </div>
    </div>
  );
}
