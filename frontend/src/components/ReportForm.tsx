"use client";

import { useState } from "react";
import { isApiError } from "@/lib/api/errors";
import {
  REPORT_DETAIL_MAX,
  REPORT_REASONS,
  fileReport,
  type ReportReason,
  type ReportTargetType,
} from "@/lib/api/reports";
import { Button } from "./Button";
import { ChoiceChips } from "./ChoiceChips";
import styles from "./ReportForm.module.css";
import { TextAreaField } from "./TextAreaField";

type Props = {
  targetType: ReportTargetType;
  targetId: number;
  /** 무엇을 신고하는지 한 줄 (예: "이 스터디", "이 메시지") */
  targetLabel: string;
  onClose: () => void;
};

/** 신고 실패를 안내 문구로 바꾼다 (서버 코드 기준) */
function failureText(error: unknown): string {
  if (isApiError(error, "ALREADY_REPORTED")) {
    return "이미 신고한 대상이에요. 운영자가 확인하고 있어요.";
  }
  if (isApiError(error, "RATE_LIMITED")) {
    return "신고를 너무 많이 보냈어요. 잠시 뒤 다시 시도해 주세요.";
  }
  if (isApiError(error, "NOT_FOUND")) {
    return "신고할 대상을 찾을 수 없어요. 이미 삭제됐을 수 있어요.";
  }
  if (isApiError(error, "FORBIDDEN")) {
    return "이 대상은 신고할 수 없어요.";
  }
  if (isApiError(error, "VALIDATION_FAILED")) {
    return "내용을 다시 확인해 주세요. 내 메시지나 나 자신은 신고할 수 없어요.";
  }
  return "신고를 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.";
}

/**
 * 신고 입력 (PRD 4.11, API 7장, PLAN T-061). 사유를 골라야 보낼 수 있고, 기타는 상세 내용이 필요하다. 화면 안에 펼쳐지는
 * 폼이라 별도 모달이 없다. 보낸 뒤에는 접수 안내를 보여 준다.
 */
export function ReportForm({
  targetType,
  targetId,
  targetLabel,
  onClose,
}: Props) {
  const [reason, setReason] = useState<ReportReason | undefined>();
  const [detail, setDetail] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const detailMissing = reason === "OTHER" && detail.trim().length === 0;

  const submit = async () => {
    if (!reason || detailMissing) return;
    setPending(true);
    setError(null);
    try {
      await fileReport(targetType, targetId, reason, detail.trim());
      setSent(true);
    } catch (e) {
      setError(failureText(e));
    } finally {
      setPending(false);
    }
  };

  if (sent) {
    return (
      <div className={styles.done} role="status">
        <p>신고를 접수했어요. 운영자가 확인해요.</p>
        <div className={styles.actions}>
          <Button onClick={onClose}>닫기</Button>
        </div>
      </div>
    );
  }
  return (
    <form
      className={styles.form}
      aria-label={`${targetLabel} 신고`}
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <p className={styles.title}>{targetLabel}을 신고할까요?</p>
      <ChoiceChips
        legend="신고 사유"
        choices={REPORT_REASONS}
        value={reason}
        onChange={(value) => setReason(value as ReportReason)}
      />
      <TextAreaField
        label="상세 내용"
        optional={reason !== "OTHER"}
        rows={3}
        maxLength={REPORT_DETAIL_MAX}
        value={detail}
        onChange={setDetail}
        hint={reason === "OTHER" ? "기타는 내용을 적어 주세요." : undefined}
      />
      {error && (
        <p className={styles.error} role="alert">
          ! {error}
        </p>
      )}
      <div className={styles.actions}>
        <Button onClick={onClose} disabled={pending}>
          취소
        </Button>
        <Button
          type="submit"
          variant="primary"
          disabled={!reason || detailMissing}
          pending={pending}
          pendingLabel="보내는 중…"
        >
          신고하기
        </Button>
      </div>
    </form>
  );
}
