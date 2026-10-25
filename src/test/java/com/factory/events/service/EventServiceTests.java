package com.factory.events.service;

import com.factory.events.dto.EventRequest;
import com.factory.events.enums.EventType;
import com.factory.events.repository.EventAuditRepository;
import com.factory.events.repository.EventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(EventService.class)
class EventServiceTests {
    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventAuditRepository eventAuditRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void returnsEmptyListWhenNoEventsExist() {
        assertThat(eventService.getEvents()).isEmpty();
    }

    @Test
    void returnsPersistedEventsAsResponses() {
        var production = eventService.createEvent(new EventRequest(
                "production-1", EventType.PRODUCTION, "Produção concluída", LocalDateTime.of(2026, 10, 24, 10, 30)));
        var quality = eventService.createEvent(new EventRequest(
                "quality-1", EventType.QUALITY, "Inspeção concluída", LocalDateTime.of(2026, 10, 24, 11, 0)));
        entityManager.flush();
        entityManager.clear();

        assertThat(eventService.getEvents()).containsExactlyInAnyOrder(production, quality);
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void generatesInternalIdsAndPreservesExternalEventIds(EventType eventType) {
        var request = new EventRequest("external-1", eventType, "Evento de teste", LocalDateTime.of(2026, 10, 24, 10, 30));

        var first = eventService.createEvent(request);
        var second = eventService.createEvent(new EventRequest(
                "external-2", eventType, request.description(), request.timestamp()));
        entityManager.flush();
        entityManager.clear();

        assertThat(first.eventId()).isEqualTo("external-1");
        assertThat(second.eventId()).isNotEqualTo(first.eventId());
        assertThat(eventRepository.findByEventId(first.eventId())).hasValueSatisfying(event -> {
            assertThat(event.getId()).isPositive();
            assertThat(event.getEventType()).isEqualTo(request.eventType());
            assertThat(event.getDescription()).isEqualTo(request.description());
            assertThat(event.getTimestamp()).isEqualTo(request.timestamp());
        });
        var firstEntity = eventRepository.findByEventId(first.eventId()).orElseThrow();
        var secondEntity = eventRepository.findByEventId(second.eventId()).orElseThrow();
        assertThat(secondEntity.getId()).isPositive().isNotEqualTo(firstEntity.getId());
        if (eventType == EventType.QUALITY) {
            assertThat(eventAuditRepository.findAll()).allSatisfy(audit -> assertThat(audit.getId()).isPositive());
            assertThat(eventAuditRepository.findAll())
                    .extracting(audit -> audit.getEventId())
                    .containsExactlyInAnyOrder(first.eventId(), second.eventId());
        } else {
            assertThat(eventAuditRepository.count()).isZero();
        }
    }
}
