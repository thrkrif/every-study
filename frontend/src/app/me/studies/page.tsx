import type { Metadata } from "next";
import { MyStudiesView } from "./MyStudiesView";

export const metadata: Metadata = {
  title: "내 스터디 | 모두의 스터디",
};

export default function MyStudiesPage() {
  return <MyStudiesView />;
}
