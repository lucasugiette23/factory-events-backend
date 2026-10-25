package com.factory.events.service;

import com.factory.events.dto.EventRequest;
import com.factory.events.dto.EventResponse;
import com.factory.events.entity.Event;
import com.factory.events.entity.EventAudit;
import com.factory.events.enums.EventType;
import com.factory.events.exception.DuplicateEventException;
import com.factory.events.repository.EventAuditRepository;
import com.factory.events.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final EventAuditRepository eventAuditRepository;

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        if (eventRepository.existsByEventId(request.eventId())) {
            throw duplicateEvent();
        }

        Event event;
        try {
            event = eventRepository.saveAndFlush(new Event(
                    request.eventId(), request.eventType(), request.description(), request.timestamp()));
        } catch (DataIntegrityViolationException exception) {
            if (isEventIdConflict(exception)) {
                throw duplicateEvent();
            }
            throw exception;
        }
        if (event.getEventType() == EventType.QUALITY) {
            eventAuditRepository.save(new EventAudit(
                    event.getEventId(), LocalDateTime.now(), event.getEventType().name()));
        }
        return new EventResponse(
                event.getEventId(), event.getEventType(), event.getDescription(), event.getTimestamp());
    }

    public List<EventResponse> getEvents() {
        return eventRepository.findAll().stream()
                .map(event -> new EventResponse(
                        event.getEventId(), event.getEventType(), event.getDescription(), event.getTimestamp()))
                .toList();
    }

    private DuplicateEventException duplicateEvent() {
        return new DuplicateEventException("Já existe um evento registrado com este eventId.");
    }

    private boolean isEventIdConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT)
                            .contains("uk_events_event_id")) {
                return true;
            }
        }
        return false;
    }
}  
