package ai.loomspan.sidecar.storage;

public enum SnapshotStatus
{
    PENDING, PUBLISHED, FAILED;

    String databaseValue()
    {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    static SnapshotStatus fromDatabase(String value)
    {
        return valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
