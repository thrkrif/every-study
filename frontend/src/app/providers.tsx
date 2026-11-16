"use client";

import {
  isServer,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import type { ReactNode } from "react";
import { isApiError } from "@/lib/api/errors";

// 서버 상태 조회는 TanStack Query 로 통일한다 (ARCHITECTURE 6장).
function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 서버에서 그린 직후 브라우저가 곧바로 다시 조회하지 않게 한다 (TanStack Query SSR 안내).
        staleTime: 60 * 1000,
        // 4xx 는 다시 불러도 같은 결과다. 서버 오류와 네트워크 실패만 다시 시도한다.
        retry: (failureCount, error) =>
          failureCount < 2 &&
          !(isApiError(error) && error.status >= 400 && error.status < 500),
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient() {
  // 서버: 요청마다 새로 만든다 (사용자 간 캐시가 섞이지 않게).
  if (isServer) return makeQueryClient();
  // 브라우저: 하나를 계속 쓴다 (첫 렌더가 중단돼도 캐시를 잃지 않게).
  browserQueryClient ??= makeQueryClient();
  return browserQueryClient;
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={getQueryClient()}>
      {children}
    </QueryClientProvider>
  );
}
