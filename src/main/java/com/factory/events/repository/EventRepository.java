package com.factory.events.repository;

import com.factory.events.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    boolean existsByEventId(String eventId);

    java.util.Optional<Event> findByEventId(String eventId);
}
