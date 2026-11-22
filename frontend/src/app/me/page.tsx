import type { Metadata } from "next";
import { MyPageView } from "./MyPageView";

export const metadata: Metadata = {
  title: "마이페이지 | 모두의 스터디",
};

export default function MyPage() {
  return <MyPageView />;
}
