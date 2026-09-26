CREATE TABLE configuration_snapshot (
    submission_sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    local_id TEXT NOT NULL UNIQUE,
    source_id TEXT,
    document_count INTEGER NOT NULL CHECK (document_count >= 0),
    rest_routes_yaml TEXT NOT NULL,
    execution_configuration_yaml TEXT NOT NULL
);

CREATE TABLE configuration_skill_document (
    snapshot_sequence INTEGER NOT NULL REFERENCES configuration_snapshot(submission_sequence) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    label TEXT NOT NULL CHECK (length(trim(label)) > 0),
    yaml TEXT NOT NULL,
    PRIMARY KEY (snapshot_sequence, ordinal),
    UNIQUE (snapshot_sequence, label)
);

CREATE TABLE configuration_snapshot_status (
    snapshot_sequence INTEGER PRIMARY KEY REFERENCES configuration_snapshot(submission_sequence) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('pending', 'published', 'failed'))
);

CREATE TABLE configuration_store_state (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    initialized INTEGER NOT NULL CHECK (initialized IN (0, 1)),
    current_snapshot_sequence INTEGER REFERENCES configuration_snapshot(submission_sequence),
    CHECK ((initialized = 0 AND current_snapshot_sequence IS NULL) OR initialized = 1)
);

INSERT INTO configuration_store_state(singleton, initialized, current_snapshot_sequence) VALUES (1, 0, NULL);

CREATE TRIGGER configuration_snapshot_immutable
BEFORE UPDATE ON configuration_snapshot
BEGIN
    SELECT RAISE(ABORT, 'configuration snapshot content is immutable');
END;

CREATE TRIGGER configuration_skill_document_immutable
BEFORE UPDATE ON configuration_skill_document
BEGIN
    SELECT RAISE(ABORT, 'configuration skill document is immutable');
END;
