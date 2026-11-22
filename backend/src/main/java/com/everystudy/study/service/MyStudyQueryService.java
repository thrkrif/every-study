package com.everystudy.study.service;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.study.domain.MyRole;
import com.everystudy.study.repository.MyStudyRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 스터디 목록 (PRD 4.10, API 3장). 내가 {@code ACTIVE} 참여자인 스터디만 나오므로 승계로 주최자가 바뀌면 전 주최자는
 * 목록에서 빠지고 새 주최자는 {@code HOST} 로 나온다. 나간 스터디·대기 중인 스터디는 나오지 않는다.
 */
@Service
public class MyStudyQueryService {

    /** 다가오는 모임은 진행 중, 지난 모임은 종료·파투. */
    public enum State {
        UPCOMING("U"), PAST("P");

        private final String cursorTag;

        State(String cursorTag) {
            this.cursorTag = cursorTag;
        }
    }

    /** @param role {@code null} 이면 주최·참여 모두 */
    public record Query(MyRole role, State state, String cursor, int size) {
    }

    /** 내 스터디 한 건. {@code role} 은 내 역할({@code HOST}/{@code MEMBER}), {@code status} 는 저장 상태다. */
    public record Item(long id, String title, String topicCode, String districtCode, Instant startAt,
            int durationMin, int capacity, int memberCount, String status, MyRole role) {
    }

    /** @param nextCursor 다음 페이지가 없으면 {@code null} */
    public record Page(List<Item> items, String nextCursor) {
    }

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final MyStudyRepository repository;

    MyStudyQueryService(MyStudyRepository repository) {
        this.repository = repository;
    }

    /** @throws InvalidInputException 커서가 망가졌거나 다른 {@code state} 의 것 */
    @Transactional(readOnly = true)
    public Page find(long userId, Query query) {
        MyStudyRepository.Cursor cursor = decode(query.cursor(), query.state());
        List<MyStudyRepository.Row> rows = repository.find(new MyStudyRepository.Condition(userId, query.role(),
                query.state() == State.UPCOMING, cursor, query.size()));
        boolean hasNext = rows.size() > query.size();
        List<MyStudyRepository.Row> page = hasNext ? rows.subList(0, query.size()) : rows;

        List<Item> items = page.stream()
                .map(r -> new Item(r.id(), r.title(), r.topicCode(), r.districtCode(), r.startAt(), r.durationMin(),
                        r.capacity(), r.memberCount(), r.status(), r.role()))
                .toList();
        String nextCursor = null;
        if (hasNext) {
            MyStudyRepository.Row last = page.getLast();
            nextCursor = ENCODER.encodeToString(
                    (query.state().cursorTag + "|" + last.startAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new Page(items, nextCursor);
    }

    private static MyStudyRepository.Cursor decode(String cursor, State state) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(DECODER.decode(cursor), StandardCharsets.UTF_8).split("\\|");
            if (parts.length == 3 && parts[0].equals(state.cursorTag)) {
                return new MyStudyRepository.Cursor(Instant.parse(parts[1]), Long.parseLong(parts[2]));
            }
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            // 아래에서 같은 오류로 응답한다.
        }
        throw new InvalidInputException(List.of(new FieldViolation("cursor", "올바르지 않은 커서입니다")));
    }
}
