alter table scep_profile add column intune_enabled BOOLEAN NULL DEFAULT NULL;
alter table scep_profile add column intune_tenant varchar NULL DEFAULT NULL;
alter table scep_profile add column intune_application_id varchar NULL DEFAULT NULL;
alter table scep_profile add column intune_application_key varchar NULL DEFAULT NULL;
