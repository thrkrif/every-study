"use client";

import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { BottomBar } from "@/components/BottomBar";
import { Button } from "@/components/Button";
import { CheckChips } from "@/components/CheckChips";
import { ChoiceChips } from "@/components/ChoiceChips";
import { PlacePicker } from "@/components/PlacePicker";
import { ProgressSteps } from "@/components/ProgressSteps";
import { Stepper } from "@/components/Stepper";
import { TextAreaField } from "@/components/TextAreaField";
import { TextField } from "@/components/TextField";
import { isApiError } from "@/lib/api/errors";
import { districtsOf } from "@/lib/api/meta";
import { createStudy } from "@/lib/api/studies";
import type { PlaceResult } from "@/lib/kakaoMap";
import { useMetaOptions, useSession } from "@/lib/session";
import {
  CREATE_STEPS,
  DESCRIPTION_MAX,
  TITLE_MAX,
  dateOptions,
  descriptionProblem,
  districtOfAddress,
  fieldTarget,
  formatClock,
  formatDuration,
  isPastStart,
  maxDuration,
  titleProblem,
  toStartAt,
  type CreateStep,
  type StudyRules,
} from "./rules";
import styles from "./CreateStudyFlow.module.css";

type Props = {
  /** 장소 검색. 테스트에서 바꿔 끼운다 */
  searchPlaces?: (keyword: string) => Promise<PlaceResult[]>;
  /** 지금 시각. 테스트에서 고정한다 */
  now?: () => Date;
};

/**
 * 스터디 만들기 (PRD 4.2, 5장 화면 5, DESIGN 6장 개설). 한 화면에 질문 하나, 마지막에 요약을 보고 만든다.
 * 온보딩을 마친 회원만 쓸 수 있다.
 */
export function CreateStudyFlow(props: Props) {
  const session = useSession();
  const router = useRouter();
  const meta = useMetaOptions();

  useEffect(() => {
    if (session.status === "anonymous") router.replace("/login");
    if (session.status === "member" && !session.me.onboarded)
      router.replace("/onboarding");
  }, [session, router]);

  if (session.status === "error" || meta.isError) {
    return (
      <main className={styles.page}>
        <p className={styles.notice} role="alert">
          서버에 연결하지 못했어요. 잠시 뒤 새로고침해 주세요.
        </p>
      </main>
    );
  }
  if (
    session.status !== "member" ||
    !session.me.onboarded ||
    meta.data === undefined
  ) {
    return <main className={styles.page} aria-busy="true" />;
  }
  return (
    <CreateStudyForm
      {...props}
      rules={meta.data.study}
      topics={meta.data.topics}
      jobs={meta.data.jobs}
      regions={meta.data.regions}
      myDistrict={session.me.districtCode ?? undefined}
    />
  );
}

type FormProps = Props & {
  rules: StudyRules;
  topics: { code: string; name: string }[];
  jobs: { code: string; name: string }[];
  regions: Parameters<typeof districtsOf>[0];
  myDistrict?: string;
};

type Errors = Partial<Record<string, string>>;

function CreateStudyForm({
  rules,
  topics,
  jobs,
  regions,
  searchPlaces,
  now = () => new Date(),
}: FormProps) {
  const router = useRouter();
  const districts = useMemo(() => districtsOf(regions), [regions]);
  const dates = useMemo(
    () => dateOptions(now(), rules.openableDays),
    // 화면을 연 날을 기준으로 한 번만 만든다
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rules.openableDays],
  );

  const [step, setStep] = useState<CreateStep>("about");
  const [touched, setTouched] = useState(false);
  const [serverErrors, setServerErrors] = useState<Errors>({});

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [topic, setTopic] = useState<string>();
  const [targetJobs, setTargetJobs] = useState<string[]>([]);
  const [date, setDate] = useState<string>();
  const [period, setPeriod] = useState<"AM" | "PM">("PM");
  const [hour12, setHour12] = useState<number>();
  const [minute, setMinute] = useState<number>(0);
  const [duration, setDuration] = useState(120);
  const [place, setPlace] = useState<PlaceResult>();
  const [capacity, setCapacity] = useState(
    Math.min(Math.max(4, rules.capacityMin), rules.capacityMax),
  );
  const [minRate, setMinRate] = useState<number | null>(null);
  const [allowNewcomer, setAllowNewcomer] = useState(true);

  const minuteOfDay =
    hour12 === undefined
      ? undefined
      : ((hour12 % 12) + (period === "PM" ? 12 : 0)) * 60 + minute;
  const durationMax =
    minuteOfDay === undefined ? 24 * 60 : maxDuration(minuteOfDay);
  const effectiveDuration = Math.min(duration, durationMax);
  const district = place
    ? districtOfAddress(place.address, districts)
    : undefined;

  const clear = (key: string) =>
    setServerErrors((errors) => ({ ...errors, [key]: undefined }));

  // 단계마다 다음으로 넘어가지 못하는 이유. 없으면 undefined
  function problemOf(target: CreateStep): Errors {
    switch (target) {
      case "about":
        return {
          title: titleProblem(title),
          description: descriptionProblem(description),
        };
      case "topic":
        return { topicCode: topic ? undefined : "주제를 골라 주세요." };
      case "schedule":
        if (!date) return { startAt: "날짜를 골라 주세요." };
        if (minuteOfDay === undefined)
          return { startAt: "시작 시각을 골라 주세요." };
        if (isPastStart(date, minuteOfDay, now()))
          return { startAt: "지금보다 뒤의 시각을 골라 주세요." };
        if (effectiveDuration < rules.durationMinMinutes)
          return {
            durationMin: `진행 시간은 ${formatDuration(rules.durationMinMinutes)} 이상이어야 하고, 자정 전에 끝나야 해요.`,
          };
        return {};
      case "place":
        if (!place) return { place: "장소를 골라 주세요." };
        if (!district)
          return {
            place: `지금은 ${districts.map((item) => item.label).join(", ")} 안의 장소만 고를 수 있어요.`,
          };
        return {};
      default:
        return {};
    }
  }

  const stepErrors = problemOf(step);
  const blocker = Object.values(stepErrors).find(Boolean);
  const errorOf = (key: string) =>
    serverErrors[key] ?? (touched ? stepErrors[key] : undefined);

  const save = useMutation({
    mutationFn: () =>
      createStudy({
        title: title.trim(),
        description: description.trim(),
        topicCode: topic!,
        districtCode: district!,
        targetJobCodes: targetJobs,
        place: {
          name: place!.name,
          address: place!.roadAddress || place!.address,
          latitude: place!.latitude,
          longitude: place!.longitude,
        },
        startAt: toStartAt(date!, minuteOfDay!),
        durationMin: effectiveDuration,
        capacity,
        minAttendanceRate: minRate,
        allowNewcomer,
      }),
    onSuccess: ({ id }) => router.push(`/studies/${id}`),
    onError: (error) => {
      if (isApiError(error, "ONBOARDING_REQUIRED")) {
        router.replace("/onboarding");
        return;
      }
      if (isApiError(error, "UNAUTHENTICATED")) {
        router.replace("/login");
        return;
      }
      if (isApiError(error, "VALIDATION_FAILED")) {
        const errors: Errors = {};
        let first: CreateStep | undefined;
        for (const fieldError of error.fieldErrors) {
          const target = fieldTarget(fieldError.field);
          if (!target) continue;
          errors[target.key] ??= fieldError.message;
          if (
            first === undefined ||
            CREATE_STEPS.indexOf(target.step) < CREATE_STEPS.indexOf(first)
          )
            first = target.step;
        }
        if (first) {
          setServerErrors(errors);
          setStep(first);
        }
      }
    },
  });

  const index = CREATE_STEPS.indexOf(step);
  const isLast = step === "review";
  const saveFailed =
    save.isError &&
    !isApiError(save.error, "VALIDATION_FAILED") &&
    !isApiError(save.error, "ONBOARDING_REQUIRED") &&
    !isApiError(save.error, "UNAUTHENTICATED");

  function goNext() {
    if (blocker) {
      setTouched(true);
      return;
    }
    setTouched(false);
    if (isLast) save.mutate();
    else setStep(CREATE_STEPS[index + 1]);
  }

  const topicName = topics.find((item) => item.code === topic)?.name;
  const districtLabel = districts.find((item) => item.code === district)?.label;
  const minuteChoices = Array.from(
    { length: 60 / rules.startMinuteUnit },
    (_, i) => i * rules.startMinuteUnit,
  );

  return (
    <main className={styles.page}>
      <ProgressSteps current={index + 1} total={CREATE_STEPS.length} />
      <form
        className={styles.form}
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          goNext();
        }}
      >
        {step === "about" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              어떤 스터디인가요?
            </h1>
            <TextField
              label="제목"
              value={title}
              onChange={(value) => {
                setTitle(value);
                clear("title");
              }}
              maxLength={TITLE_MAX}
              error={errorOf("title")}
              placeholder="예: 백엔드 면접 질문 같이 풀어요"
            />
            <TextAreaField
              label="소개"
              optional
              value={description}
              onChange={(value) => {
                setDescription(value);
                clear("description");
              }}
              maxLength={DESCRIPTION_MAX}
              hint="무엇을 어떻게 하는지, 준비물이 있는지 적어 주세요."
              error={errorOf("description")}
            />
          </section>
        )}

        {step === "topic" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              주제와 함께할 직군을 골라 주세요
            </h1>
            <ChoiceChips
              legend="주제"
              choices={topics.map((item) => ({
                value: item.code,
                label: item.name,
              }))}
              value={topic}
              onChange={(value) => {
                setTopic(value);
                clear("topicCode");
              }}
            />
            {errorOf("topicCode") && (
              <p className={styles.fieldError} role="alert">
                {errorOf("topicCode")}
              </p>
            )}
            <CheckChips
              legend="희망 직군 (선택)"
              choices={jobs.map((item) => ({
                value: item.code,
                label: item.name,
              }))}
              values={targetJobs}
              onChange={(values) => {
                setTargetJobs(values);
                clear("targetJobCodes");
              }}
            />
            <p className={styles.help}>
              {targetJobs.length === 0
                ? "고르지 않으면 직군과 상관없이 누구나 신청할 수 있어요."
                : "고른 직군의 회원만 신청할 수 있어요."}
            </p>
            {errorOf("targetJobCodes") && (
              <p className={styles.fieldError} role="alert">
                {errorOf("targetJobCodes")}
              </p>
            )}
          </section>
        )}

        {step === "schedule" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              언제 만날까요?
            </h1>
            <p className={styles.band}>
              참여자가 한 명이라도 생기면 일정과 장소는 바꿀 수 없어요.
            </p>
            {date && minuteOfDay !== undefined && (
              <p className={`${styles.picked} num`} aria-live="polite">
                {pickedDateLabel(dates, date)} · {formatClock(minuteOfDay)}
              </p>
            )}
            <ChoiceChips
              legend="날짜"
              choices={dates.map((item) => ({
                value: item.date,
                label: `${item.day}일 ${item.weekday}${item.isToday ? " · 오늘" : ""}`,
              }))}
              value={date}
              onChange={(value) => {
                setDate(value);
                clear("startAt");
              }}
            />
            <ChoiceChips
              legend="오전/오후"
              choices={[
                { value: "AM", label: "오전" },
                { value: "PM", label: "오후" },
              ]}
              value={period}
              onChange={(value) => setPeriod(value as "AM" | "PM")}
            />
            <ChoiceChips
              legend="시"
              choices={Array.from({ length: 12 }, (_, i) => {
                const hour = i === 0 ? 12 : i;
                return { value: String(hour), label: `${hour}시` };
              })}
              value={hour12 === undefined ? undefined : String(hour12)}
              onChange={(value) => {
                setHour12(Number(value));
                clear("startAt");
              }}
            />
            <ChoiceChips
              legend="분"
              choices={minuteChoices.map((value) => ({
                value: String(value),
                label: `${String(value).padStart(2, "0")}분`,
              }))}
              value={String(minute)}
              onChange={(value) => setMinute(Number(value))}
            />
            {errorOf("startAt") && (
              <p className={styles.fieldError} role="alert">
                {errorOf("startAt")}
              </p>
            )}
            <Stepper
              label="진행 시간"
              value={effectiveDuration}
              min={rules.durationMinMinutes}
              max={Math.max(rules.durationMinMinutes, durationMax)}
              step={rules.durationMinuteUnit}
              format={formatDuration}
              onChange={(value) => {
                setDuration(value);
                clear("durationMin");
              }}
            />
            {errorOf("durationMin") && (
              <p className={styles.fieldError} role="alert">
                {errorOf("durationMin")}
              </p>
            )}
          </section>
        )}

        {step === "place" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              어디서 만날까요?
            </h1>
            <PlacePicker
              value={place}
              onChange={(value) => {
                setPlace(value);
                clear("place");
              }}
              search={searchPlaces}
              error={errorOf("place")}
            />
            {districtLabel && (
              <p className={styles.help}>활동 지역: {districtLabel}</p>
            )}
          </section>
        )}

        {step === "people" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              몇 명이 함께할까요?
            </h1>
            <Stepper
              label="정원 (나를 포함)"
              value={capacity}
              min={rules.capacityMin}
              max={rules.capacityMax}
              format={(value) => `${value}명`}
              onChange={(value) => {
                setCapacity(value);
                clear("capacity");
              }}
            />
            {errorOf("capacity") && (
              <p className={styles.fieldError} role="alert">
                {errorOf("capacity")}
              </p>
            )}
            <ChoiceChips
              legend="최소 출석률"
              choices={[
                { value: "", label: "기준 없음" },
                ...rules.minAttendanceRates.map((rate) => ({
                  value: String(rate),
                  label: `${rate}% 이상`,
                })),
              ]}
              value={minRate === null ? "" : String(minRate)}
              onChange={(value) => {
                setMinRate(value === "" ? null : Number(value));
                clear("minAttendanceRate");
              }}
            />
            {errorOf("minAttendanceRate") && (
              <p className={styles.fieldError} role="alert">
                {errorOf("minAttendanceRate")}
              </p>
            )}
            <ChoiceChips
              legend="출석 기록이 없는 신규 회원"
              choices={[
                { value: "yes", label: "받아요" },
                { value: "no", label: "받지 않아요" },
              ]}
              value={allowNewcomer ? "yes" : "no"}
              onChange={(value) => setAllowNewcomer(value === "yes")}
            />
          </section>
        )}

        {step === "review" && (
          <section className={styles.step} aria-labelledby="step-title">
            <h1 id="step-title" className={styles.question}>
              이대로 만들까요?
            </h1>
            <dl className={styles.summary}>
              <SummaryRow term="제목" value={title.trim()} />
              <SummaryRow term="주제" value={topicName ?? ""} />
              <SummaryRow
                term="희망 직군"
                value={
                  targetJobs.length === 0
                    ? "직군 무관"
                    : jobs
                        .filter((item) => targetJobs.includes(item.code))
                        .map((item) => item.name)
                        .join(", ")
                }
              />
              <SummaryRow
                term="일시"
                value={`${pickedDateLabel(dates, date!)} ${formatClock(minuteOfDay!)} · ${formatDuration(effectiveDuration)}`}
              />
              <SummaryRow
                term="장소"
                value={`${place!.name} (${districtLabel})`}
              />
              <SummaryRow term="정원" value={`${capacity}명`} />
              <SummaryRow
                term="입장 기준"
                value={`${minRate === null ? "출석률 기준 없음" : `출석률 ${minRate}% 이상`} · 신규 ${allowNewcomer ? "받음" : "받지 않음"}`}
              />
            </dl>
            <p className={styles.band}>
              참여자가 생기면 일정과 장소는 바꿀 수 없어요. 제목·소개·정원은
              언제든 바꿀 수 있어요.
            </p>
          </section>
        )}

        <BottomBar
          note={
            saveFailed
              ? "만들지 못했어요. 잠시 뒤 다시 시도해 주세요."
              : step !== "about" && touched
                ? blocker
                : undefined
          }
        >
          {index > 0 && (
            <Button
              variant="secondary"
              onClick={() => {
                setTouched(false);
                setStep(CREATE_STEPS[index - 1]);
              }}
              disabled={save.isPending}
            >
              이전
            </Button>
          )}
          <Button
            type="submit"
            variant="primary"
            pending={save.isPending}
            pendingLabel="만드는 중"
          >
            {isLast ? "만들기" : "다음"}
          </Button>
        </BottomBar>
      </form>
    </main>
  );
}

function SummaryRow({ term, value }: { term: string; value: string }) {
  return (
    <div className={styles.summaryRow}>
      <dt>{term}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function pickedDateLabel(
  dates: ReturnType<typeof dateOptions>,
  date: string,
): string {
  const option = dates.find((item) => item.date === date);
  const [, month, day] = date.split("-").map(Number);
  return `${month}월 ${day}일 (${option?.weekday ?? ""})`;
}
