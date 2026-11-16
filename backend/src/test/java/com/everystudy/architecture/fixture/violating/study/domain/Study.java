package com.everystudy.architecture.fixture.violating.study.domain;

import com.everystudy.architecture.fixture.violating.study.service.StudyService;

public class Study {
    StudyService upperLayer; // 위반: domain → service
}
