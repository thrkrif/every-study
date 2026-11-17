import { useId, type InputHTMLAttributes } from "react";
import styles from "./TextField.module.css";

type TextFieldProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
> & {
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** 입력 규칙 안내. 오류가 있으면 오류 문구가 대신 보인다 */
  hint?: string;
  error?: string;
  /** 길이 제한을 주면 오른쪽 아래에 글자 수를 센다 */
  maxLength?: number;
};

/** 입력칸 (DESIGN 5장). 라벨은 위에 항상 보이고, 오류는 칸 아래에 글로 쓴다. */
export function TextField({
  label,
  value,
  onChange,
  hint,
  error,
  maxLength,
  ...rest
}: TextFieldProps) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? hint;
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        {...rest}
        id={id}
        value={value}
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        className={styles.input}
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
