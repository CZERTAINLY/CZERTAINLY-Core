create sequence if not exists proxy_id_seq start 1 increment 1;

create table if not exists proxy
(
    id          int8         not null,
    uuid        varchar(255),
    i_author    varchar(255),
    i_cre       timestamp    not null,
    i_upd       timestamp    not null,
    name        varchar(255) not null,
    description text,
    code        varchar(255) not null,
    status      varchar(255) not null,
    primary key (id)
    );

alter table if exists connector
    add column proxy_id int8;

alter table if exists connector
    add constraint connector_to_proxy_key
    foreign key (proxy_id)
    references proxy;