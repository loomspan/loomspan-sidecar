package ai.loomspan.sidecar.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Durable correlation and safe inspection, never a recovery authority. */
public final class FileConfigurationStartupStore {
    public record Startup(UUID localId, Instant startedAt, Map<String, Object> inspection) {}
    private final JdbcTemplate jdbc;
    private final int retention;
    private final JsonMapper json = JsonMapper.builder().build();
    public FileConfigurationStartupStore(JdbcTemplate jdbc, int retention) {
        this.jdbc = jdbc; this.retention = retention;
    }
    public Startup record(Map<String, Object> inspection) {
        Startup value = new Startup(UUID.randomUUID(), Instant.now(), Map.copyOf(inspection));
        jdbc.update("INSERT INTO configuration_file_startup(local_id, started_at, inspection_json) VALUES (?, ?, ?)",
                value.localId().toString(), value.startedAt().toString(), json.writeValueAsString(inspection));
        jdbc.update("DELETE FROM configuration_file_startup WHERE sequence NOT IN "
                + "(SELECT sequence FROM configuration_file_startup ORDER BY sequence DESC LIMIT ?)", retention);
        return value;
    }
    @SuppressWarnings("unchecked")
    public List<Startup> history() {
        return jdbc.query("SELECT * FROM configuration_file_startup ORDER BY sequence DESC", (rs, row) ->
                new Startup(UUID.fromString(rs.getString("local_id")), Instant.parse(rs.getString("started_at")),
                        json.readValue(rs.getString("inspection_json"), Map.class)));
    }
}
