import type { Metadata } from "next";
import { NotificationCenter } from "./NotificationCenter";

export const metadata: Metadata = {
  title: "알림 | 모두의 스터디",
};

export default function NotificationsPage() {
  return <NotificationCenter />;
}
