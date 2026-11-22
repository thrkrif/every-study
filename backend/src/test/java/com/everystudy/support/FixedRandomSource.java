package com.everystudy.support;

import com.everystudy.common.random.RandomSource;

/** 테스트용 고정 {@link RandomSource}. 정해 둔 위치를 돌려준다(범위를 넘으면 나머지). 추첨 결과를 고정해 검증한다. */
public class FixedRandomSource implements RandomSource {

    private volatile int next;

    /** 다음 추첨에서 고를 위치. */
    public void pick(int index) {
        this.next = index;
    }

    @Override
    public int nextInt(int bound) {
        return next % bound;
    }
}
