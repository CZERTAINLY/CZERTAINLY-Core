CREATE TABLE rule_trigger (
	uuid UUID NOT NULL,
	name VARCHAR NOT NULL,
	description VARCHAR,
	trigger_type VARCHAR NOT NULL,
	event_name VARCHAR,
	resource VARCHAR NOT NULL,
	trigger_resource VARCHAR,
    trigger_resource_uuid UUID,
	PRIMARY KEY (uuid)
);

CREATE TABLE rule (
	uuid UUID NOT NULL,
	connector_uuid UUID,
	name VARCHAR NOT NULL,
	description VARCHAR,
    resource VARCHAR NOT NULL,
    resource_type VARCHAR,
    resource_format VARCHAR,
    attributes JSONB,
	PRIMARY KEY (uuid),
	FOREIGN KEY (connector_uuid) REFERENCES connector(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rule_condition_group (
	uuid UUID NOT NULL,
	name VARCHAR NOT NULL,
    description VARCHAR,
    resource VARCHAR NOT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE rule_condition (
	uuid UUID NOT NULL,
	condition_group_uuid UUID,
	rule_uuid UUID,
	field_source VARCHAR NOT NULL,
    field_identifier VARCHAR NOT NULL,
    operator VARCHAR NOT NULL,
    value JSONB,
	PRIMARY KEY (uuid),
	FOREIGN KEY (condition_group_uuid) REFERENCES rule_condition_group(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
	FOREIGN KEY (rule_uuid) REFERENCES rule(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rule_action_group (
	uuid UUID NOT NULL,
	name VARCHAR NOT NULL,
    description VARCHAR,
    resource VARCHAR NOT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE rule_action (
	uuid UUID NOT NULL,
	trigger_uuid UUID,
	action_group_uuid UUID,
	action_type VARCHAR NOT NULL,
	field_source VARCHAR,
    field_identifier VARCHAR,
    action_data JSONB,
	PRIMARY KEY (uuid),
	FOREIGN KEY (action_group_uuid) REFERENCES rule_action_group(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
	FOREIGN KEY (trigger_uuid) REFERENCES rule_trigger(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rule_trigger_2_rule (
    rule_trigger_uuid UUID,
    rule_uuid UUID,
    PRIMARY KEY (rule_trigger_uuid, rule_uuid),
    FOREIGN KEY (rule_trigger_uuid) REFERENCES rule_trigger(uuid),
    FOREIGN KEY (rule_uuid) REFERENCES rule(uuid)
);

CREATE TABLE rule_trigger_2_rule_action_group (
    rule_trigger_uuid UUID,
    rule_action_group_uuid UUID,
    PRIMARY KEY (rule_trigger_uuid, rule_action_group_uuid),
    FOREIGN KEY (rule_trigger_uuid) REFERENCES rule_trigger(uuid),
    FOREIGN KEY (rule_action_group_uuid) REFERENCES rule_action_group(uuid)
);

CREATE TABLE rule_2_rule_condition_group (
    rule_uuid UUID,
    rule_condition_group_uuid UUID,
    PRIMARY KEY (rule_uuid, rule_condition_group_uuid),
    FOREIGN KEY (rule_uuid) REFERENCES rule(uuid),
    FOREIGN KEY (rule_condition_group_uuid) REFERENCES rule_condition_group(uuid)
);




