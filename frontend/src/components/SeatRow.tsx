import styles from "./SeatRow.module.css";

type SeatRowProps = {
  capacity: number;
  memberCount: number;
  /** 내 자리 번호(0부터). 참여 중이 아니면 비운다 */
  mySeatIndex?: number;
};

/**
 * 좌석 줄 (DESIGN 3장). 정원만큼 칸을 늘어놓고 참여자 수만큼 채운다.
 * 첫 칸은 주최자다. 정원이 차면 마감이지만 오류가 아니므로 중립색으로 채운다.
 */
export function SeatRow({ capacity, memberCount, mySeatIndex }: SeatRowProps) {
  const filled = Math.min(memberCount, capacity);
  const full = filled >= capacity;
  return (
    <div
      className={styles.row}
      role="img"
      aria-label={`정원 ${capacity}명 중 ${filled}명 참여`}
      data-full={full || undefined}
    >
      <span className={styles.seats} aria-hidden="true">
        {Array.from({ length: capacity }, (_, index) => (
          <span
            key={index}
            className={styles.seat}
            data-state={
              index >= filled ? "empty" : index === 0 ? "host" : "member"
            }
            data-mine={index === mySeatIndex || undefined}
          />
        ))}
      </span>
      <span className={`${styles.count} num`} aria-hidden="true">
        {filled}/{capacity}명
      </span>
    </div>
  );
}
