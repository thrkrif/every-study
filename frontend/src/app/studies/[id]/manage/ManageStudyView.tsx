"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import { ChoiceChips } from "@/components/ChoiceChips";
import { PlacePicker } from "@/components/PlacePicker";
import { Stepper } from "@/components/Stepper";
import { TextAreaField } from "@/components/TextAreaField";
import { TextField } from "@/components/TextField";
import { isApiError } from "@/lib/api/errors";
import {
  changeStudyCapacity,
  fetchStudy,
  updateStudy,
  type StudyDetail,
} from "@/lib/api/studies";
import type { PlaceResult } from "@/lib/kakaoMap";
import { useMetaOptions, useSession } from "@/lib/session";
import { manageErrorText } from "@/lib/studyActions";
import { formatLongWhen, nicknameOf } from "@/lib/studyFormat";
import {
  DESCRIPTION_MAX,
  TITLE_MAX,
  dateOptions,
  descriptionProblem,
  formatClock,
  formatDuration,
  isPastStart,
  maxDuration,
  titleProblem,
  toStartAt,
  type StudyRules,
} from "../../new/rules";
import styles from "./ManageStudyView.module.css";

type Props = {
  /** 주소의 id. 숫자가 아니면 없는 스터디로 본다 */
  id: string;
  /** 장소 검색. 테스트에서 바꿔 끼운다 */
  searchPlaces?: (keyword: string) => Promise<PlaceResult[]>;
  /** 지금 시각. 테스트에서 고정한다 */
  now?: () => Date;
};

/**
 * 내 스터디 관리 (PRD 5장 화면 6, PLAN T-026). 주최자만 쓴다. 제목·소개와 정원은 언제든 바꾸고, 일정·장소는
 * 참여자가 없고 시작 전일 때만 바꾼다(`scheduleEditable`). 참여자 목록과 대기 인원도 여기서 본다.
 */
export function ManageStudyView({ id, searchPlaces, now }: Props) {
  const studyId = /^\d+$/.test(id) ? Number(id) : undefined;
  const session = useSession();
  const meta = useMetaOptions();
  const study = useQuery({
    queryKey: ["study", studyId],
    queryFn: ({ signal }) => fetchStudy(studyId!, signal),
    enabled: studyId !== undefined && session.status === "member",
    retry: (count, error) => !isApiError(error, "NOT_FOUND") && count < 2,
  });

  if (studyId === undefined || isApiError(study.error, "NOT_FOUND")) {
    return <Message text="스터디를 찾을 수 없어요." />;
  }
  if (session.status === "anonymous") {
    return (
      <Message
        text="로그인한 주최자만 관리할 수 있어요."
        href="/login"
        cta="로그인"
      />
    );
  }
  if (study.isError || session.status === "error") {
    return (
      <Message text="스터디를 불러오지 못했어요. 잠시 뒤 새로고침해 주세요." />
    );
  }
  if (!study.data || !meta.data) {
    return (
      <main className={styles.page} aria-busy="true">
        <p className={styles.sub}>불러오는 중…</p>
      </main>
    );
  }
  const data = study.data;
  if (data.myState !== "HOST" || data.status !== "ACTIVE") {
    return (
      <Message
        text={
          data.myState !== "HOST"
            ? "주최자만 관리할 수 있어요."
            : "끝났거나 열리지 않은 스터디는 바꿀 수 없어요."
        }
        href={`/studies/${data.id}`}
        cta="스터디 보기"
      />
    );
  }
  return (
    <ManageForm
      study={data}
      rules={meta.data.study}
      searchPlaces={searchPlaces}
      now={now}
    />
  );
}

function ManageForm({
  study,
  rules,
  searchPlaces,
  now = () => new Date(),
}: {
  study: StudyDetail;
  rules: StudyRules;
  searchPlaces?: Props["searchPlaces"];
  now?: () => Date;
}) {
  const queryClient = useQueryClient();
  const refresh = (data: StudyDetail) => {
    queryClient.setQueryData(["study", study.id], data);
    void queryClient.invalidateQueries({ queryKey: ["studies"] });
    void queryClient.invalidateQueries({ queryKey: ["my-studies"] });
  };

  return (
    <main className={styles.page}>
      <header className={styles.head}>
        <Link href={`/studies/${study.id}`} className={styles.back}>
          ← 스터디로 돌아가기
        </Link>
        <h1 className={styles.title}>스터디 관리</h1>
        <p className={styles.sub}>{study.title}</p>
      </header>

      <TextSection study={study} onSaved={refresh} />
      <ScheduleSection
        study={study}
        rules={rules}
        searchPlaces={searchPlaces}
        now={now}
        onSaved={refresh}
      />
      <CapacitySection study={study} rules={rules} onSaved={refresh} />

      <Section title={`대기 ${study.waitingCount ?? 0}명`}>
        <p className={styles.sub}>
          {(study.waitingCount ?? 0) > 0
            ? "정원을 늘리면 대기 중인 분들에게 자리가 났다고 알려요."
            : "대기 중인 분이 없어요."}
        </p>
      </Section>

      <Section title={`참여자 ${study.memberCount}명`}>
        <ul className={styles.members}>
          {study.members.map((member) => (
            <li key={member.userId} className={styles.member}>
              <span>
                {nicknameOf(member.nickname)}
                {member.userId === study.host.userId && (
                  <span className={styles.hostMark}>주최</span>
                )}
              </span>
            </li>
          ))}
        </ul>
      </Section>
    </main>
  );
}

type Saved = { text: string; tone: "ok" | "error" } | null;

function useSave<T>(
  memberCount: number,
  request: (input: T) => Promise<StudyDetail>,
  onSaved: (data: StudyDetail) => void,
  okText: string,
) {
  const [saved, setSaved] = useState<Saved>(null);
  const mutation = useMutation({
    mutationFn: request,
    onSuccess: (data) => {
      onSaved(data);
      setSaved({ text: okText, tone: "ok" });
    },
    onError: (error) =>
      setSaved({ text: manageErrorText(error, memberCount), tone: "error" }),
  });
  return { saved, setSaved, mutation };
}

function ResultLine({ saved }: { saved: Saved }) {
  if (!saved) return null;
  return (
    <p
      role={saved.tone === "error" ? "alert" : "status"}
      className={styles.result}
      data-tone={saved.tone}
    >
      {saved.text}
    </p>
  );
}

/** 제목·소개: 진행 중이면 언제든 바꿀 수 있다 */
function TextSection({
  study,
  onSaved,
}: {
  study: StudyDetail;
  onSaved: (data: StudyDetail) => void;
}) {
  const [title, setTitle] = useState(study.title);
  const [description, setDescription] = useState(study.description);
  const [touched, setTouched] = useState(false);
  const { saved, setSaved, mutation } = useSave(
    study.memberCount,
    (input: { title?: string; description?: string }) =>
      updateStudy(study.id, input),
    onSaved,
    "저장했어요.",
  );
  const titleError = touched ? titleProblem(title) : undefined;
  const descriptionError = touched
    ? descriptionProblem(description)
    : undefined;
  const changed =
    title.trim() !== study.title || description.trim() !== study.description;

  return (
    <Section title="기본 정보">
      <TextField
        label="제목"
        value={title}
        onChange={(value) => {
          setTitle(value);
          setSaved(null);
        }}
        maxLength={TITLE_MAX}
        error={titleError}
      />
      <TextAreaField
        label="소개"
        optional
        value={description}
        onChange={(value) => {
          setDescription(value);
          setSaved(null);
        }}
        maxLength={DESCRIPTION_MAX}
        error={descriptionError}
      />
      <ResultLine saved={saved} />
      <Button
        variant="primary"
        disabled={!changed}
        pending={mutation.isPending}
        pendingLabel="저장하는 중…"
        onClick={() => {
          setTouched(true);
          if (titleProblem(title) || descriptionProblem(description)) return;
          mutation.mutate({
            ...(title.trim() !== study.title && { title: title.trim() }),
            ...(description.trim() !== study.description && {
              description: description.trim(),
            }),
          });
        }}
      >
        저장
      </Button>
    </Section>
  );
}

/** KST 날짜·시각을 화면 입력값(날짜, 오전/오후, 시, 분)으로 나눈다 */
function startParts(startAt: string) {
  const kst = new Date(new Date(startAt).getTime() + 9 * 60 * 60 * 1000);
  const hour = kst.getUTCHours();
  return {
    date: kst.toISOString().slice(0, 10),
    period: hour < 12 ? ("AM" as const) : ("PM" as const),
    hour12: hour % 12 === 0 ? 12 : hour % 12,
    minute: kst.getUTCMinutes(),
  };
}

/**
 * 일정·장소. 참여자가 있거나 시작했으면 잠그고 새 스터디를 안내한다 (PRD 4.2). 서버도 같은 규칙으로 막으므로
 * (`SCHEDULE_LOCKED`) 화면이 오래된 상태여도 안전하다.
 */
function ScheduleSection({
  study,
  rules,
  searchPlaces,
  now,
  onSaved,
}: {
  study: StudyDetail;
  rules: StudyRules;
  searchPlaces?: Props["searchPlaces"];
  now: () => Date;
  onSaved: (data: StudyDetail) => void;
}) {
  const initial = useMemo(() => startParts(study.startAt), [study.startAt]);
  const dates = useMemo(
    () => dateOptions(now(), rules.openableDays),
    // 화면을 연 날을 기준으로 한 번만 만든다
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rules.openableDays],
  );
  const [date, setDate] = useState(initial.date);
  const [period, setPeriod] = useState<"AM" | "PM">(initial.period);
  const [hour12, setHour12] = useState(initial.hour12);
  const [minute, setMinute] = useState(initial.minute);
  const [duration, setDuration] = useState(study.durationMin);
  // 지금 장소를 처음 값으로 둔다. 새로 고르기 전까지는 같은 값을 다시 보낸다
  const [place, setPlace] = useState<PlaceResult>({
    id: "current",
    name: study.place.name,
    address: study.place.address,
    roadAddress: "",
    latitude: study.place.latitude,
    longitude: study.place.longitude,
  });
  const [problem, setProblem] = useState<string>();
  const { saved, setSaved, mutation } = useSave(
    study.memberCount,
    (input: Parameters<typeof updateStudy>[1]) => updateStudy(study.id, input),
    onSaved,
    "일정과 장소를 저장했어요.",
  );

  if (!study.scheduleEditable) {
    return (
      <Section title="일정·장소">
        <p className={`${styles.locked} num`}>
          {formatLongWhen(study.startAt)} · {formatDuration(study.durationMin)}
        </p>
        <p className={styles.sub}>{study.place.name}</p>
        <p className={styles.band} role="note">
          참여자가 있거나 이미 시작해서 일정·장소는 바꿀 수 없어요. 일정을
          바꾸려면 주최를 넘기고 새 스터디를 만들어 주세요.
        </p>
        <Link
          href="/studies/new"
          className={buttonStyles.button}
          data-variant="secondary"
        >
          새 스터디 만들기
        </Link>
      </Section>
    );
  }

  const minuteOfDay =
    ((hour12 % 12) + (period === "PM" ? 12 : 0)) * 60 + minute;
  const durationMax = maxDuration(minuteOfDay);
  const effectiveDuration = Math.min(duration, durationMax);
  const minuteChoices = Array.from(
    { length: 60 / rules.startMinuteUnit },
    (_, index) => index * rules.startMinuteUnit,
  );

  const save = () => {
    if (isPastStart(date, minuteOfDay, now())) {
      setProblem("지금보다 이후 시각을 골라 주세요.");
      return;
    }
    setProblem(undefined);
    mutation.mutate({
      startAt: toStartAt(date, minuteOfDay),
      durationMin: effectiveDuration,
      place: {
        name: place.name,
        address: place.roadAddress || place.address,
        latitude: place.latitude,
        longitude: place.longitude,
      },
    });
  };
  const edit =
    <T,>(set: (value: T) => void) =>
    (value: T) => {
      set(value);
      setSaved(null);
      setProblem(undefined);
    };

  return (
    <Section title="일정·장소">
      <p className={styles.band}>
        참여자가 한 명이라도 생기면 일정과 장소는 바꿀 수 없어요.
      </p>
      <ChoiceChips
        legend="날짜"
        choices={dates.map((item) => ({
          value: item.date,
          label: `${item.day}일 ${item.weekday}${item.isToday ? " · 오늘" : ""}`,
        }))}
        value={date}
        onChange={edit(setDate)}
      />
      <ChoiceChips
        legend="오전/오후"
        choices={[
          { value: "AM", label: "오전" },
          { value: "PM", label: "오후" },
        ]}
        value={period}
        onChange={edit((value: string) => setPeriod(value as "AM" | "PM"))}
      />
      <ChoiceChips
        legend="시"
        choices={Array.from({ length: 12 }, (_, i) => {
          const hour = i === 0 ? 12 : i;
          return { value: String(hour), label: `${hour}시` };
        })}
        value={String(hour12)}
        onChange={edit((value: string) => setHour12(Number(value)))}
      />
      <ChoiceChips
        legend="분"
        choices={minuteChoices.map((value) => ({
          value: String(value),
          label: `${String(value).padStart(2, "0")}분`,
        }))}
        value={String(minute)}
        onChange={edit((value: string) => setMinute(Number(value)))}
      />
      <p className={`${styles.picked} num`} aria-live="polite">
        {formatClock(minuteOfDay)}
      </p>
      <Stepper
        label="진행 시간"
        value={effectiveDuration}
        min={rules.durationMinMinutes}
        max={Math.max(rules.durationMinMinutes, durationMax)}
        step={rules.durationMinuteUnit}
        format={formatDuration}
        onChange={edit(setDuration)}
      />
      <PlacePicker
        value={place}
        onChange={edit(setPlace)}
        search={searchPlaces}
      />
      {problem && (
        <p className={styles.result} role="alert" data-tone="error">
          {problem}
        </p>
      )}
      <ResultLine saved={saved} />
      <Button
        variant="primary"
        pending={mutation.isPending}
        pendingLabel="저장하는 중…"
        onClick={save}
      >
        일정·장소 저장
      </Button>
    </Section>
  );
}

/** 정원: 늘리면 대기자에게 알림이 가고, 참여자 수보다 줄일 수 없다 */
function CapacitySection({
  study,
  rules,
  onSaved,
}: {
  study: StudyDetail;
  rules: StudyRules;
  onSaved: (data: StudyDetail) => void;
}) {
  const [capacity, setCapacity] = useState(study.capacity);
  const { saved, setSaved, mutation } = useSave(
    study.memberCount,
    (value: number) => changeStudyCapacity(study.id, value),
    onSaved,
    "정원을 바꿨어요.",
  );
  return (
    <Section title="정원">
      <Stepper
        label="정원 (나를 포함)"
        value={capacity}
        min={rules.capacityMin}
        max={rules.capacityMax}
        format={(value) => `${value}명`}
        onChange={(value) => {
          setCapacity(value);
          setSaved(null);
        }}
      />
      <p className={styles.sub}>
        지금 참여자는 {study.memberCount}명이에요. 그보다 적게 줄일 수 없어요.
      </p>
      <ResultLine saved={saved} />
      <Button
        variant="primary"
        disabled={capacity === study.capacity}
        pending={mutation.isPending}
        pendingLabel="바꾸는 중…"
        onClick={() => mutation.mutate(capacity)}
      >
        정원 바꾸기
      </Button>
    </Section>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>{title}</h2>
      {children}
    </section>
  );
}

function Message({
  text,
  href = "/",
  cta = "목록으로",
}: {
  text: string;
  href?: string;
  cta?: string;
}) {
  return (
    <main className={styles.page}>
      <div className={styles.message}>
        <p>{text}</p>
        <Link
          href={href}
          className={buttonStyles.button}
          data-variant="secondary"
        >
          {cta}
        </Link>
      </div>
    </main>
  );
}
