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
import { TextAreaField } from "@/components/TextAreaField";
import { TextField } from "@/components/TextField";
import {
  ACTION_NOTE_MAX,
  REPORT_NOTE_MAX,
  deleteChatMessage,
  fetchAdminReports,
  fetchSanctions,
  forceEndStudy,
  handleReport,
  revokeSanction,
  suspendUser,
  type AdminReport,
  type AdminSanction,
} from "@/lib/api/admin";
import { isApiError } from "@/lib/api/errors";
import { REPORT_REASONS } from "@/lib/api/reports";
import { formatDay, nicknameOf } from "@/lib/studyFormat";
import styles from "./AdminReportsView.module.css";

const REPORTS_KEY = ["admin-reports", "open"] as const;
const SANCTIONS_KEY = ["admin-sanctions"] as const;

const TARGET_LABEL = { STUDY: "스터디", USER: "사용자", MESSAGE: "메시지" };
const KIND_LABEL = {
  JOIN_RESTRICT: "참여 신청 제한",
  SUSPEND: "이용 정지",
  HOST_RESTRICT: "스터디 개설 제한",
};

/** 운영자 조치 실패를 안내 문구로 바꾼다. 서버 문구는 쓰지 않고 코드로 정한다 */
function actionErrorText(error: unknown): string {
  if (isApiError(error, "REPORT_ALREADY_HANDLED")) {
    return "이미 처리된 신고예요. 목록을 새로 불러옵니다.";
  }
  if (isApiError(error, "STUDY_CLOSED")) {
    return "이미 종료됐거나 주최자가 포기한 스터디예요.";
  }
  if (isApiError(error, "NOT_FOUND")) return "대상을 찾을 수 없어요.";
  if (isApiError(error, "FORBIDDEN")) return "운영자 권한이 없어요.";
  if (isApiError(error, "VALIDATION_FAILED")) {
    return "입력한 내용을 확인해 주세요.";
  }
  return "처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.";
}

/**
 * 운영자의 신고 큐와 제재 관리 (PRD 4.11·5장 화면 13, API 7장, PLAN T-060). 신고를 보고 사유를 적어 반려하거나 처리
 * 완료로 표시하고, 대상에 따라 스터디 강제 종료·메시지 삭제·이용 정지를 한다. 되돌릴 수 없는 강제 종료는 한 번 더
 * 확인한다. 아래에서는 제재(자동·수동)를 보고 해제하며 이용 정지를 직접 건다. 운영자 검토 대상 주최자는 표시한다.
 */
export function AdminReportsView() {
  return (
    <AdminGate>
      <main className={styles.page}>
        <Link href="/admin" className={styles.back}>
          ← 운영
        </Link>
        <h1 className={styles.title}>신고 큐</h1>
        <ReportQueue />
        <SanctionManager />
      </main>
    </AdminGate>
  );
}

function ReportQueue() {
  const queue = useInfiniteQuery({
    queryKey: REPORTS_KEY,
    queryFn: ({ pageParam, signal }) => fetchAdminReports(pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    retry: false,
  });
  const items = queue.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <>
      {queue.isPending && <p className={styles.note}>불러오는 중…</p>}
      {queue.isError && (
        <div className={styles.empty} role="alert">
          <p>신고를 불러오지 못했어요.</p>
          <Button onClick={() => void queue.refetch()}>다시 시도</Button>
        </div>
      )}
      {queue.isSuccess && items.length === 0 && (
        <p className={styles.note}>처리할 신고가 없어요.</p>
      )}
      {items.length > 0 && (
        <ul className={styles.list}>
          {items.map((report) => (
            <li key={report.id} className={styles.card}>
              <ReportCard report={report} />
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
    </>
  );
}

function ReportCard({ report }: { report: AdminReport }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const [touched, setTouched] = useState(false);
  const [confirmEnd, setConfirmEnd] = useState(false);
  const [message, setMessage] = useState<{
    text: string;
    tone: "ok" | "error";
  } | null>(null);
  const [days, setDays] = useState("7");

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: REPORTS_KEY });
  const fail = (e: unknown) => {
    setMessage({ text: actionErrorText(e), tone: "error" });
    if (isApiError(e, "REPORT_ALREADY_HANDLED")) void refresh();
  };
  const handle = useMutation({
    mutationFn: (action: "dismiss" | "resolve") =>
      handleReport(report.id, action, note.trim()),
    onSuccess: refresh,
    onError: fail,
  });
  const forceEnd = useMutation({
    mutationFn: () => forceEndStudy(report.targetId, note.trim()),
    onSuccess: () => {
      setConfirmEnd(false);
      setMessage({ text: "스터디를 강제 종료했어요.", tone: "ok" });
    },
    onError: (e) => {
      setConfirmEnd(false);
      fail(e);
    },
  });
  const removeMessage = useMutation({
    mutationFn: () => deleteChatMessage(report.targetId, note.trim()),
    onSuccess: () => setMessage({ text: "메시지를 삭제했어요.", tone: "ok" }),
    onError: fail,
  });
  const suspend = useMutation({
    mutationFn: () => suspendUser(report.targetId, Number(days), note.trim()),
    onSuccess: () => {
      setMessage({ text: "이용 정지를 걸었어요.", tone: "ok" });
      void queryClient.invalidateQueries({ queryKey: SANCTIONS_KEY });
    },
    onError: fail,
  });

  const noteMissing = note.trim().length === 0;
  const busy =
    handle.isPending ||
    forceEnd.isPending ||
    removeMessage.isPending ||
    suspend.isPending;
  const run = (action: () => void) => {
    setTouched(true);
    if (noteMissing) return;
    setMessage(null);
    action();
  };
  const reason =
    REPORT_REASONS.find((r) => r.value === report.reason)?.label ??
    report.reason;

  return (
    <>
      <div className={styles.head}>
        <span className={styles.status}>{TARGET_LABEL[report.targetType]}</span>
        <span className={styles.meta}>
          {reason} · {nicknameOf(report.reporterNickname)} ·{" "}
          {formatDay(report.createdAt)} 신고
        </span>
      </div>
      <p className={styles.label}>신고 대상</p>
      <p className={styles.text}>
        {report.targetSummary ?? "대상을 찾을 수 없어요 (삭제됐을 수 있어요)."}
      </p>
      {report.detail && (
        <>
          <p className={styles.label}>상세 내용</p>
          <p className={styles.text}>{report.detail}</p>
        </>
      )}
      <form
        className={styles.form}
        aria-label={`신고 ${report.id} 처리`}
        onSubmit={(event) => event.preventDefault()}
      >
        <TextAreaField
          label="처리 사유"
          rows={2}
          maxLength={REPORT_NOTE_MAX}
          value={note}
          onChange={(value) => {
            setNote(value);
            setMessage(null);
          }}
          error={
            touched && noteMissing ? "처리 사유를 적어 주세요." : undefined
          }
          hint="반려·처리 완료와 아래 조치에 함께 기록돼요."
        />
        {report.targetType === "USER" && (
          <TextField
            label="정지 기간(일)"
            inputMode="numeric"
            value={days}
            onChange={setDays}
          />
        )}
        {message && (
          <p
            role={message.tone === "error" ? "alert" : "status"}
            className={styles.result}
            data-tone={message.tone}
          >
            {message.text}
          </p>
        )}
        {confirmEnd && (
          <div
            className={styles.confirm}
            role="alertdialog"
            aria-label="강제 종료 확인"
          >
            <p>
              이 스터디를 강제 종료할까요? 참여자와 대기자에게 알림이 가고
              되돌릴 수 없어요.
            </p>
            <div className={styles.actions}>
              <Button onClick={() => setConfirmEnd(false)}>취소</Button>
              <Button
                variant="primary"
                onClick={() => forceEnd.mutate()}
                pending={forceEnd.isPending}
                pendingLabel="종료 중…"
              >
                강제 종료 확정
              </Button>
            </div>
          </div>
        )}
        <div className={styles.actions}>
          {report.targetType === "STUDY" && (
            <Button
              disabled={busy}
              onClick={() => run(() => setConfirmEnd(true))}
            >
              강제 종료
            </Button>
          )}
          {report.targetType === "MESSAGE" && (
            <Button
              disabled={busy}
              onClick={() => run(() => removeMessage.mutate())}
            >
              메시지 삭제
            </Button>
          )}
          {report.targetType === "USER" && (
            <Button
              disabled={busy || !/^[1-9]\d*$/.test(days.trim())}
              onClick={() => run(() => suspend.mutate())}
            >
              이용 정지
            </Button>
          )}
          <Button
            disabled={busy}
            onClick={() => run(() => handle.mutate("dismiss"))}
          >
            반려
          </Button>
          <Button
            variant="primary"
            disabled={busy}
            onClick={() => run(() => handle.mutate("resolve"))}
          >
            처리 완료
          </Button>
        </div>
      </form>
    </>
  );
}

function SanctionManager() {
  const [reviewOnly, setReviewOnly] = useState(false);
  const sanctions = useInfiniteQuery({
    queryKey: [...SANCTIONS_KEY, reviewOnly],
    queryFn: ({ pageParam, signal }) =>
      fetchSanctions(
        { activeOnly: true, reviewRequired: reviewOnly },
        pageParam,
        signal,
      ),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    retry: false,
  });
  const items = sanctions.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section className={styles.section} aria-label="제재 관리">
      <h2 className={styles.sectionTitle}>제재 관리</h2>
      <label className={styles.filter}>
        <input
          type="checkbox"
          checked={reviewOnly}
          onChange={(event) => setReviewOnly(event.target.checked)}
        />
        운영자 검토 대상만 보기
      </label>
      {sanctions.isPending && <p className={styles.note}>불러오는 중…</p>}
      {sanctions.isError && (
        <div className={styles.empty} role="alert">
          <p>제재를 불러오지 못했어요.</p>
          <Button onClick={() => void sanctions.refetch()}>다시 시도</Button>
        </div>
      )}
      {sanctions.isSuccess && items.length === 0 && (
        <p className={styles.note}>유효한 제재가 없어요.</p>
      )}
      {items.length > 0 && (
        <ul className={styles.list}>
          {items.map((sanction) => (
            <li key={sanction.id} className={styles.card}>
              <SanctionCard sanction={sanction} />
            </li>
          ))}
        </ul>
      )}
      {sanctions.hasNextPage && (
        <Button
          onClick={() => void sanctions.fetchNextPage()}
          pending={sanctions.isFetchingNextPage}
          pendingLabel="불러오는 중…"
        >
          더 보기
        </Button>
      )}
      <SuspendForm />
    </section>
  );
}

function SanctionCard({ sanction }: { sanction: AdminSanction }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const revoke = useMutation({
    mutationFn: () => revokeSanction(sanction.id, note.trim()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SANCTIONS_KEY }),
    onError: (e) => setError(actionErrorText(e)),
  });
  return (
    <>
      <div className={styles.head}>
        <span className={styles.status}>{KIND_LABEL[sanction.kind]}</span>
        {sanction.reviewRequired && (
          <span className={styles.review}>운영자 검토 대상</span>
        )}
        <span className={styles.meta}>
          회원 {sanction.userId} ·{" "}
          {sanction.source === "AUTO" ? "자동" : "운영자"} ·{" "}
          {formatDay(sanction.endsAt)}까지
        </span>
      </div>
      <p className={styles.text}>{sanction.reason}</p>
      {open ? (
        <form
          className={styles.form}
          aria-label={`제재 ${sanction.id} 해제`}
          onSubmit={(event) => {
            event.preventDefault();
            if (note.trim().length === 0) return;
            setError(null);
            revoke.mutate();
          }}
        >
          <TextAreaField
            label="해제 사유"
            rows={2}
            maxLength={ACTION_NOTE_MAX}
            value={note}
            onChange={setNote}
          />
          {error && (
            <p role="alert" className={styles.result} data-tone="error">
              {error}
            </p>
          )}
          <div className={styles.actions}>
            <Button onClick={() => setOpen(false)}>취소</Button>
            <Button
              type="submit"
              variant="primary"
              disabled={note.trim().length === 0}
              pending={revoke.isPending}
              pendingLabel="해제 중…"
            >
              해제하기
            </Button>
          </div>
        </form>
      ) : (
        <div className={styles.actions}>
          <Button onClick={() => setOpen(true)}>제재 해제</Button>
        </div>
      )}
    </>
  );
}

/** 신고 없이도 이용 정지를 직접 건다 (PRD 4.11 조치 4) */
function SuspendForm() {
  const queryClient = useQueryClient();
  const [userId, setUserId] = useState("");
  const [days, setDays] = useState("7");
  const [note, setNote] = useState("");
  const [outcome, setOutcome] = useState<{
    text: string;
    tone: "ok" | "error";
  } | null>(null);
  const suspend = useMutation({
    mutationFn: () => suspendUser(Number(userId), Number(days), note.trim()),
    onSuccess: () => {
      setOutcome({ text: "이용 정지를 걸었어요.", tone: "ok" });
      setUserId("");
      setNote("");
      void queryClient.invalidateQueries({ queryKey: SANCTIONS_KEY });
    },
    onError: (e) => setOutcome({ text: actionErrorText(e), tone: "error" }),
  });
  const ready =
    /^[1-9]\d*$/.test(userId.trim()) &&
    /^[1-9]\d*$/.test(days.trim()) &&
    note.trim().length > 0;
  return (
    <form
      className={styles.form}
      aria-label="이용 정지 부여"
      onSubmit={(event) => {
        event.preventDefault();
        if (!ready) return;
        setOutcome(null);
        suspend.mutate();
      }}
    >
      <h3 className={styles.label}>이용 정지 직접 걸기</h3>
      <TextField
        label="회원 번호"
        inputMode="numeric"
        value={userId}
        onChange={setUserId}
      />
      <TextField
        label="기간(일, 1~365)"
        inputMode="numeric"
        value={days}
        onChange={setDays}
      />
      <TextAreaField
        label="정지 사유"
        rows={2}
        maxLength={ACTION_NOTE_MAX}
        value={note}
        onChange={setNote}
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
        disabled={!ready}
        pending={suspend.isPending}
        pendingLabel="거는 중…"
      >
        이용 정지
      </Button>
    </form>
  );
}
