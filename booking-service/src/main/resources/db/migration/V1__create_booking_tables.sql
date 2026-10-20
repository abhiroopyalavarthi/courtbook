create table courts (
    id     bigserial   primary key,
    name   varchar(50) not null unique,
    active boolean     not null default true
);

insert into courts (name) values ('Court 1'), ('Court 2'), ('Court 3'), ('Court 4');

-- Slots are created on demand when someone looks at a day (see SlotQueries).
create table slots (
    id        bigserial   primary key,
    court_id  bigint      not null references courts (id),
    starts_at timestamptz not null,
    ends_at   timestamptz not null,
    -- Bumped by every operation that changes who holds the slot or who is next
    -- in line, so two of them racing on the same slot can't both commit.
    version   bigint      not null default 0,
    unique (court_id, starts_at),
    check (ends_at > starts_at)
);

create index slots_starts_at on slots (starts_at);

create table bookings (
    id           bigserial    primary key,
    slot_id      bigint       not null references slots (id),
    user_id      bigint       not null,
    user_name    varchar(100) not null,
    status       varchar(16)  not null check (status in ('ACTIVE', 'CANCELLED')),
    source       varchar(16)  not null check (source in ('DIRECT', 'WAITLIST')),
    created_at   timestamptz  not null default now(),
    cancelled_at timestamptz,
    -- Two cancels of the same booking at once: the second one's update finds
    -- the version already changed and fails instead of cancelling twice.
    version      bigint       not null default 0
);

-- The double-booking guarantee. At most one ACTIVE booking per slot, enforced
-- by Postgres no matter how many requests race. Checking in Java first is not
-- enough: two requests can both see the slot as free before either inserts.
create unique index bookings_one_active_per_slot on bookings (slot_id) where status = 'ACTIVE';

create index bookings_by_user on bookings (user_id, created_at desc);

create table waitlist_entries (
    id         bigserial    primary key,
    slot_id    bigint       not null references slots (id),
    user_id    bigint       not null,
    user_name  varchar(100) not null,
    status     varchar(16)  not null check (status in ('WAITING', 'PROMOTED', 'LEFT')),
    created_at timestamptz  not null default now()
);

create unique index waitlist_one_entry_per_user on waitlist_entries (slot_id, user_id) where status = 'WAITING';
create index waitlist_queue on waitlist_entries (slot_id, created_at, id) where status = 'WAITING';

-- Transactional outbox: events are written in the same transaction as the
-- change they describe, then a relay sends them to SQS after commit.
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
