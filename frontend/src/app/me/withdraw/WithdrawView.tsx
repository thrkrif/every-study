"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import { withdraw } from "@/lib/api/me";
import { useSession } from "@/lib/session";
import styles from "./WithdrawView.module.css";

/**
 * 회원 탈퇴 (PRD 4.1, API 3장, PLAN T-063). 탈퇴하면 진행 중인 스터디에 어떤 일이 생기는지 먼저 알리고, 이해했다는
 * 체크와 한 번 더 확인을 거쳐야 서버를 부른다. 성공하면 로그인 상태를 비우고 처음 화면으로 보낸다.
 */
export function WithdrawView() {
  const session = useSession();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [agreed, setAgreed] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (session.status === "loading") {
    return (
      <main className={styles.page}>
        <p>불러오는 중…</p>
      </main>
    );
  }
  if (session.status !== "member") {
    return (
      <main className={styles.page}>
        <div className={styles.empty} role="status">
          <p>로그인한 회원만 탈퇴할 수 있어요.</p>
          <Link
            href="/login"
            className={buttonStyles.button}
            data-variant="primary"
          >
            로그인
          </Link>
        </div>
      </main>
    );
  }

  const leave = async () => {
    setPending(true);
    setError(null);
    try {
      await withdraw();
      // 쿠키가 지워졌으니 로그인 상태와 회원별 응답을 처음부터 다시 받는다
      await queryClient.resetQueries();
      router.push("/");
    } catch {
      setError("탈퇴하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
      setConfirming(false);
    } finally {
      setPending(false);
    }
  };

  return (
    <main className={styles.page}>
      <Link href="/me" className={styles.back}>
        ← 마이페이지
      </Link>
      <h1 className={styles.title}>회원 탈퇴</h1>
      <p>탈퇴하면 아래 일이 한꺼번에 일어나고 되돌릴 수 없어요.</p>
      <ul className={styles.effects}>
        <li>
          참여 중인 스터디는 참여 취소로 처리돼요. 시작 3시간 이내라면 직전
          취소로 기록이 남아요.
        </li>
        <li>
          주최 중인 스터디는 남은 참여자 중 한 명에게 넘어가고, 혼자라면
          취소돼요. 시작 3시간 이내에 넘기면 주최자 감점이 붙어요.
        </li>
        <li>
          이미 시작한 스터디는 그대로 두고 출석 판정에 맡겨요. 대기 중인
          스터디의 대기는 모두 취소돼요.
        </li>
        <li>
          닉네임·이메일·소셜 로그인 정보가 삭제돼요. 다른 사람의 채팅과 출석
          기록에는 &quot;탈퇴한 사용자&quot;로 남아요.
        </li>
        <li>
          같은 소셜 계정으로 다시 가입하면 새 회원이에요. 이전 출석 기록은
          이어지지 않아요.
        </li>
      </ul>
      <label className={styles.agree}>
        <input
          type="checkbox"
          checked={agreed}
          onChange={(event) => {
            setAgreed(event.target.checked);
            setConfirming(false);
          }}
        />
        위 내용을 이해했고 탈퇴할게요.
      </label>
      {error && (
        <p className={styles.error} role="alert">
          ! {error}
        </p>
      )}
      {confirming ? (
        <div
          className={styles.confirm}
          role="alertdialog"
          aria-label="탈퇴 확인"
        >
          <p>정말 탈퇴할까요? 이 작업은 되돌릴 수 없어요.</p>
          <div className={styles.actions}>
            <Button onClick={() => setConfirming(false)} disabled={pending}>
              취소
            </Button>
            <Button
              variant="primary"
              onClick={() => void leave()}
              pending={pending}
              pendingLabel="탈퇴 중…"
            >
              탈퇴 확정
            </Button>
          </div>
        </div>
      ) : (
        <Button
          variant="primary"
          disabled={!agreed}
          onClick={() => setConfirming(true)}
        >
          회원 탈퇴
        </Button>
      )}
    </main>
  );
}
