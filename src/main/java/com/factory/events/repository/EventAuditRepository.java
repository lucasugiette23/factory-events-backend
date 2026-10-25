package com.factory.events.repository;

import com.factory.events.entity.EventAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAuditRepository extends JpaRepository<EventAudit, Long> {
}
