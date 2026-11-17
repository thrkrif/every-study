import { LoginView } from "./LoginView";

// 소셜 로그인 실패 시 서버가 /login?error=social_login_failed 로 보낸다 (API 2장).
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string | string[] }>;
}) {
  const { error } = await searchParams;
  return <LoginView error={typeof error === "string" ? error : undefined} />;
}
