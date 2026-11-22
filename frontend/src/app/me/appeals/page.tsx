import type { Metadata } from "next";
import { MyAppealsView } from "./MyAppealsView";

export const metadata: Metadata = {
  title: "이의 내역 | 모두의 스터디",
};

export default function MyAppealsPage() {
  return <MyAppealsView />;
}
