import type { Metadata } from "next";
import { OnboardingFlow } from "./OnboardingFlow";

export const metadata: Metadata = {
  title: "시작하기 | 모두의 스터디",
};

export default function OnboardingPage() {
  return <OnboardingFlow />;
}
