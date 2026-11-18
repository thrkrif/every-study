import styles from "./ChoiceChips.module.css";
import type { Choice } from "./ChoiceChips";

/** 여러 개 고르는 선택 칩 (DESIGN 5장 선택 칩). 실제로는 체크박스다. */
export function CheckChips({
  legend,
  choices,
  values,
  onChange,
}: {
  legend: string;
  choices: readonly Choice[];
  values: readonly string[];
  onChange: (values: string[]) => void;
}) {
  return (
    <fieldset className={styles.group}>
      <legend className={styles.legend}>{legend}</legend>
      <div className={styles.chips}>
        {choices.map((choice) => {
          const checked = values.includes(choice.value);
          return (
            <label key={choice.value} className={styles.chip}>
              <input
                type="checkbox"
                value={choice.value}
                checked={checked}
                onChange={() =>
                  onChange(
                    checked
                      ? values.filter((value) => value !== choice.value)
                      : [...values, choice.value],
                  )
                }
                className={styles.input}
              />
              <span className={styles.face}>{choice.label}</span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
