package com.everystudy.architecture.fixture.violating.study.service;

import com.everystudy.architecture.fixture.violating.chat.service.ChatRoomService;
import com.everystudy.architecture.fixture.violating.study.web.StudyResponse;

public class StudyService {
    StudyResponse upperLayer; // 위반: service → web
    ChatRoomService chatRooms; // 위반: study → chat (chat → study 와 합쳐 순환)
}
