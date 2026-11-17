import { useId } from "react";
import styles from "./ChoiceChips.module.css";

export type Choice = { value: string; label: string };

type ChoiceChipsProps = {
  legend: string;
  /** 화면에는 숨기고 화면 낭독기에만 읽히는 legend 인지 */
  hideLegend?: boolean;
  choices: readonly Choice[];
  value: string | undefined;
  onChange: (value: string) => void;
};

/**
 * 하나만 고르는 선택 칩 (DESIGN 5장). 실제로는 라디오 버튼이라 키보드와 화면 낭독기가 그대로 동작한다.
 * 선택됨은 배경색만이 아니라 윤곽선과 체크 표시로도 구분한다.
 */
export function ChoiceChips({
  legend,
  hideLegend = false,
  choices,
  value,
  onChange,
}: ChoiceChipsProps) {
  const name = useId();
  return (
    <fieldset className={styles.group}>
      <legend className={hideLegend ? "visually-hidden" : styles.legend}>
        {legend}
      </legend>
      <div className={styles.chips}>
        {choices.map((choice) => (
          <label key={choice.value} className={styles.chip}>
            <input
              type="radio"
              name={name}
              value={choice.value}
              checked={value === choice.value}
              onChange={() => onChange(choice.value)}
              className={styles.input}
            />
            <span className={styles.face}>{choice.label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
