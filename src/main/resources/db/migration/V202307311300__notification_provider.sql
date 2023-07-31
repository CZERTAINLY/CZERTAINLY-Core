insert into function_group (name, code, uuid) values (
    'notificationProvider',
    'NOTIFICATION_PROVIDER',
    '159fde5a-7438-44ed-97ad-028094ab083f');

-- add required and optional endpoints for the notification provider connectors
insert into endpoint (context,method,name,required,function_group_uuid,uuid) values
('/v1','GET','listSupportedFunctions',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'995801fd-cef2-4470-9fc6-1e13d21bc72a'),
('/v1/health','GET','healthCheck',false,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'91fc5a07-f6b5-43f4-947b-7b2596305ee2'),
('/v1/notificationProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'d1d809d1-2534-44e2-921e-7588c2855540'),
('/v1/notificationProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'f9581e8f-70d7-4f09-9c7c-c47000719fef'),
('/v1/notificationProvider/{kind}/attributes/mapping','GET','listMappingAttributes',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'989f80bf-7223-4cf3-a1ee-336cc158cad0'),
('/v1/notificationProvider/notifications','GET','listNotificationInstances',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'85b5e134-07ff-4ea2-a939-1f5936ec80ac'),
('/v1/notificationProvider/notifications/{uuid}','GET','getNotificationInstance',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'c6a3e340-baf8-435d-88b9-4be7f628e4c7'),
('/v1/notificationProvider/notifications','POST','createNotificationInstance',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'0a265bf3-f0f9-47ad-a1c6-5f50722f7ab5'),
('/v1/notificationProvider/notifications/{uuid}','PUT','updateNotificationInstance',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'e3578a7e-8f4d-4d4a-8b79-0589235b0e07'),
('/v1/notificationProvider/notifications/{uuid}','DELETE','removeNotificationInstance',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'e2a999e5-821a-4a66-922b-0da30251fb09'),
('/v1/notificationProvider/notifications/{uuid}/notify','POST','sendNotification',true,(select uuid from function_group where code = 'NOTIFICATION_PROVIDER'),'59d58d7a-3a82-42b9-8cb1-fcc14ba27b94');
