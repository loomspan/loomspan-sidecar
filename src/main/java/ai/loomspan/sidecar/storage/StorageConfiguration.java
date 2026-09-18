package ai.loomspan.sidecar.storage;

import ai.loomspan.sidecar.config.SidecarStorageProperties;
import ai.loomspan.sidecar.config.SidecarSnapshotProperties;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

@Configuration
public class StorageConfiguration
{
    @Bean
    DataSource storageDataSource(SidecarStorageProperties properties)
    {
        return dataSource(Path.of(properties.getDatabasePath()));
    }

    @Bean
    Flyway storageFlyway(DataSource storageDataSource)
    {
        return migrate(storageDataSource);
    }

    @Bean
    ConfigurationSnapshotRepository configurationSnapshotRepository(DataSource storageDataSource, Flyway storageFlyway)
    {
        return new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(storageDataSource));
    }

    @Bean
    DataSourceTransactionManager transactionManager(DataSource storageDataSource)
    {
        return new DataSourceTransactionManager(storageDataSource);
    }

    @Bean
    ConfigurationSnapshotStore configurationSnapshotStore(ConfigurationSnapshotRepository repository,
            DataSourceTransactionManager transactionManager, SidecarSnapshotProperties properties)
    {
        var store = new ConfigurationSnapshotStore(repository, new TransactionTemplate(transactionManager),
                properties.getMaxRetained());
        store.initialize();
        store.prune(Set.of());
        return store;
    }

    public static DataSource dataSource(Path path)
    {
        Path absolute = path.toAbsolutePath().normalize();
        boolean newDatabase = Files.notExists(absolute);
        if (Files.isDirectory(absolute))
        {
            throw new IllegalStateException("Storage database path is a directory");
        }
        try
        {
            Files.createDirectories(absolute.getParent());
            if (!newDatabase && Files.size(absolute) == 0)
            {
                throw new IllegalStateException("Existing storage database has no migration history");
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Cannot create storage database directory", e);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + absolute);
        if (!newDatabase)
        {
            requireMigrationHistory(source);
        }
        verifyPragmas(source);
        return source;
    }

    private static void requireMigrationHistory(DataSource source)
    {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1 FROM sqlite_master "
                        + "WHERE type = 'table' AND name = 'flyway_schema_history'"))
        {
            if (!result.next())
            {
                throw new IllegalStateException("Existing storage database has no migration history");
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Cannot inspect existing storage database", e);
        }
    }

    public static Flyway migrate(DataSource source)
    {
        Flyway flyway = Flyway.configure().dataSource(source).load();
        flyway.migrate();
        flyway.validate();
        return flyway;
    }

    private static void verifyPragmas(DataSource source)
    {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement())
        {
            if (number(statement, "foreign_keys") != 1 || number(statement, "synchronous") != 2
                    || number(statement, "busy_timeout") != 5000 || !value(statement, "journal_mode").equalsIgnoreCase("wal"))
            {
                throw new IllegalStateException("SQLite durability settings could not be applied");
            }
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Cannot verify SQLite durability settings", e);
        }
    }

    private static int number(Statement statement, String pragma) throws SQLException
    {
        return Integer.parseInt(value(statement, pragma));
    }

    private static String value(Statement statement, String pragma) throws SQLException
    {
        try (ResultSet result = statement.executeQuery("PRAGMA " + pragma))
        {
            if (!result.next())
            {
                throw new IllegalStateException("SQLite did not return PRAGMA " + pragma);
            }
            return result.getString(1);
        }
    }
}
