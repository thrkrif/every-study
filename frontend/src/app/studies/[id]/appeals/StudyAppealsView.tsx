"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import { TextAreaField } from "@/components/TextAreaField";
import {
  APPEAL_REASON_MAX,
  fetchStudyAppeals,
  saveHostOpinion,
  type HostAppeal,
} from "@/lib/api/appeals";
import { isApiError } from "@/lib/api/errors";
import {
  appealErrorText,
  appealStatusText,
  textProblem,
} from "@/lib/appealRules";
import { useSession } from "@/lib/session";
import {
  formatClockOf,
  formatDay,
  nicknameOf,
  verdictLabel,
} from "@/lib/studyFormat";
import styles from "./StudyAppealsView.module.css";

/**
 * 스터디에 들어온 이의와 주최자 1차 의견 (PRD 4.9, API 5장). 주최자만 본다. 의견은 운영자가 결정하기 전까지
 * 다시 보내 고칠 수 있다. 결정(인정·기각)은 운영자가 한다.
 */
export function StudyAppealsView({ id }: { id: string }) {
  const studyId = /^\d+$/.test(id) ? Number(id) : undefined;
  const session = useSession();
  const appeals = useQuery({
    queryKey: ["study-appeals", studyId],
    queryFn: ({ signal }) => fetchStudyAppeals(studyId!, signal),
    enabled: studyId !== undefined && session.status === "member",
    retry: false,
  });

  if (studyId === undefined || isApiError(appeals.error, "NOT_FOUND")) {
    return <Notice text="스터디를 찾을 수 없어요." />;
  }
  if (session.status === "loading") {
    return <Notice text="불러오는 중…" />;
  }
  if (session.status === "anonymous") {
    return (
      <Notice
        text="로그인한 주최자만 볼 수 있어요."
        href="/login"
        cta="로그인"
      />
    );
  }
  if (isApiError(appeals.error, "FORBIDDEN")) {
    return (
      <Notice
        text="주최자만 볼 수 있어요."
        href={`/studies/${studyId}`}
        cta="스터디 보기"
      />
    );
  }
  if (appeals.isError || session.status === "error") {
    return (
      <Notice
        text="이의를 불러오지 못했어요."
        action={() => void appeals.refetch()}
      />
    );
  }
  if (appeals.isPending) return <Notice text="불러오는 중…" />;

  return (
    <main className={styles.page}>
      <Link href={`/studies/${studyId}/manage`} className={styles.back}>
        ← 스터디 관리
      </Link>
      <h1 className={styles.title}>들어온 이의</h1>
      <p className={styles.note}>
        참여자가 노쇼·지각 판정에 이의를 내면 여기에 보여요. 의견은 운영자가
        결정할 때 참고돼요.
      </p>
      {appeals.data.length === 0 ? (
        <p className={styles.note}>들어온 이의가 없어요.</p>
      ) : (
        <ul className={styles.list}>
          {appeals.data.map((appeal) => (
            <li key={appeal.id} className={styles.card}>
              <AppealCard studyId={studyId} appeal={appeal} />
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

function AppealCard({
  studyId,
  appeal,
}: {
  studyId: number;
  appeal: HostAppeal;
}) {
  const queryClient = useQueryClient();
  const [opinion, setOpinion] = useState(appeal.hostOpinion ?? "");
  const [touched, setTouched] = useState(false);
  const [result, setResult] = useState<{
    text: string;
    tone: "ok" | "error";
  } | null>(null);
  const open = appeal.status === "OPEN";
  const mutation = useMutation({
    mutationFn: () => saveHostOpinion(appeal.id, opinion.trim()),
    onSuccess: () => {
      setResult({ text: "의견을 저장했어요.", tone: "ok" });
      void queryClient.invalidateQueries({
        queryKey: ["study-appeals", studyId],
      });
    },
    onError: (error) => {
      setResult({ text: appealErrorText(error), tone: "error" });
      // 이미 결정된 이의라면 목록을 다시 받아 입력칸을 닫는다
      void queryClient.invalidateQueries({
        queryKey: ["study-appeals", studyId],
      });
    },
  });
  const problem = touched ? textProblem(opinion, "opinion") : undefined;

  return (
    <>
      <div className={styles.head}>
        <span className={styles.status} data-status={appeal.status}>
          {appealStatusText(appeal.status)}
        </span>
        <span className={styles.meta}>
          {nicknameOf(appeal.applicantNickname)} ·{" "}
          {verdictLabel(appeal.verdictType)} · {formatDay(appeal.createdAt)}{" "}
          신청
        </span>
      </div>
      <p className={styles.label}>이의 사유</p>
      <p className={styles.text}>{appeal.reason}</p>
      {open ? (
        <form
          className={styles.form}
          onSubmit={(event) => {
            event.preventDefault();
            setTouched(true);
            if (textProblem(opinion, "opinion")) return;
            setResult(null);
            mutation.mutate();
          }}
        >
          <TextAreaField
            label="주최자 의견"
            rows={3}
            maxLength={APPEAL_REASON_MAX}
            value={opinion}
            onChange={(value) => {
              setOpinion(value);
              setResult(null);
            }}
            error={problem}
            hint={`운영자가 결정하기 전까지 고칠 수 있어요. 의견 기한 ${formatDay(appeal.dueAt)} ${formatClockOf(appeal.dueAt)}`}
          />
          {result && (
            <p
              role={result.tone === "error" ? "alert" : "status"}
              className={styles.result}
              data-tone={result.tone}
            >
              {result.text}
            </p>
          )}
          <Button
            type="submit"
            variant="primary"
            pending={mutation.isPending}
            pendingLabel="저장 중…"
          >
            의견 저장
          </Button>
        </form>
      ) : (
        <>
          <p className={styles.label}>주최자 의견</p>
          <p className={styles.text}>
            {appeal.hostOpinion ?? "남긴 의견이 없어요."}
          </p>
        </>
      )}
    </>
  );
}

function Notice({
  text,
  href,
  cta,
  action,
}: {
  text: string;
  href?: string;
  cta?: string;
  action?: () => void;
}) {
  return (
    <main className={styles.page}>
      <div className={styles.empty} role="status">
        <p>{text}</p>
        {href && cta && (
          <Link
            href={href}
            className={buttonStyles.button}
            data-variant="primary"
          >
            {cta}
          </Link>
        )}
        {action && <Button onClick={action}>다시 시도</Button>}
      </div>
    </main>
  );
}
