alter table certificate add column source_certificate_uuid uuid default null;

with parent(parent_uuid, certificate_uuid) as (
	select (additional_information::json->>'Parent Certificate UUID')::uuid as parent_uuid, certificate_uuid
	from certificate_event_history ceh
	where ceh.additional_information like '%Parent Certificate UUID%'
)
update certificate c set source_certificate_uuid = p.parent_uuid from parent p where c.uuid = p.certificate_uuid;
