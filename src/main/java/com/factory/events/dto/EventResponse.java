package com.factory.events.dto;

import com.factory.events.enums.EventType;
import java.time.LocalDateTime;

public record EventResponse(
        String eventId,
        EventType eventType,
        String description,
        LocalDateTime timestamp) {
}
