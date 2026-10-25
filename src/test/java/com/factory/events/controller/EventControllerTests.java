package com.factory.events.controller;

import com.factory.events.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void returnsValidationErrorsWithFieldMessages() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')].message")
                        .value("A descrição é obrigatória."));
    }

    @Test
    void returnsAllowedEventTypesForInvalidEnum() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"eventId":"external-1","eventType":"OTHER","description":"Teste","timestamp":"2026-10-24T10:30:00"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Tipo de evento inválido. Utilize PRODUCTION ou QUALITY."));
    }

    @Test
    void returnsExpectedFormatForInvalidTimestamp() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"eventId":"external-1","eventType":"QUALITY","description":"Teste","timestamp":"ontem"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Data e hora inválidas. Utilize o formato ISO 8601, por exemplo 2026-10-24T10:30:00."));
    }

    @Test
    void hidesDatabaseDetails() throws Exception {
        when(eventService.getEvents()).thenThrow(new DataAccessResourceFailureException("SQL privado"));
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"))
                .andExpect(jsonPath("$.detail").value("Não foi possível acessar os dados. Tente novamente mais tarde."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void hidesUnexpectedErrorDetails() throws Exception {
        when(eventService.getEvents()).thenThrow(new IllegalStateException("Detalhe privado"));
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro interno ao processar a requisição."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
