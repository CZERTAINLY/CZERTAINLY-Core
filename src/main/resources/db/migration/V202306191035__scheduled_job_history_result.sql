ALTER TABLE scheduled_job RENAME COLUMN one_shot_only TO one_time;
ALTER TABLE scheduled_job_history RENAME COLUMN exception_message TO result_message;
ALTER TABLE scheduled_job_history ADD result_object_type text NULL;
ALTER TABLE scheduled_job_history ADD result_object_identification text NULL;
