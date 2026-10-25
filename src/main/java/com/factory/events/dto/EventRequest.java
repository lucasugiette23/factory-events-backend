package com.factory.events.dto;

import com.factory.events.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank(message = "O eventId é obrigatório.")
        @Size(max = 255, message = "O eventId deve ter no máximo 255 caracteres.") String eventId,
        @NotNull(message = "O tipo do evento é obrigatório.") EventType eventType,
        @NotBlank(message = "A descrição é obrigatória.")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.") String description,
        @NotNull(message = "A data e hora do evento são obrigatórias.") LocalDateTime timestamp) {
}
