update certificate set source_certificate_uuid = null where source_certificate_uuid not in (select uuid from certificate);

alter table certificate add constraint certificate_source_certificate_uuid_fk foreign key (source_certificate_uuid) references certificate(uuid) on delete set null;

update certificate_event_history set additional_information = concat('{"message": "', additional_information, '"}') where additional_information is not null and additional_information <> '' and not (additional_information like '{"%');