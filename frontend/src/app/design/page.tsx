import { notFound } from "next/navigation";
import { AttendanceRow } from "@/components/AttendanceRow";
import { Button } from "@/components/Button";
import { SeatRow } from "@/components/SeatRow";
import { StatusChip } from "@/components/StatusChip";
import styles from "./page.module.css";

// 디자인 토큰과 공통 컴포넌트를 한 화면에서 확인하는 개발용 화면이다 (DESIGN 9장).
// 운영 빌드에서는 열리지 않는다. 예시 데이터는 모두 가짜다.

const COLORS = [
  "ink",
  "ink-sub",
  "paper",
  "surface",
  "line",
  "primary",
  "primary-tint",
  "ok",
  "ok-bg",
  "late",
  "late-bg",
  "bad",
  "bad-bg",
  "neutral-bg",
  "disabled",
] as const;

const TEXT_STYLES = [
  ["time", "내일 오전 10:00"],
  ["title", "면접 스터디 같이 준비해요"],
  ["heading", "참여자 3명"],
  ["body", "모의 면접을 서로 해 주고 피드백을 나눠요."],
  ["label", "경기"],
  ["caption", "신청은 시작 10분 전까지"],
] as const;

export default function DesignPreviewPage() {
  if (process.env.NODE_ENV === "production") {
    notFound();
  }
  return (
    <main className={styles.page}>
      <h1 className={styles.pageTitle}>디자인 확인</h1>

      <section className={styles.section} aria-labelledby="list-card">
        <h2 id="list-card" className={styles.sectionTitle}>
          목록 카드
        </h2>
        <a href="#" className={styles.card}>
          <span className={styles.cardTop}>
            <StatusChip phase="RECRUITING" />
            <span className={styles.criteria}>출석률 80% 이상</span>
          </span>
          <span className={`${styles.cardTime} num`}>내일 오전 10:00</span>
          <span className={styles.cardTitle}>
            백엔드 면접 질문 같이 풀어 봐요
          </span>
          <span className={styles.meta}>경기 · 2시간</span>
          <SeatRow capacity={6} memberCount={3} />
          <span className={styles.host}>
            <span>주최 공부하는곰</span>
            <AttendanceRow
              rate={92}
              records={[
                "PRESENT",
                "PRESENT",
                "PRESENT",
                "LATE",
                "PRESENT",
                "PRESENT",
              ]}
            />
          </span>
        </a>
        <a href="#" className={styles.card}>
          <span className={styles.cardTop}>
            <StatusChip phase="CLOSED" />
          </span>
          <span className={`${styles.cardTime} num`}>
            11월 22일 토 오후 2:00
          </span>
          <span className={styles.cardTitle}>공무원 한국사 기출 회독</span>
          <span className={styles.meta}>서울 · 3시간</span>
          <SeatRow capacity={4} memberCount={4} />
          <span className={styles.host}>
            <span>주최 새벽정리</span>
            <AttendanceRow records={[]} />
          </span>
        </a>
      </section>

      <section className={styles.section} aria-labelledby="detail">
        <h2 id="detail" className={styles.sectionTitle}>
          상세 요약
        </h2>
        <div className={styles.detail}>
          <p className={styles.detailTitle}>백엔드 면접 질문 같이 풀어 봐요</p>
          <p className={`${styles.detailTime} num`}>
            11월 20일 목요일 오전 10:00
          </p>
          <p className={styles.meta}>경기 · 카페 2층 창가 자리</p>
          <div className={styles.detailSeats}>
            <SeatRow capacity={6} memberCount={4} mySeatIndex={3} />
            <span className={`${styles.meta} num`}>신청 마감 오전 9:50</span>
          </div>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="states">
        <h2 id="states" className={styles.sectionTitle}>
          상태 칩
        </h2>
        <div className={styles.row}>
          <StatusChip phase="RECRUITING" />
          <StatusChip phase="WAITLIST" />
          <StatusChip phase="CLOSED" />
          <StatusChip phase="IN_PROGRESS" />
          <StatusChip phase="ENDED" />
        </div>
      </section>

      <section className={styles.section} aria-labelledby="trust">
        <h2 id="trust" className={styles.sectionTitle}>
          출석 기록 줄
        </h2>
        <AttendanceRow
          rate={70}
          records={[
            "PRESENT",
            "NO_SHOW",
            "PRESENT",
            "LATE",
            "PRESENT",
            "PRESENT",
            "LATE",
            "PRESENT",
            "NO_SHOW",
            "PRESENT",
          ]}
        />
      </section>

      <section className={styles.section} aria-labelledby="buttons">
        <h2 id="buttons" className={styles.sectionTitle}>
          버튼
        </h2>
        <div className={styles.stack}>
          <Button variant="primary">참여하기</Button>
          <Button variant="primary" pending pendingLabel="참여하는 중">
            참여하기
          </Button>
          <div className={styles.row}>
            <Button>참여 취소</Button>
            <Button variant="text">자세히 보기</Button>
          </div>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="empty">
        <h2 id="empty" className={styles.sectionTitle}>
          빈 화면
        </h2>
        <div className={styles.empty}>
          <p>아직 참여한 스터디가 없어요</p>
          <Button>스터디 찾아보기</Button>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="text">
        <h2 id="text" className={styles.sectionTitle}>
          글자
        </h2>
        <ul className={styles.stack}>
          {TEXT_STYLES.map(([name, sample]) => (
            <li key={name} className={styles.textSample}>
              <span className={styles.tokenName}>{name}</span>
              <span className={styles[`text-${name}`]}>{sample}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className={styles.section} aria-labelledby="colors">
        <h2 id="colors" className={styles.sectionTitle}>
          색
        </h2>
        <ul className={styles.swatches}>
          {COLORS.map((name) => (
            <li key={name} className={styles.swatch}>
              <span
                className={styles.swatchColor}
                style={{ background: `var(--color-${name})` }}
              />
              <span className={styles.tokenName}>{name}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
