import type { Metadata } from "next";
import { CreateStudyFlow } from "./CreateStudyFlow";

export const metadata: Metadata = {
  title: "스터디 만들기 | 모두의 스터디",
};

export default function NewStudyPage() {
  return <CreateStudyFlow />;
}
