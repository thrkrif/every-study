import Link from "next/link";
import type { ReactNode } from "react";
import styles from "./AppHeader.module.css";

/**
 * 모든 화면 위의 헤더 (DESIGN 6장 폭에 따른 배치). 왼쪽 서비스 이름, 오른쪽 메뉴.
 * 메뉴(만들기, 알림, 내 스터디 등)는 그 화면을 만드는 작업에서 `actions` 로 채운다.
 */
export function AppHeader({ actions }: { actions?: ReactNode }) {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link href="/" className={styles.brand}>
          모두의 스터디
        </Link>
        {actions && <nav className={styles.actions}>{actions}</nav>}
      </div>
    </header>
  );
}
