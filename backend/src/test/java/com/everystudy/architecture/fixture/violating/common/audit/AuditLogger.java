package com.everystudy.architecture.fixture.violating.common.audit;

import com.everystudy.architecture.fixture.violating.study.service.StudyService;

public class AuditLogger {
    StudyService module; // 위반: common → 기능 모듈
}
