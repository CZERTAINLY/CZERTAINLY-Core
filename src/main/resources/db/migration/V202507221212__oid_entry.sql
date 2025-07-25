CREATE TABLE oid_entry (
    oid TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    description TEXT,
    code TEXT,
    category TEXT NOT NULL,
    alt_codes TEXT[]
);
