"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { BottomBar } from "@/components/BottomBar";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import {
  cancelWaiting,
  fetchLeavePreview,
  joinStudy,
  leaveStudy,
  registerWaiting,
  type LeavePreview,
  type StudyDetail,
} from "@/lib/api/studies";
import {
  actionErrorText,
  actionOf,
  jobCategoryOf,
  leaveConfirmText,
  leftNotice,
  shouldRefetchAfter,
} from "@/lib/studyActions";
import { useMetaOptions, useSession } from "@/lib/session";
import styles from "./StudyDetailView.module.css";

type Props = {
  study: StudyDetail;
  now: Date;
  /** 직군 코드 → 이름 */
  names: ReadonlyMap<string, string>;
};

/**
 * 상세의 참여·대기·나가기 영역 (PLAN T-024, DESIGN 6장 상세). 무엇을 보여 줄지는 `actionOf` 가 정하고 여기서는
 * 요청과 결과 안내만 맡는다. 나가기는 사전 조회 결과를 확인 창에 보여 준 뒤에 보낸다.
 */
export function StudyActions({ study, now, names }: Props) {
  const queryClient = useQueryClient();
  const session = useSession();
  const meta = useMetaOptions();
  const [message, setMessage] = useState<{
    text: string;
    tone: "ok" | "error";
  } | null>(null);
  const [preview, setPreview] = useState<LeavePreview | null>(null);

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ["study", study.id] });
    await queryClient.invalidateQueries({ queryKey: ["studies"] });
  };
  const fail = (error: unknown) => {
    setMessage({ text: actionErrorText(error), tone: "error" });
    if (shouldRefetchAfter(error)) void refresh();
  };
  const succeed = (text: string) => {
    setMessage({ text, tone: "ok" });
    setPreview(null);
    void refresh();
  };

  const join = useMutation({
    mutationFn: () => joinStudy(study.id),
    onSuccess: () => succeed("참여했어요."),
    onError: fail,
  });
  const wait = useMutation({
    mutationFn: () => registerWaiting(study.id),
    onSuccess: () => succeed("대기를 등록했어요. 자리가 나면 알려 드려요."),
    onError: fail,
  });
  const unwait = useMutation({
    mutationFn: () => cancelWaiting(study.id),
    onSuccess: () => succeed("대기를 취소했어요."),
    onError: fail,
  });
  const askLeave = useMutation({
    mutationFn: () => fetchLeavePreview(study.id),
    onSuccess: (result) => {
      setMessage(null);
      setPreview(result);
    },
    onError: fail,
  });
  const leave = useMutation({
    mutationFn: () => leaveStudy(study.id),
    onSuccess: (result) => succeed(leftNotice(result)),
    onError: (error) => {
      setPreview(null);
      fail(error);
    },
  });

  const me = session.status === "member" ? session.me : null;
  const action = actionOf({
    study,
    now,
    session: session.status,
    onboarded: me?.onboarded ?? false,
    jobCategory: jobCategoryOf(meta.data?.jobs ?? [], me?.jobCode ?? null),
    jobNames: names,
  });
  const busy =
    join.isPending ||
    wait.isPending ||
    unwait.isPending ||
    askLeave.isPending ||
    leave.isPending;

  const result = message && (
    <p
      role={message.tone === "error" ? "alert" : "status"}
      className={styles.result}
      data-tone={message.tone}
    >
      {message.text}
    </p>
  );

  if (preview) {
    const confirm = leaveConfirmText(preview);
    return (
      <div
        role="alertdialog"
        aria-label={confirm.title}
        className={styles.confirm}
      >
        <p className={styles.confirmTitle}>{confirm.title}</p>
        {confirm.lines.map((line) => (
          <p key={line} className={styles.sub}>
            {line}
          </p>
        ))}
        <BottomBar>
          <Button
            variant="secondary"
            onClick={() => setPreview(null)}
            disabled={leave.isPending}
          >
            돌아가기
          </Button>
          <Button
            variant="primary"
            pending={leave.isPending}
            pendingLabel="처리하는 중…"
            onClick={() => leave.mutate()}
          >
            {confirm.confirm}
          </Button>
        </BottomBar>
      </div>
    );
  }

  switch (action.kind) {
    case "none":
      return (
        <>
          {result}
          {action.text && <p className={styles.guide}>{action.text}</p>}
        </>
      );
    case "login":
      return (
        <Link
          href="/login"
          className={buttonStyles.button}
          data-variant="primary"
        >
          로그인하고 참여하기
        </Link>
      );
    case "onboarding":
      return (
        <>
          <p className={styles.guide}>
            닉네임·직군·지역을 입력하면 참여할 수 있어요.
          </p>
          <Link
            href="/onboarding"
            className={buttonStyles.button}
            data-variant="primary"
          >
            프로필 입력하기
          </Link>
        </>
      );
    case "join-blocked":
      return (
        <>
          {result}
          <p className={styles.guide}>{action.text}</p>
        </>
      );
    case "join":
      return (
        <>
          {result}
          <p className={styles.guide}>
            {study.capacity - study.memberCount}자리 남았어요.
          </p>
          <Button
            variant="primary"
            disabled={busy}
            pending={join.isPending}
            pendingLabel="신청하는 중…"
            onClick={() => join.mutate()}
          >
            참여하기
          </Button>
        </>
      );
    case "wait":
      return (
        <>
          {result}
          <p className={styles.guide}>{action.note}</p>
          <Button
            variant="primary"
            disabled={busy}
            pending={wait.isPending}
            pendingLabel="등록하는 중…"
            onClick={() => wait.mutate()}
          >
            대기하기
          </Button>
        </>
      );
    case "cancel-wait":
      return (
        <>
          {result}
          <p className={styles.guide}>{action.note}</p>
          <Button
            variant="secondary"
            disabled={busy}
            pending={unwait.isPending}
            pendingLabel="취소하는 중…"
            onClick={() => unwait.mutate()}
          >
            대기 취소
          </Button>
        </>
      );
    case "leave":
      return (
        <>
          {result}
          <p className={styles.guide}>{action.text}</p>
          <Button
            variant="secondary"
            disabled={busy}
            pending={askLeave.isPending}
            pendingLabel="확인하는 중…"
            onClick={() => askLeave.mutate()}
          >
            {action.host ? "주최 그만두기" : "참여 취소"}
          </Button>
        </>
      );
  }
}
