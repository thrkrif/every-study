package com.everystudy.architecture.fixture.clean.study.service;

import com.everystudy.architecture.fixture.clean.study.domain.Study;
import com.everystudy.architecture.fixture.clean.study.event.StudyCreated;
import com.everystudy.architecture.fixture.clean.study.repository.StudyRepository;

public class StudyService {
    StudyRepository repository;
    Study study;
    StudyCreated published;
}
