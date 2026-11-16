package com.everystudy.architecture.fixture.violating.chat.service;

import com.everystudy.architecture.fixture.violating.study.domain.Study;
import com.everystudy.architecture.fixture.violating.study.repository.StudyRepository;

public class ChatReader {
    Study otherModuleEntity; // 위반: 다른 모듈의 domain
    StudyRepository otherModuleRepository; // 위반: 다른 모듈의 repository
}
