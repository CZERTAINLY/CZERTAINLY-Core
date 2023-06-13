alter table scheduled_job ADD PRIMARY KEY (uuid);
alter table scheduled_job_history ADD PRIMARY KEY (uuid);
alter table scheduled_job_history DROP COLUMN job_name;
alter table scheduled_job_history ADD scheduled_job_uuid uuid;
alter table scheduled_job_history ADD FOREIGN KEY (scheduled_job_uuid) REFERENCES scheduled_job(uuid);
alter table scheduled_job_history ADD exception_message varchar(255);