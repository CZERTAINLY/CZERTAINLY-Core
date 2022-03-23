create sequence certificate_event_history_id_seq start 1 increment 1;

CREATE TABLE certificate_event_history (
	"id" BIGINT NOT NULL,
	"uuid" VARCHAR NOT NULL,
	"i_cre" TIMESTAMP NOT NULL,
	"i_upd" TIMESTAMP NOT NULL,
	"i_author" VARCHAR NOT NULL,
	"event" VARCHAR NOT NULL,
	"status" VARCHAR NOT NULL,
	"message" VARCHAR NOT NULL,
	"additional_information" VARCHAR NULL DEFAULT NULL,
	"certificate_id" BIGINT NOT NULL,
	PRIMARY KEY ("id")
)
;


alter table if exists certificate_event_history
    add constraint CERTIFICATE_HISTORY_TO_CERTIFICATE_KEY_1
    foreign key (certificate_id)
    references certificate
    ON UPDATE NO ACTION ON DELETE CASCADE;