"use client";

import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { AdminGate } from "@/components/AdminGate";
import { Button } from "@/components/Button";
import { ChoiceChips } from "@/components/ChoiceChips";
import { TextAreaField } from "@/components/TextAreaField";
import { TextField } from "@/components/TextField";
import {
  CORRECTION_NOTE_MAX,
  DECISION_NOTE_MAX,
  correctAttendance,
  decideAppeal,
  fetchAdminAppeals,
  type AdminAppeal,
  type CorrectionResult,
} from "@/lib/api/admin";
import { isApiError } from "@/lib/api/errors";
import { appealStatusText } from "@/lib/appealRules";
import {
  formatClockOf,
  formatDay,
  nicknameOf,
  verdictLabel,
} from "@/lib/studyFormat";
import styles from "./AdminAppealsView.module.css";

const QUEUE_KEY = ["admin-appeals", "open"] as const;

/** 처리 실패를 안내 문구로 바꾼다. 서버 문구는 쓰지 않고 코드로 정한다 */
function decisionErrorText(error: unknown): string {
  if (isApiError(error, "APPEAL_CLOSED")) {
    return "이미 결정됐거나 더는 결정할 수 없는 이의예요. 목록을 새로 불러옵니다.";
  }
  if (isApiError(error, "NOT_FOUND")) return "이의를 찾을 수 없어요.";
  if (isApiError(error, "FORBIDDEN")) return "운영자 권한이 없어요.";
  if (isApiError(error, "VALIDATION_FAILED")) {
    return `사유는 1자 이상 ${DECISION_NOTE_MAX}자 이하로 적어 주세요.`;
  }
  return "처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.";
}

function correctionErrorText(error: unknown): string {
  if (isApiError(error, "CORRECTION_NOT_ALLOWED")) {
    return "정정할 수 없는 판정이에요. 이미 정정됐거나, 노쇼 후보·지각·노쇼가 아니거나, 열린 이의가 있어요.";
  }
  if (isApiError(error, "NOT_FOUND")) return "판정을 찾을 수 없어요.";
  if (isApiError(error, "FORBIDDEN")) return "운영자 권한이 없어요.";
  if (isApiError(error, "VALIDATION_FAILED")) {
    return `판정 번호와 사유(1~${CORRECTION_NOTE_MAX}자)를 확인해 주세요.`;
  }
  return "정정하지 못했어요. 잠시 뒤 다시 시도해 주세요.";
}

/**
 * 운영자의 이의 큐와 개별 출석 정정 (PRD 4.9·5장 화면 13, API 7장, PLAN T-059). 열린 이의의 사유와 주최자 의견을 보고
 * 사유를 적어 인정·기각한다. 결정하면 큐에서 사라진다. 이의 없이 판정을 바로잡을 때는 아래 "개별 출석 정정"을 쓴다.
 */
export function AdminAppealsView() {
  return (
    <AdminGate>
      <Queue />
    </AdminGate>
  );
}

function Queue() {
  const queue = useInfiniteQuery({
    queryKey: QUEUE_KEY,
    queryFn: ({ pageParam, signal }) =>
      fetchAdminAppeals("open", pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    retry: false,
  });
  const items = queue.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <main className={styles.page}>
      <Link href="/admin" className={styles.back}>
        ← 운영
      </Link>
      <h1 className={styles.title}>이의 큐</h1>
      {queue.isPending && <p className={styles.note}>불러오는 중…</p>}
      {queue.isError && (
        <div className={styles.empty} role="alert">
          <p>이의를 불러오지 못했어요.</p>
          <Button onClick={() => void queue.refetch()}>다시 시도</Button>
        </div>
      )}
      {queue.isSuccess && items.length === 0 && (
        <p className={styles.note}>처리할 이의가 없어요.</p>
      )}
      {items.length > 0 && (
        <ul className={styles.list}>
          {items.map((appeal) => (
            <li key={appeal.id} className={styles.card}>
              <AppealCard appeal={appeal} />
            </li>
          ))}
        </ul>
      )}
      {queue.hasNextPage && (
        <Button
          onClick={() => void queue.fetchNextPage()}
          pending={queue.isFetchingNextPage}
          pendingLabel="불러오는 중…"
        >
          더 보기
        </Button>
      )}
      <CorrectionForm />
    </main>
  );
}

function AppealCard({ appeal }: { appeal: AdminAppeal }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: (accept: boolean) =>
      decideAppeal(appeal.id, accept, note.trim()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUEUE_KEY }),
    onError: (e) => {
      setError(decisionErrorText(e));
      if (isApiError(e, "APPEAL_CLOSED")) {
        void queryClient.invalidateQueries({ queryKey: QUEUE_KEY });
      }
    },
  });
  const noteMissing = note.trim().length === 0;

  const decide = (accept: boolean) => {
    setTouched(true);
    if (noteMissing) return;
    setError(null);
    mutation.mutate(accept);
  };

  return (
    <>
      <div className={styles.head}>
        <span className={styles.status} data-status={appeal.status}>
          {appealStatusText(appeal.status)}
        </span>
        <span className={styles.meta}>
          {nicknameOf(appeal.applicantNickname)} · {appeal.studyTitle} ·{" "}
          {verdictLabel(appeal.verdictType)} · {formatDay(appeal.createdAt)}{" "}
          신청
        </span>
      </div>
      <p className={styles.label}>이의 사유</p>
      <p className={styles.text}>{appeal.reason}</p>
      <p className={styles.label}>주최자 의견</p>
      <p className={styles.text}>
        {appeal.hostOpinion ?? "남긴 의견이 없어요."}
      </p>
      <form
        className={styles.form}
        onSubmit={(event) => event.preventDefault()}
        aria-label={`이의 ${appeal.id} 결정`}
      >
        <TextAreaField
          label="결정 사유"
          rows={2}
          maxLength={DECISION_NOTE_MAX}
          value={note}
          onChange={(value) => {
            setNote(value);
            setError(null);
          }}
          hint={`신청자에게 알림으로 가요. 기한 ${formatDay(appeal.dueAt)} ${formatClockOf(appeal.dueAt)}`}
          error={
            touched && noteMissing ? "결정 사유를 적어 주세요." : undefined
          }
        />
        {error && (
          <p role="alert" className={styles.result} data-tone="error">
            {error}
          </p>
        )}
        <div className={styles.actions}>
          <Button onClick={() => decide(false)} disabled={mutation.isPending}>
            기각
          </Button>
          <Button
            variant="primary"
            onClick={() => decide(true)}
            pending={mutation.isPending}
            pendingLabel="처리 중…"
          >
            인정
          </Button>
        </div>
      </form>
    </>
  );
}

/** 이의 없이 운영자가 판정을 바로잡는다 (PRD 9장, API 7장 X-7). 판정 번호는 출석 기록의 이벤트 번호다 */
function CorrectionForm() {
  const [eventId, setEventId] = useState("");
  const [result, setResult] = useState<CorrectionResult | undefined>();
  const [note, setNote] = useState("");
  const [outcome, setOutcome] = useState<{
    text: string;
    tone: "ok" | "error";
  } | null>(null);
  const mutation = useMutation({
    mutationFn: () => correctAttendance(Number(eventId), result!, note.trim()),
    onSuccess: (done) => {
      setOutcome({
        text:
          done.verdict === "ATTENDED"
            ? "출석으로 정정했어요."
            : "판정을 무효화했어요.",
        tone: "ok",
      });
      setEventId("");
      setNote("");
    },
    onError: (e) => setOutcome({ text: correctionErrorText(e), tone: "error" }),
  });
  const idValid = /^[1-9]\d*$/.test(eventId.trim());
  const ready = idValid && result !== undefined && note.trim().length > 0;

  return (
    <form
      className={styles.section}
      aria-label="개별 출석 정정"
      onSubmit={(event) => {
        event.preventDefault();
        if (!ready) return;
        setOutcome(null);
        mutation.mutate();
      }}
    >
      <h2 className={styles.sectionTitle}>개별 출석 정정</h2>
      <p className={styles.note}>
        서버 장애처럼 이의 없이 판정을 바로잡을 때 써요. 노쇼 후보·지각·노쇼
        판정만 정정할 수 있어요.
      </p>
      <TextField
        label="판정 번호"
        inputMode="numeric"
        value={eventId}
        onChange={(value) => {
          setEventId(value);
          setOutcome(null);
        }}
      />
      <ChoiceChips
        legend="정정 결과"
        choices={[
          { value: "ATTENDED", label: "출석으로 정정" },
          { value: "CLEARED", label: "판정 무효화" },
        ]}
        value={result}
        onChange={(value) => setResult(value as CorrectionResult)}
      />
      <TextAreaField
        label="정정 사유"
        rows={2}
        maxLength={CORRECTION_NOTE_MAX}
        value={note}
        onChange={(value) => {
          setNote(value);
          setOutcome(null);
        }}
      />
      {outcome && (
        <p
          role={outcome.tone === "error" ? "alert" : "status"}
          className={styles.result}
          data-tone={outcome.tone}
        >
          {outcome.text}
        </p>
      )}
      <Button
        type="submit"
        variant="primary"
        disabled={!ready}
        pending={mutation.isPending}
        pendingLabel="정정 중…"
      >
        정정하기
      </Button>
    </form>
  );
}
