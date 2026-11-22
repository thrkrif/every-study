import type { Metadata } from "next";
import { AdminHome } from "./AdminHome";

export const metadata: Metadata = {
  title: "운영 | 모두의 스터디",
};

export default function AdminPage() {
  return <AdminHome />;
}
