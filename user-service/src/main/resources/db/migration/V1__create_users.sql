create table users (
    id            bigserial    primary key,
    email         varchar(254) not null,
    name          varchar(100) not null,
    password_hash varchar(100) not null,
    role          varchar(16)  not null check (role in ('MEMBER', 'ADMIN')),
    created_at    timestamptz  not null default now()
);

-- Emails are stored lowercased; the index enforces it case-insensitively anyway.
create unique index users_email_uk on users (lower(email));
