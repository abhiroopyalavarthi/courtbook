-- One row per member who has ever entered a tournament; holds their Elo rating.
create table players (
    user_id    bigint       primary key,
    name       varchar(100) not null,
    rating     int          not null default 1200,
    wins       int          not null default 0,
    losses     int          not null default 0,
    updated_at timestamptz  not null default now()
);

create table tournaments (
    id               bigserial    primary key,
    name             varchar(100) not null,
    starts_on        date         not null,
    signup_deadline  timestamptz  not null,
    status           varchar(16)  not null check (status in ('SIGNUP', 'IN_PROGRESS', 'COMPLETED')),
    created_by       bigint       not null,
    created_at       timestamptz  not null default now(),
    champion_user_id bigint,
    version          bigint       not null default 0
);

create table registrations (
    id            bigserial    primary key,
    tournament_id bigint       not null references tournaments (id),
    user_id       bigint       not null,
    user_name     varchar(100) not null,
    registered_at timestamptz  not null default now(),
    seed          int,
    unique (tournament_id, user_id)
);

create table matches (
    id            bigserial    primary key,
    tournament_id bigint       not null references tournaments (id),
    round         int          not null,
    position      int          not null,
    player1_id    bigint,
    player1_name  varchar(100),
    player2_id    bigint,
    player2_name  varchar(100),
    winner_id     bigint,
    score         varchar(64),
    bye           boolean      not null default false,
    completed_at  timestamptz,
    version       bigint       not null default 0,
    unique (tournament_id, round, position)
);

create table outbox_events (
    id           uuid        primary key,
    type         varchar(64) not null,
    payload      text        not null,
    -- clock_timestamp(), not now(): now() is fixed for the whole transaction,
    -- and events from one transaction must keep their order.
    created_at   timestamptz not null default clock_timestamp(),
    published_at timestamptz
);

create index outbox_unpublished on outbox_events (created_at) where published_at is null;
