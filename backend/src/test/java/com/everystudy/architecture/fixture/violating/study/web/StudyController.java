package com.everystudy.architecture.fixture.violating.study.web;

import com.everystudy.architecture.fixture.violating.study.repository.StudyRepository;

public class StudyController {
    StudyRepository repository; // 위반: web → repository
}
