package ai.loomspan.sidecar.storage;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Transaction boundary for durable intended selection and snapshot history. */
public final class ConfigurationSnapshotStore
{
    public static final String EMPTY_REST_ROUTES = "targets: {}\nroutes: {}\n";

    private final ConfigurationSnapshotRepository repository;
    private final TransactionTemplate transactions;
    private final int maxRetained;

    public ConfigurationSnapshotStore(ConfigurationSnapshotRepository repository, TransactionTemplate transactions)
    {
        this(repository, transactions, 10);
    }

    public ConfigurationSnapshotStore(ConfigurationSnapshotRepository repository, TransactionTemplate transactions, int maxRetained)
    {
        this.repository = repository;
        this.transactions = transactions;
        if (maxRetained <= 0)
        {
            throw new IllegalArgumentException("Snapshot retention limit must be positive");
        }
        this.maxRetained = maxRetained;
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
                throw selectedLoadFailure("missing pointer");
            }
            return loadSelected(state.currentSequence());
        }));
    }

    public ConfigurationSnapshot current()
    {
        return Objects.requireNonNull(transactions.execute(ignored -> {
            var state = repository.state();
            if (!state.initialized())
            {
                throw new IllegalStateException("Configuration store is not initialized");
            }
            if (state.currentSequence() == null)
            {
                throw selectedLoadFailure("missing pointer");
            }
            return loadSelected(state.currentSequence());
        }));
    }

    /** Commit a complete pending candidate as intended production, guarded by the caller's expected selection. */
    public ConfigurationSnapshot submit(ManagedConfiguration configuration, UUID sourceId, UUID expectedCurrentLocalId)
    {
        Objects.requireNonNull(expectedCurrentLocalId, "expectedCurrentLocalId");
        return Objects.requireNonNull(transactions.execute(ignored -> {
            ConfigurationSnapshot current = current();
            if (!current.localId().equals(expectedCurrentLocalId))
            {
                throw new IllegalStateException("Current configuration snapshot changed unexpectedly");
            }
            ConfigurationSnapshot candidate = repository.insert(configuration, sourceId);
            repository.switchCurrent(current.submissionSequence(), candidate.submissionSequence());
            return candidate;
        }));
    }

    /** Restore a known predecessor and mark the rejected candidate failed in one transaction. */
    public void revert(UUID expectedCurrentLocalId, UUID predecessorLocalId)
    {
        Objects.requireNonNull(expectedCurrentLocalId, "expectedCurrentLocalId");
        Objects.requireNonNull(predecessorLocalId, "predecessorLocalId");
        transactions.executeWithoutResult(ignored -> {
            ConfigurationSnapshot current = current();
            if (!current.localId().equals(expectedCurrentLocalId))
            {
                throw new IllegalStateException("Current configuration snapshot changed unexpectedly");
            }
            Long predecessorSequence = repository.sequenceFor(predecessorLocalId);
            if (predecessorSequence == null || predecessorSequence == current.submissionSequence())
            {
                throw new IllegalArgumentException("Unknown predecessor configuration snapshot");
            }
            repository.requireBySequence(predecessorSequence);
            repository.switchCurrent(current.submissionSequence(), predecessorSequence);
            repository.updateStatus(expectedCurrentLocalId, SnapshotStatus.FAILED);
        });
    }

    public ConfigurationSnapshot findByLocalId(UUID localId)
    {
        return transactions.execute(ignored -> repository.findByLocalId(localId));
    }

    public List<ConfigurationSnapshot> history() {
        return Objects.requireNonNull(transactions.execute(ignored -> repository.history()));
    }

    public void updateStatus(UUID localId, SnapshotStatus status)
    {
        transactions.executeWithoutResult(ignored -> repository.updateStatus(localId, status));
    }

    /** Caller protection and the selected pointer count toward the limit; excess protected history remains. */
    public void prune(Set<UUID> protectedLocalIds)
    {
        Set<UUID> protectedIds = Set.copyOf(protectedLocalIds);
        transactions.executeWithoutResult(ignored -> {
            ConfigurationSnapshot selected = current();
            var oldest = repository.snapshotIdsOldestFirst();
            int excess = oldest.size() - maxRetained;
            for (var id : oldest)
            {
                if (excess <= 0)
                {
                    break;
                }
                if (!id.localId().equals(selected.localId()) && !protectedIds.contains(id.localId()))
                {
                    repository.deleteBySequence(id.sequence());
                    excess--;
                }
            }
        });
    }

    private ConfigurationSnapshot loadSelected(long sequence)
    {
        try
        {
            return repository.requireBySequence(sequence);
        }
        catch (RuntimeException failure)
        {
            throw selectedLoadFailure("sequence " + sequence);
        }
    }

    private IllegalStateException selectedLoadFailure(String selection)
    {
        return new IllegalStateException("Cannot load selected configuration snapshot (" + selection
                + "); stop Sidecar and restore a consistent full database backup");
    }
}
