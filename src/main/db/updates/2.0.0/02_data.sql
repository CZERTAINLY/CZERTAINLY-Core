insert into function_group (id, name, code) values (
    nextval('function_group_id_seq'),
    'credentialProvider',
    'CREDENTIAL_PROVIDER');

insert into function_group (id, name, code) values (
    nextval('function_group_id_seq'),
    'caConnector',
    'CA_CONNECTOR');

insert into function_group (id, name, code) values (
    nextval('function_group_id_seq'),
    'legacyCaConnector',
    'LEGACY_CA_CONNECTOR');

insert into function_group (id, name, code) values (
    nextval('function_group_id_seq'),
    'discoveryProvider',
    'DISCOVERY_PROVIDER');

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listAttributeDefinitions',
    '/v1/attributes',
    'GET',
    true,
    (select id from function_group where code = 'CREDENTIAL_PROVIDER'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'validateAttributes',
    '/v1/attributes/validate',
    'POST',
    true,
    (select id from function_group where code = 'CREDENTIAL_PROVIDER'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listSupportedFunctions',
    '/v1',
    'GET',
    true,
    (select id from function_group where code = 'CREDENTIAL_PROVIDER'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listAttributeDefinitions',
    '/v1/caConnector/{kind/}attributes',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'validateAttributes',
    '/v1/caConnector/{kind}/attributes/validate',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listSupportedFunctions',
    '/v1',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));



insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listCAInstances',
    '/v1/caConnector/authorities',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'getCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'createCAInstance',
    '/v1/caConnector/authorities',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'updateCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'deleteCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'DELETE',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));


insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'issueCertificate',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/certificates/issue',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'revokeCertificate',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/certificates/revoke',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));


insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listEndEntities',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/endEntities',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'getEndEntity',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'createEndEntity',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/endEntities',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'updateEndEntity',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}',
    'POST',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'removeEndEntity',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}',
    'DELETE',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'resetPassword',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}/resetPassword',
    'PUT',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));


insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listEntityProfiles',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listCertificateProfiles',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileId}/certificateprofiles',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listCAsInProfile',
    '/v1/caConnector/authorities/{authorityId}/endEntityProfiles/{endEntityProfileId}/cas',
    'GET',
    true,
    (select id from function_group where code = 'LEGACY_CA_CONNECTOR'));





insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listAttributeDefinitions',
    '/v1/caConnector/{kind/}attributes',
    'GET',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'validateAttributes',
    '/v1/caConnector/{kind}/attributes/validate',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));



insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listCAInstances',
    '/v1/caConnector/authorities',
    'GET',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'getCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'GET',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'createCAInstance',
    '/v1/caConnector/authorities',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'updateCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'deleteCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'DELETE',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'deleteCAInstance',
    '/v1/caConnector/authorities/{authorityId}',
    'DELETE',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));


insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listRAProfileAttributes',
    '/v1/caConnector/authorities/{authorityId}/raProfiles/attributes',
    'GET',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));


insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'issueCertificate',
    '/v2/caConnector/authorities/{authorityId}/certificates/issue',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listIssueCertificateAttributes',
    '/v2/caConnector/authorities/{authorityId}/certificates/issue/attributes',
    'GET',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'validateIssueCertificateAttributes',
    '/v2/caConnector/authorities/{authorityId}/certificates/issue/attributes/validate',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'renewCertificate',
    '/v2/caConnector/authorities/{authorityId}/certificates/{certificateId}/renew',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'listRevokeCertificateAttributes',
    '/v2/caConnector/authorities/{authorityId}/certificates/revoke/attributes',
    'GET',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'validateRevokeCertificateAttributes',
    '/v2/caConnector/authorities/{authorityId}/certificates/revoke/attributes/validate',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'revokeCertificate',
    '/v2/caConnector/authorities/{authorityId}/certificates/{certificateId}/revoke',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));

insert into endpoint (id, name, context, method, required, function_group_id) values (
    nextval('endpoint_id_seq'),
    'revokeCertificate',
    '/v2/caConnector/authorities/{authorityId}/certificates/{certificateId}/revoke',
    'POST',
    true,
    (select id from function_group where code = 'CA_CONNECTOR'));









update ra_profile set ca_instance_ref_id = <doplnit_id_ze_selectu_podle_jmena>;

-- pro jistotu funkcni castaveni EJBCA
-- insert into ca_instance (id, credential, url, i_cre, i_upd) values (1, '[{"id":"e334e055-900e-43f1-aedc-54e837028de0","name":"keyStoreType","type":"list","required":true,"readOnly":false,"editable":true,"visible":true,"multiValue":false,"description":null,"validationRegex":null,"dependsOn":null,"value":"PKCS12"},{"id":"6df7ace9-c501-4d58-953c-f8d53d4fb378","name":"keyStore","type":"file","required":true,"readOnly":false,"editable":true,"visible":true,"multiValue":false,"description":null,"validationRegex":null,"dependsOn":null,"value":"MIIQ/QIBAzCCELYGCSqGSIb3DQEHAaCCEKcEghCjMIIQnzCCBXsGCSqGSIb3DQEHAaCCBWwEggVoMIIFZDCCBWAGCyqGSIb3DQEMCgECoIIE+zCCBPcwKQYKKoZIhvcNAQwBAzAbBBRhV6j8tLr874u8bH1+FEJ/tblc1wIDAMgABIIEyGAYg+3UUe1RY124/haDPJ2Xn/QJJJT6YmWK6ylIzeCBc/Sv4t1annKUuJXrB6gDMIEEbBrufoTqsyQhg+5JH1cDI7XVGrc0PB1RTONyqjyqnUIWngEoQgBKA8eopS8M7MWbENWZc8+TdmPwGPhqyQ3Bt6LaYHuYjTSkE9v82X1qgOkq3MVH06EkgiS5xl2F2UC/UAp9PX6hFJOb37DDDgqWEM1ub0HgKwqc+TbIekFdQ2QXdKZCqrgV8Oq00eWBHmNe8F5GWHB4/5n1jQLnY/jnAcxLDzltUlkaL/9BdbpFd2q8P/Jsd/Q+b4l03t4TbO9TMhTA328gOMUHjIjPfrvP+8xCeAda602XohJ18GIJLaRzqDUZIKpf2NPG85AAyAk1Pv6nMGBPL36HppORiPIJoscQMryPhV7iHCQlwz3eftxnsWw1DS/tT3smK5z9a4F1TFyN4luuSAXaAZjgm86yrmrQbitvYt63yInzzyvoliTV33K5dp0dBYLjRZBbGLmvqQw+SNPH048YBp1oKjeRKKIThaYQgJEQpkDS+gNWtTtIQeJfdJ1w3Pw81tN7gtcV8QKOgRi86AeaGCuzabtt6wKKF0V6JG2eh4T/FtANTFa4VbW43xlJfEBEbHb0VsvMPYMoIhwUnxoAMDdDufzZKD3QSdo8rZp4wedTH0EKErCBXHZ6+PhsBm+NJt1KpF0V/1U8BGmqwk/eT4odnjn/grfo5A2Mt3qHymJ6lUNWjvDfFcNIXKm5aIry/7/oKFFwLxiLsaKQONT8s0fuGpl82tbwVJMz6Hi34X5Ls3eNIiHX2ZlTiB+yb+OEMENKakHdSGx3np9llp/WMWS2HVSV+pkzkV3E4ciJIgoDbUbupXI/ivJw/0IAExF3uJ7O5QoYwboND+M+P0Bz7Gy3R50tigUj7pN+PtjI1T6TwkpbSab9s+Rwx/h4XAPU4HU6C7nPbe6UpOC/liqDi4DGfI3L8aeB54w+eBgrIjiKIy2ZyEAFTkfB1/+ljl4VU8SY2HOr8UgJxpYiJAGY9hULtRoAsS3ajK9Rb2ZE4Lx1Hzxj6k0sdEIShEgsgqm+53zAjtgDmRvcg5ZebH7Cl8CBLarqpJp+lGptditR08M+abaLcO1WrMUwNYPBpvmh9dfOk8KkxrFnLFEWpoZrw+5jYuMGSvbO2iokhLzuAFYd0xZMt9LWqCBe2AifB0UrsF8YEiGd2mZfWFzMgugS0W8NHmQgdDjR6MWpp1puM8C7tcgYAOW4vNs++5WDI6CjGefMnjkpAfvV0iErVcoNkCKCcsd3I/1/VzMFXReGrm6fexaaINT/SSFnVUXjtfvrEZWBuGW3l61Sqxf1+9NfXD6QwkKY9gHL3QVvB52e/ZU66gEx99CrtaeHXHRovJYjxdXzERqitra7yr+DQlkTYdS80y4s9ZYznqFu6hxMbLzCuzI/qYcELriPFpw+gxxPFuQ1N/wtyT5seFDRm9gaGYWkxpngUXecEHX75ng3WdzKwVfCFX2qYm3rSJSxK9dkCDYad2NAXewUeCvm0/jipoJ0OnO0Sks97pNdm5Xo+Z/E2NfvSOJpBGW8HlrlQZL2pNQ0E5r6+YVTm3m+wDI94qN4s7E1S/alv430JTFSMCMGCSqGSIb3DQEJFTEWBBQxQpRpo7sAukBgDGNTJVvqyAgI5jArBgkqhkiG9w0BCRQxHh4cAGMAbABvAHUAZABmAGkAZQBsAGQAZABlAG0AbzCCCxwGCSqGSIb3DQEHBqCCCw0wggsJAgEAMIILAgYJKoZIhvcNAQcBMCkGCiqGSIb3DQEMAQYwGwQUAx8gcvv+7hG/JTE8gTQLDLpbBewCAwDIAICCCsi3A6V/onAl96FuFD+W5QbxR4f/bYLqcw4ks8aoA+CBO5LQ/loq+1D8nmLw4pbqU0S0oWXBjDg/AFUzYzx+QDdK1n/Y3POuNi1rRu8T9L4z3LI5T/VjduiqRCKdgsS16JvnMt9cWKkDUOlRK+kaJddC20TH+A3iLqnRincu4qBXGLuLzlaRXM7DRPamCysp0y+h0YybGyHxGUAsikat2Ct0fPyaP9GeJKBoLTkwegkXolkkyDXji8tDmcZSfWqMJhRCo9JmekFXE7Ly/UkkbDmzMi35r4nXFw9WrEO9H7HAkQj4ekG4CLEqypn17YK06nKzg4gP7YI/ZHBDssNkxYQYzfuRDitGVfC1Ww3xBanB3LOgTAJYuBlLIxzL+oBnmSuQY9kucZfKNVDbezEacyiJXSvc6u9AIzUzijMO9fTHQkxEq4TnCtIF5EPtdRdVHOgpnPXm5yWvlZ93PkwZ6BayJdw2MbQMlCLUCsdbbF+t8HQnUJWeT8N3k+GOHwpgUNBJk8PZws0KofnVlyQW49WW6pdn0okZjSmptCgb/jB54HoLdY5je0wZz1L1X3yxrLVjV64pOIWzruem9v3IDPtL629O0Cc1YnxkICCtWrNWm4gyo3jODvY7kqGGl6mkzsL5dcukGYuNMpkrNlc9lKrZwpDLpjtErsSpvalIKthn15E1IIOWCu4Na/azypDgbQzfemRF7PMFxmLrZ8Grg/xeOKihco2SEIXqtQl+0IB7OY3Q7TY91nRfqrstovw2q5qa1IX6iWAxPUOYpt9FBSDsGOuGj8fgXMecwoKUfjZFnocIf93anN3Ys/1TDMyhMmqErRSoYo+Aj/X+uxoc35ptgeV1iwC/M/n5orOdpL95w8033VkhuIWr0fiwE25QilqlhWcSpQH3xcz8rCLBc2KV5Q/LAGUF9BM0wxWcvctRqjelUSFzKTIEmmX6vALOwJhU0tkJimqy9apiFlA2xwKKeC/+Q0eFYqvweounZ6WWdoTcNriLIgY93ALO8b6pUJrozBJQkWmfh3yx7jd/Cbc6LeufuQ36tRqjXy1n6dcMsmGgikfhGCn9afUlBiGdI3VHfJo321sRrgUDmTvIpqqjTGu/O/yoBUvdX2hBRgVT6C2uEJs/H71qaAlQ6cL2xAaNbQnfGKEozR57sSqroubiGth6uCNyg5VDzVHFu/6hxti6Su9b3aPHR89BjOxHGEkg7y/o+cGdoARuzuzu+Z9eT4RLOvAtkZVIkU4l/TBV7X1YE9sJAd2DVu0r5gbBQDVLwtarSvw0l4ZZDLzhv7b6CCFNhkdz6FTolhwPBgIxpqD/71lnxWSetO4GiSCOJkkuPjrsdArhcNvZDTWrEQtmY2lpzTn88OLe5TJkg3W3IL9g0st5M4QLH+86AiZFcdiOkcySNJLxAT23UHmUZsccTPfh0QXR8hJu7jaWig+41nQhrDPlImXaum3KPsTBUPLKAY9Wg6GYotSgNnEjZzfpc0IFrDZZTOUEY4TYODXckWUCsOC1sLXvuRwYuaCov/JRuAviNFbDLmjupECvvIKuyvbK/U+t1v4PFVkXG4pg4zyqZIwELMKaXIx/M+gFNGCPVljHoJ1dL/0xbu6+aQf/gt2T9UNoLRPJK0yRahhDUltCbY/YSkd05B7rJEzsUGzo3kfC0JK+oSySUUjv9P0KQCKYoJFDWF/TTXEXVHFfs4aFilX8Z0/zmusLEkdwk6fQ+Nmb3L0/41alrnAYKCwnXtwxNF2w/kLYNEFXYIhiTOtUB4YFQPB54GAOR7AmETIITl8BRDm6BqhJuxnFVG3GX5N6jEoKg4nHhGZgTFuiswxDxGSVxWeq87iAkHfTuKS1Zz/xh2zo8AElFGwFveHksnKyl8TltiuAydAo4Eu4UF62/DHQWy1J7q3gl3e4prwn/UiWsn5j6XScFtrCK+PQ8FMKpVfE2lyZNZ/3MUW+wEsVKquis23dIK+q4ZWjqDbHWEd8Ui5bPMnJjDNa0UgUb1qChXS3EOtAAVGN68radj8dGvRrsClgj80xrGSkF2jkbmntySHsU3AchiVqucyLbK5SogA186efMM2IGmkqWssNH+ZJ4NSvgShVJUqEh+amJ//A7C+XR6ShQUkGasrfVcf7a7/1OVGjdOAZV93AoYi65neZYeDNadOu9ONsztspcqwroV7K+1SJshNctyMuIk8TGnYIXS82Eie93Iy6asLmn5cvBF7cCOX77ZGleDencJe+wKo1VUX9kadX6fw3Qx4jdCMuBvn6JSnN36UVr/ubvmtg9bPsrAq/M2dayVgmJlgc6mPSE7KPUG18aiDiJEXVs9wT3ZsyfTtj+/zxbfPJKIYP+ZalSlbKZoGQf0wh+8+PnkGgvRXGTnlbPydtQ1N/f/7LYN5/DX+kcjhOa+nijbZK9HZSo4ro+yr1qx0IX/nsKUel9Grn0ZFK6+dQx9xgLXycpbWGub72/Y/JWpLvLD38ZtkuEl5z2As3ogELzo2mKNdI23jlgET/o1rOxW6NntCJfhaE0FjXNE7mvvd6LAvjOwweQSB9nddJzLtFQBnA4v6dDhV2ZQwHtBOKepFs3zChoR68BdiuFJxnwFz5Ttzmo/uEo9DLhOzVSZysNUawyE9ah64BoYPugIuDVMDwm9OJ3ZmlZQNdXwqYzViWkc3+CbEdplcqWV+4ibasojwo/2B+YqHTs+wMOXtOAvcaOpMHV2K8mEZeCzyD/XC8efMLoa8R8GL3nCtIiwlPfsnvlnmK6OoFDnHY2AJRh7P0MagrUWeaRIQnPbbL4Pq5jLuPyRpwr6P1HmFL7JS6IP/bZp5ygY10SiJiw8umsQhtKoGtnwt95yXa5hMoKJ8c3tTiKBDCzeixq/Jlk0du5XmHUrDMXA69CCoQ8eTR+boUndsFFSBB7hE5SloKrE6miTFSaiilKEtvRzEXoEdseJJljmjhh3P4D5g7d4UPXK9ezDo0/s7IXHGH87sgFXh/JkKRhvvgsb39bFXRxnIhbNMyusk1hlNwACQvtMSWHtmAwZCXGoc/jJZxil92xRs14bjxMP1hMvH+J6dibZIo+KbB8XmUbb9RnuB3JYvsr+6kXPV0ayKVVt4xzJl7wyT9rstnm7Mbc6a8yAAiy3XpysOGPgT8aPYkY3qoCl7TIQZoDNRr6PwL8LUIkpDF1g/0P5EiLXCQZKwLNyQke3bc/noDgPaLtzTg4lkCd1G5gXYkylhSncP75BIX2oIjPiSnGdY+VM/L3pvgIBlbnBsChO1heZgYQioNCgG5pl8IyBYvg6VRyFbLFNOfZxICDvuXscBDqjADXDxszEdbGuQaMSxqIo1M0eWumq7WWNlNtwrc3R+hYU7j4MakwBVHFOCNNmPRIusMkWkg1e4EGPMw2Q2cC4EoXGlIrW3DeiCIxwfRjAw4CgnwFYrZFJh5qD6FHFpQM7ZyZQuoQGVMyfB8k3W3jjnbbgdO47FdLEcmKBQ8KUNKXyPDloThfbm8u6csK28u/pBF8eStigrthv2/2Cv2E0S0496NI1PwU+MxTZl8bLOguIAJQzihmOVEx5xTEiDqcxxKe9wP8W9PeDnpp2R9Z7fZp4u5dgiMDkUXeZO7PSbEqK57b8I85tGoyOxeZKH52lJccEv1jEC6WsX/J619WhM1gRCumKquISpZwsC7jSmYQJEwPjAhMAkGBSsOAwIaBQAEFGFA5IUcwrnqVTrlrjYu1bEz5g2kBBTVydcTSrrzDRNLWhWoJxdny8qQDwIDAZAA"},{"id":"d975fe42-9d09-4740-a362-fc26f98e55ea","name":"keyStorePassword","type":"secret","required":true,"readOnly":false,"editable":true,"visible":true,"multiValue":false,"description":null,"validationRegex":null,"dependsOn":null,"value":"*GBkTvvKy8z!Z@7"}]', 'https://lab01.3key.company/ejbca/ejbcaws/ejbcaws?wsdl', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);


update ra_profile set attributes =
    '[' ||
    '{ "name": "tokenType", "value": "' || token_type || '" },' ||
    '{ "name": "endEntityProfile", "value": { "id": 0, "name": "' || end_entity_profile_name || '" } },' ||
    '{ "name": "certificateProfile", "value": { "id": 0, "name": "' || certificate_profile_name || '" } },' ||
    '{ "name": "certificationAuthority", "value": { "id": 0, "name": "' || ca_name || '" } },' ||
    '{ "name": "sendNotifications", "value": ' || send_notifications || ' },' ||
    '{ "name": "keyRecoverable", "value": ' || key_recoverable || ' }' ||
    ']'
where attributes is null;

-- If function uuid_generate_v4() not available
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

update admin set uuid = uuid_generate_v4() where uuid is null;
update audit_log set uuid = uuid_generate_v4() where uuid is null;
update client set uuid = uuid_generate_v4() where uuid is null;
update ra_profile set uuid = uuid_generate_v4() where uuid is null;