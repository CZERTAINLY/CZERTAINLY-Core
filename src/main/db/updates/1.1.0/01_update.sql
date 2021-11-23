create sequence audit_log_id_seq start 1 increment 1;

create table audit_log
(
    id                int8 not null,
    i_author          varchar(255),
    i_cre             timestamp not null,
    i_upd             timestamp not null,
    additional_data   text,
    affected          varchar(255),
    object_identifier varchar(255),
    operation         varchar(255),
    operation_status  varchar(255),
    origination       varchar(255),
    primary key (id)
);
