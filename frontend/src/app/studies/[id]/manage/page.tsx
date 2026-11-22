import type { Metadata } from "next";
import { ManageStudyView } from "./ManageStudyView";

export const metadata: Metadata = {
  title: "스터디 관리 | 모두의 스터디",
};

export default async function ManageStudyPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ManageStudyView id={id} />;
}
