package com.everystudy.meta.web;

import com.everystudy.meta.service.MetaOptions;
import com.everystudy.meta.service.MetaOptionsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 코드 목록과 클라이언트용 설정값 (API 4장). 공개다. */
@RestController
class MetaOptionsController {

    private final MetaOptionsService service;

    MetaOptionsController(MetaOptionsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/meta/options")
    MetaOptions options() {
        return service.options();
    }
}
