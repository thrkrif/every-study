import localFont from "next/font/local";

// Pretendard(SIL Open Font License 1.1)를 npm 패키지에서 읽어 자체 호스팅한다 (DESIGN 4장).
// 가변 글꼴 파일 하나로 모든 굵기를 쓴다.
export const pretendard = localFont({
  src: "../../node_modules/pretendard/dist/web/variable/woff2/PretendardVariable.woff2",
  weight: "45 920",
  display: "swap",
  variable: "--font-sans",
  fallback: ["system-ui", "sans-serif"],
});
