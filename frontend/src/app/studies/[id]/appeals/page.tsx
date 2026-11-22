import type { Metadata } from "next";
import { StudyAppealsView } from "./StudyAppealsView";

export const metadata: Metadata = {
  title: "들어온 이의 | 모두의 스터디",
};

export default async function StudyAppealsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <StudyAppealsView id={id} />;
}
