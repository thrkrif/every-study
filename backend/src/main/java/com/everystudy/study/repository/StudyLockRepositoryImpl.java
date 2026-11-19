package com.everystudy.study.repository;

import com.everystudy.study.domain.Study;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import org.hibernate.Session;

/**
 * MySQL 은 {@code FOR UPDATE} 에 대기 시간을 적을 수 없고(NOWAIT 만 있다), Hibernate 7.4 의 MySQL 방언도 0 보다 큰
 * {@code jakarta.persistence.lock.timeout} 은 무시한다. 그래서 락을 잡는 동안만 연결의 {@code innodb_lock_wait_timeout}
 * 을 바꾸고, 끝나면 원래 값으로 되돌린다 (연결 풀에서 다른 요청이 같은 연결을 받으므로).
 *
 * A, B 가 있는데 A가 들어와서 정원 마감, 그런데 DB가 느려졌거나 트랜잭션 A의 어딘가 멈춰서 B가 마감되었는데도 계속 기다리는 상황을 방지하기 위함
 * 기본적으로 마감되었을 시 바로 409 STUDY_FULL 을 보내주는데, 안전 장치로 혹시나 락 대기를 오래하는 경우 최대를 3초로 잡고
 * 3초가 넘어가는 순간 409 LOCK_TIMEOUT 를 보내서 쓸데없이 락을 오래 기다리는 상황을 방지
 */
class StudyLockRepositoryImpl implements StudyLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Study> findByIdForUpdate(long id, Duration lockTimeout) {
        Session session = entityManager.unwrap(Session.class);
        long previous = session.doReturningWork(connection -> {
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")) {
                rs.next();
                return rs.getLong(1);
            }
        });
        setLockWaitTimeout(session, Math.max(1, lockTimeout.toSeconds()));
        try {
            return Optional.ofNullable(entityManager.find(Study.class, id, LockModeType.PESSIMISTIC_WRITE));
        } finally {
            setLockWaitTimeout(session, previous);
        }
    }

    private static void setLockWaitTimeout(Session session, long seconds) {
        session.doWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout = " + seconds);
            }
        });
    }
}
