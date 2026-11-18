import type { Metadata } from "next";
import { StudyDetailView } from "./StudyDetailView";

export const metadata: Metadata = {
  title: "스터디 | 모두의 스터디",
};

export default async function StudyDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <StudyDetailView id={id} />;
}
