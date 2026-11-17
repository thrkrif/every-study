import Image from "next/image";
import { socialLoginUrl } from "@/lib/api/client";
import styles from "./SocialLoginButtons.module.css";

/**
 * 카카오·구글 로그인 버튼. 각 공급자의 공식 디자인 가이드를 따른다 (DESIGN 5장).
 * - 카카오: 배경 #FEE500, 검은 말풍선 심볼, 레이블 "카카오 로그인"(검정 85%), 모서리 12px
 * - 구글: 흰 배경, #747775 1px 윤곽선, 글자 #1F1F1F, 공식 컬러 G 로고, 레이블은 현지화 허용
 * 로고 파일은 각 공급자가 배포한 리소스에서 가져왔다(public/auth). 모양·색을 바꾸지 않는다.
 * 로그인은 fetch 가 아니라 페이지 이동이다 (API 2장).
 */
export function SocialLoginButtons() {
  return (
    <div className={styles.stack}>
      <a href={socialLoginUrl("kakao")} className={styles.kakao}>
        <Image src="/auth/kakao-symbol.svg" alt="" width={18} height={18} />
        <span>카카오 로그인</span>
      </a>
      <a href={socialLoginUrl("google")} className={styles.google}>
        <Image src="/auth/google-g.png" alt="" width={20} height={20} />
        <span>Google 계정으로 로그인</span>
      </a>
    </div>
  );
}
