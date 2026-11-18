import { StudyListView } from "./StudyListView";

// 첫 화면은 스터디 목록이다. 비회원도 본다 (PRD 3장, 4.3). 로그인은 헤더에서 연다.
export default function HomePage() {
  return <StudyListView />;
}
