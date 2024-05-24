-- drop old tables
DROP TABLE rule_condition;
DROP TABLE rule_condition_group;
DROP TABLE rule_2_rule_condition_group;
DROP TABLE rule;
DROP TABLE rule_action;
DROP TABLE rule_action_group;
DROP TABLE rule_trigger_2_rule;
DROP TABLE rule_trigger_2_rule_action_group;
DROP TABLE rule_trigger_2_object;
DROP TABLE rule_trigger_history_record;
DROP TABLE rule_trigger_history;
DROP TABLE rule_trigger;

CREATE TABLE trigger (
	uuid UUID NOT NULL,
	name TEXT NOT NULL,
	description TEXT,
	type TEXT NOT NULL,
	resource TEXT NOT NULL,
	ignore_trigger BOOLEAN NOT NULL DEFAULT FALSE,
	event TEXT,
	event_resource TEXT,
	PRIMARY KEY (uuid)
);

CREATE TABLE rule (
	uuid UUID NOT NULL,
	name TEXT NOT NULL,
	description TEXT,
    resource TEXT NOT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE condition (
	uuid UUID NOT NULL,
	name TEXT NOT NULL,
	description TEXT,
	type TEXT NOT NULL,
	resource TEXT NOT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE condition_item (
	uuid UUID NOT NULL,
	condition_uuid UUID NOT NULL,
	field_source TEXT NOT NULL,
    field_identifier TEXT NOT NULL,
    operator TEXT NOT NULL,
    "value" JSONB,
	PRIMARY KEY (uuid),
	FOREIGN KEY (condition_uuid) REFERENCES condition(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE "action" (
	uuid UUID NOT NULL,
	name TEXT NOT NULL,
    description TEXT,
    resource TEXT NOT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE execution (
	uuid UUID NOT NULL,
	name TEXT NOT NULL,
	description TEXT,
	type TEXT NOT NULL,
	resource TEXT NOT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE execution_item (
	uuid UUID NOT NULL,
	execution_uuid UUID NOT NULL,
	field_source TEXT NOT NULL,
	field_identifier TEXT NOT NULL,
	data JSONB,
	PRIMARY KEY (uuid),
	FOREIGN KEY (execution_uuid) REFERENCES execution(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rule_2_condition (
    rule_uuid UUID NOT NULL,
    condition_uuid UUID NOT NULL,
    PRIMARY KEY (rule_uuid, condition_uuid),
    FOREIGN KEY (rule_uuid) REFERENCES rule(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (condition_uuid) REFERENCES condition(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE action_2_execution (
    action_uuid UUID NOT NULL,
    execution_uuid UUID NOT NULL,
    PRIMARY KEY (action_uuid, execution_uuid),
    FOREIGN KEY (action_uuid) REFERENCES action(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
	FOREIGN KEY (execution_uuid) REFERENCES execution(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE trigger_2_rule (
    trigger_uuid UUID NOT NULL,
    rule_uuid UUID NOT NULL,
    PRIMARY KEY (trigger_uuid, rule_uuid),
    FOREIGN KEY (trigger_uuid) REFERENCES trigger(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (rule_uuid) REFERENCES rule(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE trigger_2_action (
    trigger_uuid UUID NOT NULL,
    action_uuid UUID NOT NULL,
    PRIMARY KEY (trigger_uuid, action_uuid),
    FOREIGN KEY (trigger_uuid) REFERENCES trigger(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE trigger_association (
    uuid UUID NOT NULL,
    trigger_uuid UUID NOT NULL,
    resource TEXT NOT NULL,
    object_uuid UUID NOT NULL,
    trigger_order INT,
    PRIMARY KEY (uuid),
    FOREIGN KEY (trigger_uuid) REFERENCES trigger(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE trigger_history (
	uuid UUID NOT NULL,
	trigger_uuid UUID NOT NULL,
    trigger_association_object_uuid UUID,
	object_uuid UUID,
	reference_object_uuid UUID,
	conditions_matched BOOLEAN NOT NULL,
	actions_performed BOOLEAN NOT NULL,
	triggered_at TIMESTAMP NOT NULL,
	message TEXT,
	PRIMARY KEY (uuid),
    FOREIGN KEY (trigger_uuid) REFERENCES trigger(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE trigger_history_record (
	uuid UUID NOT NULL,
	trigger_history_uuid UUID NOT NULL,
	condition_uuid UUID,
	execution_uuid UUID,
	message TEXT NOT NULL,
	PRIMARY KEY (uuid),
	FOREIGN KEY (trigger_history_uuid) REFERENCES trigger_history(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
	FOREIGN KEY (condition_uuid) REFERENCES condition(uuid) ON UPDATE CASCADE ON DELETE SET NULL,
	FOREIGN KEY (execution_uuid) REFERENCES execution(uuid) ON UPDATE CASCADE ON DELETE SET NULL
);
