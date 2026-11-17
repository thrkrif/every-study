"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { BottomBar } from "@/components/BottomBar";
import { Button } from "@/components/Button";
import { ChoiceChips } from "@/components/ChoiceChips";
import { ProgressSteps } from "@/components/ProgressSteps";
import { TextField } from "@/components/TextField";
import { isApiError } from "@/lib/api/errors";
import { updateProfile, type Me } from "@/lib/api/me";
import { districtsOf } from "@/lib/api/meta";
import { ME_QUERY_KEY, useMetaOptions, useSession } from "@/lib/session";
import {
  NICKNAME_MAX,
  nicknameProblem,
  STEPS,
  stepOfField,
  type OnboardingStep,
} from "./rules";
import styles from "./OnboardingFlow.module.css";

const NO_DETAIL = "";

const SERVER_FIELD_MESSAGES: Record<OnboardingStep, string> = {
  nickname: "이 닉네임은 쓸 수 없어요. 다시 확인해 주세요.",
  job: "직군을 다시 골라 주세요.",
  region: "지역을 다시 골라 주세요.",
};

/**
 * 온보딩 (PRD 4.1, 5장 화면 2). 한 화면에 질문 하나: 닉네임 → 직군 → 활동 지역.
 * 로그인하지 않았으면 로그인 화면으로, 이미 마쳤으면 첫 화면으로 보낸다.
 */
export function OnboardingFlow() {
  const session = useSession();
  const router = useRouter();

  useEffect(() => {
    if (session.status === "anonymous") router.replace("/login");
    if (session.status === "member" && session.me.onboarded)
      router.replace("/");
  }, [session, router]);

  if (session.status === "error") {
    return (
      <main className={styles.page}>
        <p className={styles.notice} role="alert">
          서버에 연결하지 못했어요. 잠시 뒤 새로고침해 주세요.
        </p>
      </main>
    );
  }
  if (session.status !== "member" || session.me.onboarded) {
    return <main className={styles.page} aria-busy="true" />;
  }
  return <OnboardingForm />;
}

function OnboardingForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const meta = useMetaOptions();

  const [step, setStep] = useState<OnboardingStep>("nickname");
  const [nickname, setNickname] = useState("");
  const [nicknameTouched, setNicknameTouched] = useState(false);
  const [category, setCategory] = useState<string>();
  const [detail, setDetail] = useState(NO_DETAIL);
  const [district, setDistrict] = useState<string>();
  const [serverErrors, setServerErrors] = useState<
    Partial<Record<OnboardingStep, string>>
  >({});

  const districts = useMemo(
    () => (meta.data ? districtsOf(meta.data.regions) : []),
    [meta.data],
  );
  const categoryOption = meta.data?.jobs.find((job) => job.code === category);

  const save = useMutation({
    mutationFn: () =>
      updateProfile({
        nickname: nickname.trim(),
        jobCode: detail || category!,
        districtCode: district!,
      }),
    onSuccess: (me: Me) => {
      queryClient.setQueryData(ME_QUERY_KEY, me);
      router.replace("/");
    },
    onError: (error) => {
      if (isApiError(error, "NICKNAME_TAKEN")) {
        setServerErrors({
          nickname: "이미 쓰는 닉네임이에요. 다른 닉네임을 입력해 주세요.",
        });
        setStep("nickname");
        return;
      }
      if (isApiError(error, "VALIDATION_FAILED")) {
        const errors: Partial<Record<OnboardingStep, string>> = {};
        for (const fieldError of error.fieldErrors) {
          const target = stepOfField(fieldError.field);
          if (target) errors[target] = SERVER_FIELD_MESSAGES[target];
        }
        const first = STEPS.find((candidate) => errors[candidate]);
        if (first) {
          setServerErrors(errors);
          setStep(first);
        }
      }
    },
  });

  function clearServerError(target: OnboardingStep) {
    setServerErrors((errors) => ({ ...errors, [target]: undefined }));
  }

  const nicknameError =
    serverErrors.nickname ??
    (nicknameTouched ? nicknameProblem(nickname) : undefined);

  const blocker: string | undefined =
    step === "nickname"
      ? nicknameProblem(nickname)
      : step === "job"
        ? category
          ? undefined
          : "직군을 골라 주세요."
        : district
          ? undefined
          : "활동 지역을 골라 주세요.";

  const index = STEPS.indexOf(step);
  const isLast = index === STEPS.length - 1;
  const saveFailed =
    save.isError &&
    !isApiError(save.error, "NICKNAME_TAKEN") &&
    !isApiError(save.error, "VALIDATION_FAILED");

  function goNext() {
    if (blocker) {
      setNicknameTouched(true);
      return;
    }
    if (isLast) {
      save.mutate();
    } else {
      setStep(STEPS[index + 1]);
    }
  }

  return (
    <main className={styles.page}>
      <ProgressSteps current={index + 1} total={STEPS.length} />

      <form
        className={styles.form}
        onSubmit={(event) => {
          event.preventDefault();
          goNext();
        }}
        noValidate
      >
        {step === "nickname" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              어떻게 불러 드릴까요?
            </h1>
            <TextField
              label="닉네임"
              value={nickname}
              onChange={(value) => {
                setNickname(value);
                clearServerError("nickname");
              }}
              onBlur={() => setNicknameTouched(true)}
              maxLength={NICKNAME_MAX}
              hint="2~20자, 한글·영문·숫자·밑줄(_)"
              error={nicknameError}
              autoComplete="nickname"
              enterKeyHint="next"
            />
          </section>
        )}

        {step === "job" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              어떤 직군을 준비하고 있나요?
            </h1>
            <p className={styles.help}>
              스터디마다 신청할 수 있는 직군이 정해져 있어요. 나중에 바꿀 수
              있어요.
            </p>
            {meta.isPending && <p className={styles.help}>불러오는 중</p>}
            {meta.data && (
              <>
                <ChoiceChips
                  legend="직군"
                  choices={meta.data.jobs.map((job) => ({
                    value: job.code,
                    label: job.name,
                  }))}
                  value={category}
                  onChange={(value) => {
                    setCategory(value);
                    setDetail(NO_DETAIL);
                    clearServerError("job");
                  }}
                />
                {categoryOption && categoryOption.children.length > 0 && (
                  <ChoiceChips
                    legend="세부 직무 (선택)"
                    choices={[
                      { value: NO_DETAIL, label: "고르지 않음" },
                      ...categoryOption.children.map((child) => ({
                        value: child.code,
                        label: child.name,
                      })),
                    ]}
                    value={detail}
                    onChange={setDetail}
                  />
                )}
              </>
            )}
            {serverErrors.job && (
              <p className={styles.fieldError} role="alert">
                {serverErrors.job}
              </p>
            )}
          </section>
        )}

        {step === "region" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              주로 어디서 스터디하나요?
            </h1>
            <p className={styles.help}>
              다른 지역의 스터디도 찾아보고 신청할 수 있어요.
            </p>
            {meta.data && (
              <ChoiceChips
                legend="활동 지역"
                choices={districts.map((item) => ({
                  value: item.code,
                  label: item.label,
                }))}
                value={district}
                onChange={(value) => {
                  setDistrict(value);
                  clearServerError("region");
                }}
              />
            )}
            {serverErrors.region && (
              <p className={styles.fieldError} role="alert">
                {serverErrors.region}
              </p>
            )}
          </section>
        )}

        {meta.isError && step !== "nickname" && (
          <p className={styles.fieldError} role="alert">
            목록을 불러오지 못했어요. 잠시 뒤 새로고침해 주세요.
          </p>
        )}

        <BottomBar
          note={
            saveFailed
              ? "저장하지 못했어요. 잠시 뒤 다시 시도해 주세요."
              : step !== "nickname"
                ? blocker
                : undefined
          }
        >
          {index > 0 && (
            <Button
              variant="secondary"
              onClick={() => setStep(STEPS[index - 1])}
              disabled={save.isPending}
            >
              이전
            </Button>
          )}
          <Button
            type="submit"
            variant="primary"
            disabled={step !== "nickname" && blocker !== undefined}
            pending={save.isPending}
            pendingLabel="저장하는 중"
          >
            {isLast ? "시작하기" : "다음"}
          </Button>
        </BottomBar>
      </form>
    </main>
  );
}
