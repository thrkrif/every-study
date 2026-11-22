"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/Button";
import { TextAreaField } from "@/components/TextAreaField";
import { APPEAL_REASON_MAX, fileAppeal } from "@/lib/api/appeals";
import { appealErrorText, textProblem } from "@/lib/appealRules";
import styles from "./MyPageView.module.css";

/** 출석 기록 한 줄에서 펼치는 이의 신청 입력 (PRD 4.9). 판정당 1회라 한 번 내면 줄이 "이의 신청함"으로 바뀐다 */
export function AppealForm({
  eventId,
  onClose,
}: {
  eventId: number;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () => fileAppeal(eventId, reason.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["my-attendance"] });
      void queryClient.invalidateQueries({ queryKey: ["my-appeals"] });
      onClose();
    },
    onError: (error) => {
      setFailure(appealErrorText(error));
      // 기한 지남·이미 신청 같은 상태 변화는 목록을 다시 받아 버튼을 정리한다
      void queryClient.invalidateQueries({ queryKey: ["my-attendance"] });
    },
  });
  const problem = touched ? textProblem(reason, "reason") : undefined;

  return (
    <form
      className={styles.appealForm}
      onSubmit={(event) => {
        event.preventDefault();
        setTouched(true);
        if (textProblem(reason, "reason")) return;
        setFailure(null);
        mutation.mutate();
      }}
    >
      <TextAreaField
        label="이의 사유"
        rows={4}
        maxLength={APPEAL_REASON_MAX}
        value={reason}
        onChange={(value) => {
          setReason(value);
          setFailure(null);
        }}
        error={problem}
        hint="어떤 사정이 있었는지 적어 주세요. 주최자 의견을 거쳐 운영자가 결정해요."
      />
      {failure && (
        <p role="alert" className={styles.failure}>
          {failure}
        </p>
      )}
      <div className={styles.appealActions}>
        <Button onClick={onClose} disabled={mutation.isPending}>
          취소
        </Button>
        <Button
          type="submit"
          variant="primary"
          pending={mutation.isPending}
          pendingLabel="보내는 중…"
        >
          이의 신청
        </Button>
      </div>
    </form>
  );
}
