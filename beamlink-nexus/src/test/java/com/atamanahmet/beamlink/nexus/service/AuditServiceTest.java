package com.atamanahmet.beamlink.nexus.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atamanahmet.beamlink.nexus.domain.AdminAuditLog;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import com.atamanahmet.beamlink.nexus.dto.AuditLogResponse;
import com.atamanahmet.beamlink.nexus.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    @Test
    void trimKeepsNewestDropsOldest() {
        Store store = new Store();
        AuditService service = new AuditService(mockRepo(store), passthroughTx(), 3);

        for (int i = 0; i < 5; i++) {
            service.record(
                    AuditEventType.LOGIN,
                    AuditOutcome.SUCCESS,
                    "127.0.0.1",
                    null,
                    "row-" + i);
        }

        List<AuditLogResponse> rows = service.list(0, 50);
        assertThat(rows).extracting(AuditLogResponse::detail)
                .containsExactly("row-4", "row-3", "row-2");
    }

    @Test
    void recordSwallowsPersistenceFailure() {
        AdminAuditLogRepository repo = mock(AdminAuditLogRepository.class);
        when(repo.save(any(AdminAuditLog.class))).thenThrow(new RuntimeException("db down"));
        AuditService service = new AuditService(repo, passthroughTx());

        Logger logger = (Logger) LoggerFactory.getLogger(AuditService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> service.record(
                    AuditEventType.LOGIN,
                    AuditOutcome.FAILED,
                    "127.0.0.1",
                    null,
                    null)).doesNotThrowAnyException();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static PlatformTransactionManager passthroughTx() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static AdminAuditLogRepository mockRepo(Store store) {
        AdminAuditLogRepository repo = mock(AdminAuditLogRepository.class);
        when(repo.save(any(AdminAuditLog.class))).thenAnswer(inv -> {
            AdminAuditLog row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(store.nextId++);
            }
            store.rows.add(row);
            return row;
        });
        when(repo.count()).thenAnswer(inv -> (long) store.rows.size());
        when(repo.findAll(any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(0);
            Comparator<AdminAuditLog> cmp = Comparator.comparing(AdminAuditLog::getId);
            var order = pageable.getSort().getOrderFor("id");
            if (order != null && order.isDescending()) {
                cmp = cmp.reversed();
            }
            List<AdminAuditLog> sorted = store.rows.stream().sorted(cmp).toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), sorted.size());
            List<AdminAuditLog> slice = start >= sorted.size() ? List.of() : sorted.subList(start, end);
            return new PageImpl<>(slice, pageable, store.rows.size());
        });
        doAnswer(inv -> {
            Iterable<AdminAuditLog> doomed = inv.getArgument(0);
            for (AdminAuditLog row : doomed) {
                store.rows.removeIf(existing -> existing.getId().equals(row.getId()));
            }
            return null;
        }).when(repo).deleteAll(any());
        return repo;
    }

    private static final class Store {
        private final List<AdminAuditLog> rows = new ArrayList<>();
        private long nextId = 1L;
    }
}
