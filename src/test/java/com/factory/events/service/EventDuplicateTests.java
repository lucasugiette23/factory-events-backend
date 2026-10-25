package com.factory.events.service;

import com.factory.events.dto.EventRequest;
import com.factory.events.enums.EventType;
import com.factory.events.exception.DuplicateEventException;
import com.factory.events.repository.EventAuditRepository;
import com.factory.events.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:duplicate-event-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class EventDuplicateTests {
    private static final String JSON = """
            {"eventId":"external-1","eventType":"QUALITY","description":"Inspeção","timestamp":"2026-10-24T10:30:00"}
            """;

    @Autowired
    private EventService eventService;
    @Autowired
    private EventRepository eventRepository;
    @MockitoSpyBean
    private EventAuditRepository eventAuditRepository;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        eventAuditRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void rejectsReplayWithoutDuplicatingEventOrAudit() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").isNotEmpty());
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EVENT"))
                .andExpect(jsonPath("$.detail").value("Já existe um evento registrado com este eventId."));
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventAuditRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsMissingBlankAndOversizedEventIds() throws Exception {
        for (String body : new String[]{
                JSON.replace("\"eventId\":\"external-1\",", ""),
                JSON.replace("external-1", " "),
                JSON.replace("external-1", "x".repeat(256))}) {
            mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("eventId"));
        }
        assertThat(eventRepository.count()).isZero();
        assertThat(eventAuditRepository.count()).isZero();
    }

    @Test
    void rollsBackEventWhenAuditFailsAndAllowsRetry() {
        doThrow(new IllegalStateException("Falha de auditoria"))
                .when(eventAuditRepository).save(any());
        var request = request();
        assertThatThrownBy(() -> eventService.createEvent(request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(eventRepository.count()).isZero();
        assertThat(eventAuditRepository.count()).isZero();

        reset(eventAuditRepository);
        eventService.createEvent(request);
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventAuditRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsPersistOnlyOneEventAndAudit() throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<String> submit = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                eventService.createEvent(request());
                return "created";
            } catch (DuplicateEventException exception) {
                return "duplicate";
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "duplicate");
        }
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventAuditRepository.count()).isEqualTo(1);
    }

    private EventRequest request() {
        return new EventRequest("external-1", EventType.QUALITY, "Inspeção", LocalDateTime.of(2026, 10, 24, 10, 30));
    }
}
