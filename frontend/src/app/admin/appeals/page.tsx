import type { Metadata } from "next";
import { AdminAppealsView } from "./AdminAppealsView";

export const metadata: Metadata = {
  title: "이의 큐 | 모두의 스터디",
};

export default function AdminAppealsPage() {
  return <AdminAppealsView />;
}
