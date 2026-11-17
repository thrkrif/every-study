"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { SocialLoginButtons } from "@/components/SocialLoginButtons";
import { useSession } from "@/lib/session";
import styles from "./LoginView.module.css";

export const LOGIN_ERROR_MESSAGES: Record<string, string> = {
  social_login_failed:
    "로그인을 마치지 못했어요. 동의 화면에서 취소했다면 다시 시도해 주세요.",
};

/**
 * 랜딩 겸 로그인 화면 (PRD 5장 화면 1, DESIGN 6장 로그인).
 * 이미 로그인했으면 온보딩 여부에 따라 온보딩 또는 첫 화면으로 보낸다.
 */
export function LoginView({ error }: { error?: string }) {
  const session = useSession();
  const router = useRouter();

  useEffect(() => {
    if (session.status !== "member") return;
    if (!session.me.onboarded) router.replace("/onboarding");
  }, [session, router]);

  const errorMessage = error ? LOGIN_ERROR_MESSAGES[error] : undefined;

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <div className={styles.intro}>
          <h1 className={styles.title}>약속 지키는 사람들의 스터디</h1>
          <p className={styles.lead}>
            근처에서 열리는 오프라인 스터디를 찾고,
            <br />
            출석 기록으로 믿을 수 있는 사람과 만나요.
          </p>
        </div>

        {errorMessage && (
          <p className={styles.error} role="alert">
            {errorMessage}
          </p>
        )}

        {/* 로그인 버튼 주소는 브라우저에서만 만든다(빌드 시 API 주소가 없을 수 있다) */}
        <div className={styles.actions}>
          {session.status === "anonymous" && <SocialLoginButtons />}
          {session.status === "member" && session.me.onboarded && (
            <p className={styles.welcome}>
              {session.me.nickname}님, 반가워요. 스터디 목록은 곧 열려요.
            </p>
          )}
          {session.status === "error" && (
            <p className={styles.error} role="alert">
              서버에 연결하지 못했어요. 잠시 뒤 새로고침해 주세요.
            </p>
          )}
        </div>
      </div>
    </main>
  );
}
