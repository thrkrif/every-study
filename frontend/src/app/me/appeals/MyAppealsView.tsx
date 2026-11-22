"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import Link from "next/link";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import { fetchMyAppeals } from "@/lib/api/appeals";
import { appealStatusText } from "@/lib/appealRules";
import { useSession } from "@/lib/session";
import { formatDay, verdictLabel } from "@/lib/studyFormat";
import styles from "./MyAppealsView.module.css";

/** 내 이의 내역 (PRD 4.9·화면 11). 사유, 주최자 1차 의견, 처리 상태를 한곳에서 본다 */
export function MyAppealsView() {
  const session = useSession();
  const appeals = useInfiniteQuery({
    queryKey: ["my-appeals"],
    queryFn: ({ pageParam, signal }) => fetchMyAppeals(pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: session.status === "member",
  });

  if (session.status === "loading") {
    return (
      <main className={styles.page}>
        <p className={styles.note}>불러오는 중…</p>
      </main>
    );
  }
  if (session.status === "anonymous") {
    return (
      <main className={styles.page}>
        <div className={styles.empty}>
          <p>로그인하면 이의 내역을 볼 수 있어요.</p>
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
  const items = appeals.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <main className={styles.page}>
      <Link href="/me" className={styles.back}>
        ← 마이페이지
      </Link>
      <h1 className={styles.title}>이의 내역</h1>

      {appeals.isPending && <p className={styles.note}>불러오는 중…</p>}
      {appeals.isError && (
        <div className={styles.empty} role="alert">
          <p>이의 내역을 불러오지 못했어요.</p>
          <Button onClick={() => void appeals.refetch()}>다시 시도</Button>
        </div>
      )}
      {appeals.isSuccess && items.length === 0 && (
        <p className={styles.note}>신청한 이의가 없어요.</p>
      )}
      {items.length > 0 && (
        <ul className={styles.list}>
          {items.map((appeal) => (
            <li key={appeal.id} className={styles.card}>
              <div className={styles.head}>
                <span className={styles.status} data-status={appeal.status}>
                  {appealStatusText(appeal.status)}
                </span>
                <span className={styles.meta}>
                  {verdictLabel(appeal.verdictType)} ·{" "}
                  {formatDay(appeal.createdAt)} 신청
                </span>
              </div>
              <Link
                href={`/studies/${appeal.studyId}`}
                className={styles.study}
              >
                {appeal.studyTitle}
              </Link>
              <p className={styles.label}>내 사유</p>
              <p className={styles.text}>{appeal.reason}</p>
              <p className={styles.label}>주최자 의견</p>
              <p className={styles.text}>
                {appeal.hostOpinion ?? "아직 의견이 없어요."}
              </p>
            </li>
          ))}
        </ul>
      )}
      {appeals.hasNextPage && (
        <div className={styles.more}>
          <Button
            disabled={appeals.isFetchingNextPage}
            onClick={() => void appeals.fetchNextPage()}
          >
            {appeals.isFetchingNextPage ? "불러오는 중…" : "더 보기"}
          </Button>
        </div>
      )}
    </main>
  );
}
