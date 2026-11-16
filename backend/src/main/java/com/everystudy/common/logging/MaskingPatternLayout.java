package com.everystudy.common.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** 메시지와 예외 내용까지 조립이 끝난 로그 한 건 전체를 {@link LogMasker} 로 가린다. logback-spring.xml 에서 쓴다. */
public class MaskingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return LogMasker.mask(super.doLayout(event));
    }
}
