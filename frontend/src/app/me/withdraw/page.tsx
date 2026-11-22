import type { Metadata } from "next";
import { WithdrawView } from "./WithdrawView";

export const metadata: Metadata = {
  title: "회원 탈퇴 | 모두의 스터디",
};

export default function WithdrawPage() {
  return <WithdrawView />;
}
