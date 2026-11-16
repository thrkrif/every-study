package com.everystudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 설정 속성은 각 모듈이 everystudy.<모듈> 접두사의 @ConfigurationProperties record 로 둔다 (ARCHITECTURE 4.9).
@SpringBootApplication
@ConfigurationPropertiesScan
public class EveryStudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(EveryStudyApplication.class, args);
    }
}
