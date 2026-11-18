import path from "node:path";
import { loadEnvConfig } from "@next/env";
import type { NextConfig } from "next";

// 환경 파일은 저장소 루트에 하나만 둔다 (키 이름: 루트의 .env.example).
// Next.js 는 기본으로 frontend/ 만 보므로 루트에서 다시 읽는다. 파일이 없으면(운영, CI) 아무 일도 없다.
// 브라우저 코드에는 NEXT_PUBLIC_ 로 시작하는 값만 들어간다.
const repositoryRoot = path.join(__dirname, "..");
loadEnvConfig(
  repositoryRoot,
  process.env.NODE_ENV !== "production",
  undefined,
  true,
);

const nextConfig: NextConfig = {
  // 에이전트 규칙은 저장소 루트 AGENTS.md 한 곳에 둔다. next dev 가 frontend/AGENTS.md 를 만들지 않게 한다.
  agentRules: false,
};

export default nextConfig;
