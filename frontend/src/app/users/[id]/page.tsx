import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ProfileView } from "./ProfileView";

export const metadata: Metadata = {
  title: "프로필 | 모두의 스터디",
};

export default async function ProfilePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!/^[1-9]\d{0,14}$/.test(id)) notFound();
  return <ProfileView id={Number(id)} />;
}
