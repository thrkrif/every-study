"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import { ChoiceChips } from "@/components/ChoiceChips";
import { TextField } from "@/components/TextField";
import { isApiError } from "@/lib/api/errors";
import { updateProfile, type Me } from "@/lib/api/me";
import { districtsOf, type CodeOption } from "@/lib/api/meta";
import { ME_QUERY_KEY, useMetaOptions, useSession } from "@/lib/session";
import { NICKNAME_MAX, nicknameProblem } from "../../onboarding/rules";
import styles from "./EditProfileView.module.css";

const NO_DETAIL = "";

/** 저장된 직군 코드(대분류 또는 세부 직무)를 화면의 두 칸(대분류, 세부 직무)으로 나눈다 */
export function splitJob(
  jobs: readonly CodeOption[],
  jobCode: string | null,
): { category?: string; detail: string } {
  if (!jobCode) return { detail: NO_DETAIL };
  for (const job of jobs) {
    if (job.code === jobCode) return { category: job.code, detail: NO_DETAIL };
    if (job.children.some((child) => child.code === jobCode))
      return { category: job.code, detail: jobCode };
  }
  return { detail: NO_DETAIL };
}

/**
 * 내 정보 수정 (PRD 4.1·4.10, API 3장 `PUT /me/profile`). 온보딩에서 정한 닉네임·직군·활동 지역을 한 화면에서 바꾼다.
 * 온보딩과 같은 경로·규칙이고 세 값을 모두 보낸다. 저장하면 마이페이지로 돌아간다.
 */
export function EditProfileView() {
  const session = useSession();
  const router = useRouter();

  useEffect(() => {
    if (session.status === "anonymous") router.replace("/login");
    if (session.status === "member" && !session.me.onboarded)
      router.replace("/onboarding");
  }, [session, router]);

  if (session.status === "error") {
    return (
      <main className={styles.page}>
        <p className={styles.failure} role="alert">
          내 정보를 불러오지 못했어요.
        </p>
      </main>
    );
  }
  if (session.status !== "member" || !session.me.onboarded) {
    return <main className={styles.page} aria-busy="true" />;
  }
  return <EditProfileForm me={session.me} />;
}

function EditProfileForm({ me }: { me: Me }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const meta = useMetaOptions();

  const [nickname, setNickname] = useState(me.nickname ?? "");
  const [nicknameError, setNicknameError] = useState<string>();
  // 직군은 목록을 받은 뒤에야 대분류·세부 직무로 나눌 수 있다. 고르기 전까지는 저장된 값에서 꺼낸다
  const [picked, setPicked] = useState<{ category?: string; detail: string }>();
  const [district, setDistrict] = useState(me.districtCode ?? undefined);
  const [fieldErrors, setFieldErrors] = useState<{
    job?: string;
    region?: string;
  }>({});

  const job =
    picked ??
    (meta.data ? splitJob(meta.data.jobs, me.jobCode) : { detail: NO_DETAIL });
  const categoryOption = meta.data?.jobs.find(
    (option) => option.code === job.category,
  );
  const districts = useMemo(
    () => (meta.data ? districtsOf(meta.data.regions) : []),
    [meta.data],
  );

  const save = useMutation({
    mutationFn: () =>
      updateProfile({
        nickname: nickname.trim(),
        jobCode: job.detail || job.category!,
        districtCode: district!,
      }),
    onSuccess: (saved: Me) => {
      queryClient.setQueryData(ME_QUERY_KEY, saved);
      void queryClient.invalidateQueries({ queryKey: ["public-profile"] });
      router.push("/me");
    },
    onError: (error) => {
      if (isApiError(error, "NICKNAME_TAKEN")) {
        setNicknameError(
          "이미 쓰는 닉네임이에요. 다른 닉네임을 입력해 주세요.",
        );
        return;
      }
      if (isApiError(error, "VALIDATION_FAILED")) {
        const errors: typeof fieldErrors = {};
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === "nickname")
            setNicknameError("이 닉네임은 쓸 수 없어요. 다시 확인해 주세요.");
          if (fieldError.field === "jobCode")
            errors.job = "직군을 다시 골라 주세요.";
          if (fieldError.field === "districtCode")
            errors.region = "지역을 다시 골라 주세요.";
        }
        setFieldErrors(errors);
      }
    },
  });

  const saveFailed =
    save.isError &&
    !isApiError(save.error, "NICKNAME_TAKEN") &&
    !isApiError(save.error, "VALIDATION_FAILED");

  function submit() {
    const problem = nicknameProblem(nickname);
    if (problem) {
      setNicknameError(problem);
      return;
    }
    if (!job.category || !district) return;
    save.mutate();
  }

  return (
    <main className={styles.page}>
      <form
        className={styles.form}
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
        noValidate
      >
        <h1 className={styles.title}>내 정보 수정</h1>

        <TextField
          label="닉네임"
          value={nickname}
          onChange={(value) => {
            setNickname(value);
            setNicknameError(undefined);
          }}
          onBlur={() => setNicknameError(nicknameProblem(nickname))}
          maxLength={NICKNAME_MAX}
          hint="2~20자, 한글·영문·숫자·밑줄(_)"
          error={nicknameError}
          autoComplete="nickname"
        />

        {meta.isPending && <p className={styles.note}>불러오는 중…</p>}
        {meta.isError && (
          <p className={styles.failure} role="alert">
            목록을 불러오지 못했어요. 잠시 뒤 새로고침해 주세요.
          </p>
        )}
        {meta.data && (
          <>
            <ChoiceChips
              legend="직군"
              choices={meta.data.jobs.map((option) => ({
                value: option.code,
                label: option.name,
              }))}
              value={job.category}
              onChange={(value) => {
                setPicked({ category: value, detail: NO_DETAIL });
                setFieldErrors((errors) => ({ ...errors, job: undefined }));
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
                value={job.detail}
                onChange={(value) =>
                  setPicked({ category: job.category, detail: value })
                }
              />
            )}
            {fieldErrors.job && (
              <p className={styles.fieldError} role="alert">
                {fieldErrors.job}
              </p>
            )}

            <ChoiceChips
              legend="활동 지역"
              choices={districts.map((item) => ({
                value: item.code,
                label: item.label,
              }))}
              value={district}
              onChange={(value) => {
                setDistrict(value);
                setFieldErrors((errors) => ({ ...errors, region: undefined }));
              }}
            />
            {fieldErrors.region && (
              <p className={styles.fieldError} role="alert">
                {fieldErrors.region}
              </p>
            )}
          </>
        )}

        {saveFailed && (
          <p className={styles.failure} role="alert">
            저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.
          </p>
        )}

        <div className={styles.actions}>
          <Link
            href="/me"
            className={buttonStyles.button}
            data-variant="secondary"
          >
            취소
          </Link>
          <Button
            type="submit"
            variant="primary"
            disabled={!meta.data}
            pending={save.isPending}
            pendingLabel="저장하는 중"
          >
            저장
          </Button>
        </div>
      </form>
    </main>
  );
}
