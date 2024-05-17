CREATE TABLE rule_trigger_history (
	uuid UUID NOT NULL,
	trigger_uuid UUID NOT NULL,
    trigger_association_uuid UUID,
	conditions_matched BOOLEAN NOT NULL,
	actions_performed BOOLEAN NOT NULL,
	object_uuid UUID NOT NULL,
	triggered_at TIMESTAMP NOT NULL,
	message VARCHAR,
	PRIMARY KEY (uuid),
    FOREIGN KEY (trigger_uuid) REFERENCES rule_trigger(uuid) ON UPDATE CASCADE ON DELETE CASCADE

);

CREATE TABLE rule_trigger_history_record (
	uuid UUID NOT NULL,
	trigger_history_uuid UUID NOT NULL,
	rule_action_uuid UUID,
	rule_condition_uuid UUID,
	message VARCHAR,
	PRIMARY KEY (uuid),
	FOREIGN KEY (trigger_history_uuid) REFERENCES rule_trigger_history(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);



