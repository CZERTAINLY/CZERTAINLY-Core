create sequence compliance_profile_id_seq start 1 increment 1;
create sequence compliance_rule_id_seq start 1 increment 1;

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'complianceProvider',
    'COMPLIANCE_PROVIDER',
    'fb9daa96-e7d6-11ec-8fea-0242ac120002');

-- add required endpoints for the compliance and rules management in the connector
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/rules','GET','listRules',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd77d90-e7d7-11ec-8fea-0242ac120002'),
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/groups','GET','listGroups',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd77f34-e7d7-11ec-8fea-0242ac120002'),
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/groups/{uuid}','GET','listGroupDetails',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd78038-e7d7-11ec-8fea-0242ac120002'),
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/compliance','POST','checkCompliance',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd7811e-e7d7-11ec-8fea-0242ac120002');