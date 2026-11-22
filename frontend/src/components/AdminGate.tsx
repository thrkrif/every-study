"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useSession } from "@/lib/session";
import buttonStyles from "./Button.module.css";
import styles from "./AdminGate.module.css";

/**
 * 운영자 화면의 접근 문 (PRD 4.11, 5장 화면 13). 로그인한 운영자(`ADMIN`)에게만 안을 보여 준다. 서버가 `/admin` API 를
 * 운영자에게만 열어 주므로(403) 이 문은 안내를 위한 것이고 보안은 서버가 맡는다.
 */
export function AdminGate({ children }: { children: ReactNode }) {
  const session = useSession();
  if (session.status === "loading") return <Notice text="불러오는 중…" />;
  if (session.status === "anonymous") {
    return <Notice text="운영자만 볼 수 있어요." href="/login" cta="로그인" />;
  }
  if (session.status === "error") {
    return <Notice text="정보를 불러오지 못했어요." />;
  }
  if (session.me.role !== "ADMIN") {
    return <Notice text="운영자만 볼 수 있어요." href="/" cta="처음으로" />;
  }
  return <>{children}</>;
}

function Notice({
  text,
  href,
  cta,
}: {
  text: string;
  href?: string;
  cta?: string;
}) {
  return (
    <main className={styles.page}>
      <div className={styles.empty} role="status">
        <p>{text}</p>
        {href && cta && (
          <Link
            href={href}
            className={buttonStyles.button}
            data-variant="primary"
          >
            {cta}
          </Link>
        )}
      </div>
    </main>
  );
}
