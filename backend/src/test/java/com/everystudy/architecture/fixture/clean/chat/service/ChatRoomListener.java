package com.everystudy.architecture.fixture.clean.chat.service;

import com.everystudy.architecture.fixture.clean.study.event.StudyCreated;
import com.everystudy.architecture.fixture.clean.study.service.StudyService;

// 다른 모듈은 service 와 event 로만 접근한다. 방향은 chat → study.
public class ChatRoomListener {
    StudyCreated received;
    StudyService studies;
}
