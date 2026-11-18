"use client";

import Link from "next/link";
import { useSession } from "@/lib/session";
import styles from "./HeaderMenu.module.css";

/**
 * 헤더 오른쪽 메뉴 (DESIGN 5장 상단 헤더). 비회원은 로그인, 회원은 스터디 만들기.
 * 알림·내 스터디·내 정보는 그 화면을 만드는 작업에서 더한다.
 */
export function HeaderMenu() {
  const session = useSession();
  if (session.status === "anonymous") {
    return (
      <Link href="/login" className={styles.item}>
        로그인
      </Link>
    );
  }
  if (session.status === "member" && session.me.onboarded) {
    return (
      <Link href="/studies/new" className={styles.item}>
        스터디 만들기
      </Link>
    );
  }
  return null;
}
