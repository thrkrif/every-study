import type { Metadata } from "next";
import { EditProfileView } from "./EditProfileView";

export const metadata: Metadata = {
  title: "내 정보 수정 | 모두의 스터디",
};

export default function EditProfilePage() {
  return <EditProfileView />;
}
