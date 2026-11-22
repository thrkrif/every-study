"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "@/lib/api/me";
import { useSession } from "@/lib/session";
import styles from "./HeaderMenu.module.css";

/**
 * 헤더 오른쪽 메뉴 (DESIGN 5장 상단 헤더). 비회원은 로그인, 회원은 스터디 만들기와 로그아웃.
 * 알림(T-025)과 내 스터디(T-026)는 그 화면으로 가는 링크다. 내 정보는 그 화면을 만드는 작업에서 더한다.
 */
export function HeaderMenu() {
  const session = useSession();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [leaving, setLeaving] = useState(false);

  if (session.status === "anonymous") {
    return (
      <Link href="/login" className={styles.item}>
        로그인
      </Link>
    );
  }
  if (session.status !== "member") return null;

  const signOut = async () => {
    setLeaving(true);
    try {
      await logout();
    } finally {
      // 쿠키가 지워졌으니 로그인 상태와 회원별 응답(내 상태 등)을 처음부터 다시 받는다
      await queryClient.resetQueries();
      setLeaving(false);
      router.push("/");
    }
  };

  return (
    <>
      {session.me.onboarded && (
        <Link href="/studies/new" className={styles.item}>
          스터디 만들기
        </Link>
      )}
      <Link href="/me/studies" className={styles.item}>
        내 스터디
      </Link>
      <Link href="/notifications" className={styles.item}>
        알림
      </Link>
      <button
        type="button"
        className={`${styles.item} ${styles.sub}`}
        onClick={() => void signOut()}
        disabled={leaving}
        aria-busy={leaving || undefined}
      >
        {leaving ? "로그아웃 중" : "로그아웃"}
      </button>
    </>
  );
}
