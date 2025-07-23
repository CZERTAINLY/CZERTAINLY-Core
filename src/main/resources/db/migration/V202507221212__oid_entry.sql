CREATE TABLE oid_entry (
    oid TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    description TEXT,
    code TEXT,
    oid_category TEXT NOT NULL,
    alt_codes TEXT[],
    value_type TEXT
);
