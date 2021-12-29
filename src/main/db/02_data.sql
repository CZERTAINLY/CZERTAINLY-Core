insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'credentialProvider',
    'CREDENTIAL_PROVIDER',
    'e8ae0a8c-ed12-4f63-804f-2659ee9dff6e');

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'authorityProvider',
    'AUTHORITY_PROVIDER',
    '736b0fd6-5ea0-4e10-abe7-cfed39cc2a1a');

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'legacyAuthorityProvider',
    'LEGACY_AUTHORITY_PROVIDER',
    '435ee47f-fd03-4c50-ae6f-ca60f4829023');

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'discoveryProvider',
    'DISCOVERY_PROVIDER',
    'a6c4042f-9465-4476-9528-1efd6caaf944');

insert into public.endpoint (id,context,"method","name",required,function_group_id,uuid) values
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','GET','listAuthorityInstances',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'e3521dd0-e150-4676-a79c-30a33e62889c'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','GET','getAuthorityInstance',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'924ac89a-7376-4ac8-8c15-ecb7d9e8ca16'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','POST','createAuthorityInstance',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'9bf9cd3b-73de-4c1c-a712-7396e9dc78e5'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','POST','updateAuthorityInstance',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'51c5b673-0e6e-4b8d-a31b-1b35835b4025'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','DELETE','removeAuthorityInstance',false,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'f83d858a-d63b-48e7-b22c-fdb7f7e3d9b1'),
	 (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'ecdf6214-a491-4a0f-9084-7b502a16315e'),
	 (nextval('endpoint_id_seq'),'/v1/discoveryProvider/discover','POST','discoverCertificate',true,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'784f8681-e3ea-4d8d-938a-ce315752cd80'),
	 (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'eb8645ee-5def-4b77-8c66-f8c85da88132'),
	 (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'CREDENTIAL_PROVIDER'),'886eee93-8a82-4fa0-bee0-60eb4bed766f'),
	 (nextval('endpoint_id_seq'),'/v1/credentialProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select id from function_group where code = 'CREDENTIAL_PROVIDER'),'45c825bb-5e3a-42f4-8808-9107d4966078');
insert into public.endpoint (id,context,"method","name",required,function_group_id,uuid) values
	 (nextval('endpoint_id_seq'),'/v1/credentialProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select id from function_group where code = 'CREDENTIAL_PROVIDER'),'eb159884-f7ee-457e-918c-5f7f6e2f2597'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'072349d4-d1a0-4398-b4e5-88fba454d815'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'59070334-e550-466c-b538-bd8d2d9b06e5'),
	 (nextval('endpoint_id_seq'),'/v1/discoveryProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'61f1096e-5798-40cd-874b-de7b782f1d17'),
	 (nextval('endpoint_id_seq'),'/v1/discoveryProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'8995b6ef-ea7c-417b-a298-f0a4a8a4f55c'),
	 (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'cf4af237-164e-4326-8a34-80c90d53b2d7'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities','GET','listEndEntities',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'5a78b374-3113-4310-a35d-45a8a2a04eca'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes/validate','POST','validateAttributes',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'ca07a81d-724f-4304-8ffa-3cb405766301'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes','GET','listAttributeDefinitions',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'ca0595ad-36e5-4060-a19d-e80b8f7461fd'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}','DELETE','removeEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'e881624f-af84-41fd-aeb8-a90e342bb131');
insert into public.endpoint (id,context,"method","name",required,function_group_id,uuid) values
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}/resetPassword','PUT','resetPassword',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'f2a6f043-3fb2-4f9d-9996-ce8cf68d2ad9'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles','GET','listEntityProfiles',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'e13b274b-bdbd-4b4d-a5fa-875f0a6594e9'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileId}/certificateprofiles','GET','listCertificateProfiles',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'b2a2a828-598b-47dd-a1c5-ce877989153f'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileId}/cas','GET','listCAsInProfile',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'4bef1a55-4725-48af-911e-9a051784c4c4'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/certificates/revoke','POST','revokeCertificate',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'656e4414-d735-457f-ad43-f921c5af4507'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/certificates/issue','POST','issueCertificate',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'a91dd6df-cd2c-46f4-af09-3693a167118d'),
	 (nextval('endpoint_id_seq'),'/v1/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}','GET','getEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'a8b1d647-6a8e-46fd-b4e1-844b30df4dcc'),
	 (nextval('endpoint_id_seq'),'/v1/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities','POST','createEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'5f61c054-0d68-44b1-b326-2ed28a2a55fa'),
	 (nextval('endpoint_id_seq'),'/v1/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}','POST','updateEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'57320a6d-3763-4a25-bdae-4a2a92a67487'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','DELETE','removeAuthorityInstance',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'b3592167-af2a-44b3-89d2-e4bfd000caa4');
insert into public.endpoint (id,context,"method","name",required,function_group_id,uuid) values
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','POST','createAuthorityInstance',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'6350c3bb-57ef-4416-964b-0254df28131e'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','GET','getAuthorityInstance',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'1692cec0-50aa-46a3-be7f-b32e6a752d2a'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','GET','listAuthorityInstances',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'cb1ae7eb-a97b-44bd-bf76-46ae96e32985'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','POST','updateAuthorityInstance',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'06f1f14f-328b-40f7-8f34-f168619e3a3a'),
	 (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/raProfile/attributes','GET','listRAProfileAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'e43155b6-51ad-46e0-a60c-176ee5e6dfea'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/issue','POST','issueCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'065dbfba-63f9-4011-abe4-f2ca6d224521'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/issue/attributes','GET','listIssueCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'0288132c-5d9c-4db8-97a1-7ef977b45b17');
insert into public.endpoint (id,context,"method","name",required,function_group_id,uuid) values
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/issue/attributes/validate','POST','validateIssueCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'355d306e-75f7-4b85-848b-58bddf95c582'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/{certificateId}/renew','POST','renewCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'efdb9bcd-4f7c-473b-8704-77b12b3f6d33'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/revoke/attributes','GET','listRevokeCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'2dcc528b-9e16-46c6-877e-74eae258173f'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/revoke/attributes/validate','POST','validateRevokeCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'f28a2c14-1183-430d-a908-85bcfda56dab'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/{certificateId}/revoke','POST','revokeCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'d9e162ae-2d50-4e98-bc37-62d015c43199'),
	 (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/{certificateId}/revoke','POST','revokeCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'7085fad6-df6e-4697-9c8e-7c80c2a12bd7');
