import type { ButtonHTMLAttributes } from "react";
import styles from "./Button.module.css";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /** primary(채움)는 한 화면에 하나만 쓴다 (DESIGN 5장) */
  variant?: "primary" | "secondary" | "text";
  /** 요청 중이면 버튼을 막고 글자를 바꾼다. 스피너는 쓰지 않는다 */
  pendingLabel?: string;
  pending?: boolean;
};

export function Button({
  variant = "secondary",
  pending = false,
  pendingLabel,
  disabled,
  children,
  className,
  type = "button",
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      type={type}
      className={[styles.button, className].filter(Boolean).join(" ")}
      data-variant={variant}
      disabled={disabled || pending}
      aria-busy={pending || undefined}
    >
      {pending && pendingLabel ? pendingLabel : children}
    </button>
  );
}
