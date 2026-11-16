package com.everystudy.architecture.fixture.clean.study.web;

import com.everystudy.architecture.fixture.clean.study.domain.StudyStatus;

// web 은 같은 모듈의 domain 타입을 쓸 수 있다.
public record StudyResponse(StudyStatus status) {}
