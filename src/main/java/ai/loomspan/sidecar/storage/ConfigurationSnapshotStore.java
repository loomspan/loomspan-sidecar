package ai.loomspan.sidecar.storage;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

/** Transaction boundary for durable content and first-store initialization. */
public final class ConfigurationSnapshotStore
{
    public static final String EMPTY_REST_ROUTES = "targets: {}\nroutes: {}\n";

    private final ConfigurationSnapshotRepository repository;
    private final TransactionTemplate transactions;

    public ConfigurationSnapshotStore(ConfigurationSnapshotRepository repository, TransactionTemplate transactions)
    {
        this.repository = repository;
        this.transactions = transactions;
    }

    public ConfigurationSnapshot initialize()
    {
        return Objects.requireNonNull(transactions.execute(ignored -> {
            var state = repository.state();
            if (!state.initialized())
            {
                if (state.currentSequence() != null || repository.snapshotCount() != 0)
                {
                    throw new IllegalStateException("Uninitialized configuration store contains content");
                }
                ConfigurationSnapshot empty = repository.insert(new ManagedConfiguration(java.util.List.of(), EMPTY_REST_ROUTES), null);
                repository.setInitialCurrent(empty.submissionSequence());
                return empty;
            }
            if (state.currentSequence() == null)
            {
                throw new IllegalStateException("Initialized configuration store has no current snapshot");
            }
            return repository.requireBySequence(state.currentSequence());
        }));
    }

    public ConfigurationSnapshot current()
    {
        return Objects.requireNonNull(transactions.execute(ignored -> {
            var state = repository.state();
            if (!state.initialized() || state.currentSequence() == null)
            {
                throw new IllegalStateException("Configuration store is not initialized");
            }
            return repository.requireBySequence(state.currentSequence());
        }));
    }

    public ConfigurationSnapshot submit(ManagedConfiguration configuration, UUID sourceId)
    {
        return Objects.requireNonNull(transactions.execute(ignored -> {
            if (!repository.state().initialized())
            {
                throw new IllegalStateException("Configuration store is not initialized");
            }
            return repository.insert(configuration, sourceId);
        }));
    }

    public ConfigurationSnapshot findByLocalId(UUID localId)
    {
        return repository.findByLocalId(localId);
    }

    public void updateStatus(UUID localId, SnapshotStatus status)
    {
        transactions.executeWithoutResult(ignored -> repository.updateStatus(localId, status));
    }
}
