package com.factory.events.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class H2SchemaMigrationTests {
    @Test
    void preservesLegacyDataAndMovesPrimaryKeyToGeneratedId() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:migration-test;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE events (event_id VARCHAR(255) PRIMARY KEY,
                    idempotency_key VARCHAR(128), description VARCHAR(255),
                    CONSTRAINT uk_events_idempotency_key UNIQUE(idempotency_key))
                """);
        jdbc.update("INSERT INTO events(event_id, idempotency_key, description) VALUES ('old-event', 'old-key', 'Preservado')");

        H2SchemaMigration.migrate(dataSource);
        H2SchemaMigration.migrate(dataSource);

        assertThat(jdbc.queryForObject("SELECT description FROM events WHERE event_id='old-event'", String.class))
                .isEqualTo("Preservado");
        assertThat(jdbc.queryForObject("SELECT id FROM events WHERE event_id='old-event'", Long.class)).isPositive();
        jdbc.update("INSERT INTO events(event_id, description) VALUES ('new-event', 'Novo')");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM events", Long.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO events(event_id) VALUES ('old-event')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        try (var connection = dataSource.getConnection();
                var primaryKeys = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "EVENTS")) {
            assertThat(primaryKeys.next()).isTrue();
            assertThat(primaryKeys.getString("COLUMN_NAME")).isEqualTo("ID");
        }
        assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='EVENTS'",
                String.class)).doesNotContain("IDEMPOTENCY_KEY");
    }
}
