package com.everystudy.auth.service;

/** @param onboarded 온보딩을 마친 회원인가. 아니면 온보딩 화면으로 보낸다 */
public record LoginResult(IssuedTokens tokens, boolean onboarded) {
}
