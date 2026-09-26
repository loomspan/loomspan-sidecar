package ai.loomspan.sidecar.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

/** Child JVM fixture: signal only after complete candidate and pointer writes remain uncommitted. */
public final class InterruptedSnapshotWrite
{
    private InterruptedSnapshotWrite() { }

    public static void main(String[] args) throws Exception
    {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + args[0]))
        {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO configuration_snapshot"
                    + "(local_id, document_count, rest_routes_yaml, execution_configuration_yaml) "
                    + "VALUES (?, 1, ?, ?)"))
            {
                insert.setString(1, args[2]);
                insert.setString(2, "targets: {interrupted: true}\nroutes: {}\n");
                insert.setString(3, ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
                insert.executeUpdate();
            }
            long sequence;
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT last_insert_rowid()"))
            {
                sequence = result.next() ? result.getLong(1) : -1;
            }
            try (var document = connection.prepareStatement("INSERT INTO configuration_skill_document"
                    + "(snapshot_sequence, ordinal, label, yaml) VALUES (?, 0, 'interrupted.yaml', 'name: interrupted')"))
            {
                document.setLong(1, sequence);
                document.executeUpdate();
            }
            try (var status = connection.prepareStatement("INSERT INTO configuration_snapshot_status"
                    + "(snapshot_sequence, status) VALUES (?, 'pending')"))
            {
                status.setLong(1, sequence);
                status.executeUpdate();
            }
            try (var pointer = connection.prepareStatement("UPDATE configuration_store_state "
                    + "SET current_snapshot_sequence = ? WHERE current_snapshot_sequence = ?"))
            {
                pointer.setLong(1, sequence);
                pointer.setLong(2, Long.parseLong(args[3]));
                if (pointer.executeUpdate() != 1)
                {
                    throw new IllegalStateException("Expected pointer not found");
                }
            }
            Files.writeString(Path.of(args[1]), "ready");
            Thread.sleep(30_000);
        }
    }
}
