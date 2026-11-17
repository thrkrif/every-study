"use client";

import { useQuery } from "@tanstack/react-query";
import { isApiError } from "./api/errors";
import { fetchMe, type Me } from "./api/me";
import { fetchMetaOptions } from "./api/meta";

export const ME_QUERY_KEY = ["me"] as const;
export const META_OPTIONS_QUERY_KEY = ["meta-options"] as const;

export type Session =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "error"; error: unknown }
  | { status: "member"; me: Me };

/**
 * 로그인 상태. 비회원은 오류가 아니라 `anonymous` 다.
 * `apiFetch` 가 401 에서 갱신을 한 번 시도하므로, 여기까지 401 이 오면 다시 로그인해야 하는 상태다.
 */
export function useSession(): Session {
  const query = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: ({ signal }) => fetchMe(signal),
    retry: false,
  });
  if (query.isPending) return { status: "loading" };
  if (query.isError) {
    return isApiError(query.error, "UNAUTHENTICATED")
      ? { status: "anonymous" }
      : { status: "error", error: query.error };
  }
  return { status: "member", me: query.data };
}

export function useMetaOptions() {
  return useQuery({
    queryKey: META_OPTIONS_QUERY_KEY,
    queryFn: ({ signal }) => fetchMetaOptions(signal),
    staleTime: 10 * 60 * 1000,
  });
}
