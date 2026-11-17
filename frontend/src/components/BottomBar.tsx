import type { ReactNode } from "react";
import styles from "./BottomBar.module.css";

/** 하단 고정 영역 (DESIGN 5장 하단 고정 CTA). 기기 안전 영역만큼 아래 여백을 더한다. */
export function BottomBar({
  children,
  note,
}: {
  children: ReactNode;
  /** 버튼 위에 쓰는 한 줄. 비활성 사유나 오류를 알린다 */
  note?: ReactNode;
}) {
  return (
    <div className={styles.bar}>
      {note && <div className={styles.note}>{note}</div>}
      <div className={styles.actions}>{children}</div>
    </div>
  );
}
