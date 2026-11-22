import type { Metadata } from "next";
import { NotificationSettingsView } from "./NotificationSettingsView";

export const metadata: Metadata = {
  title: "알림 설정 | 모두의 스터디",
};

export default function NotificationSettingsPage() {
  return <NotificationSettingsView />;
}
