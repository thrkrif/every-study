import type { Metadata } from "next";
import { AdminReportsView } from "./AdminReportsView";

export const metadata: Metadata = {
  title: "신고 큐 | 모두의 스터디",
};

export default function AdminReportsPage() {
  return <AdminReportsView />;
}
