CREATE TABLE compliance_internal_rule (
	"uuid"          UUID NOT NULL,
    "name"          TEXT NOT NULL,
	"description"   TEXT,
	"resource"      TEXT NOT NULL,
	PRIMARY KEY (uuid)
);

ALTER TABLE compliance_profile_rule
    DROP CONSTRAINT "fk_compliance_profile_rule_to_rule",
    ADD CONSTRAINT fk_compliance_profile_rule_to_compliance_internal_rule FOREIGN KEY (internal_rule_uuid) REFERENCES compliance_internal_rule(uuid) ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE condition_item
    ADD COLUMN compliance_internal_rule_uuid UUID NULL,
    ALTER COLUMN condition_uuid DROP NOT NULL,
    ADD CONSTRAINT fk_condition_item_to_compliance_internal_rule FOREIGN KEY (compliance_internal_rule_uuid) REFERENCES compliance_internal_rule(uuid) ON UPDATE CASCADE ON DELETE CASCADE;