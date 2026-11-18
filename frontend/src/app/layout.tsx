import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "@/styles/globals.css";
import { AppHeader } from "@/components/AppHeader";
import { HeaderMenu } from "@/components/HeaderMenu";
import { pretendard } from "./fonts";
import { Providers } from "./providers";

export const metadata: Metadata = {
  title: "모두의 스터디",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover", // 하단 고정 영역에 safe-area 여백을 더하기 위해 필요하다
  themeColor: "#f5f6f8",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko" className={pretendard.variable}>
      <body>
        <Providers>
          <AppHeader actions={<HeaderMenu />} />
          {children}
        </Providers>
      </body>
    </html>
  );
}
