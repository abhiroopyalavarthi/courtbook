package com.courtbook.booking.booking;

import com.courtbook.booking.booking.BookingDtos.BookingStats;
import com.courtbook.booking.booking.BookingDtos.BookingView;
import com.courtbook.booking.booking.BookingDtos.CourtView;
import com.courtbook.booking.booking.BookingDtos.SlotView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side queries that join several tables. Plain SQL is clearer here than
 * mapping these views through JPA.
 */
@Component
public class SlotQueries {

    private final JdbcTemplate jdbc;
    private final ScheduleProperties schedule;

    public SlotQueries(JdbcTemplate jdbc, ScheduleProperties schedule) {
        this.jdbc = jdbc;
        this.schedule = schedule;
    }

    public List<CourtView> courts() {
        return jdbc.query("select id, name from courts where active order by id",
                (rs, i) -> new CourtView(rs.getLong("id"), rs.getString("name")));
    }

    /** Creates the day's slots if they don't exist yet. Safe to call concurrently. */
    @Transactional
    public void ensureSlots(LocalDate date) {
        List<ZonedDateTime> starts = schedule.slotStarts(date);
        List<Long> courtIds = jdbc.queryForList("select id from courts where active", Long.class);
        Integer existing = jdbc.queryForObject(
                "select count(*) from slots where starts_at >= ? and starts_at < ?", Integer.class,
                ts(dayStart(date)), ts(dayStart(date.plusDays(1))));
        if (existing != null && existing >= starts.size() * courtIds.size()) {
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        for (long courtId : courtIds) {
            for (ZonedDateTime start : starts) {
                rows.add(new Object[] {courtId, ts(start.toInstant()), ts(start.plus(schedule.slotLength()).toInstant())});
            }
        }
        jdbc.batchUpdate("""
                insert into slots (court_id, starts_at, ends_at) values (?, ?, ?)
                on conflict (court_id, starts_at) do nothing""", rows);
    }

    public List<SlotView> slotsOn(LocalDate date, long userId, Instant now) {
        return jdbc.query("""
                select s.id, s.court_id, c.name as court_name, s.starts_at, s.ends_at,
                       b.id as booking_id, b.user_id as booked_by,
                       (select count(*) from waitlist_entries w
                         where w.slot_id = s.id and w.status = 'WAITING') as waitlist_count,
                       case when me.id is null then null else
                         (select count(*) from waitlist_entries w
                           where w.slot_id = s.id and w.status = 'WAITING'
                             and (w.created_at, w.id) <= (me.created_at, me.id)) end as my_position
                from slots s
                join courts c on c.id = s.court_id and c.active
                left join bookings b on b.slot_id = s.id and b.status = 'ACTIVE'
                left join waitlist_entries me on me.slot_id = s.id and me.user_id = ? and me.status = 'WAITING'
                where s.starts_at >= ? and s.starts_at < ?
                order by s.starts_at, c.id""",
                (rs, i) -> slotView(rs, userId, now),
                userId, ts(dayStart(date)), ts(dayStart(date.plusDays(1))));
    }

    private static final String BOOKING_VIEW_SQL = """
            select b.id, b.slot_id, c.name as court_name, s.starts_at, s.ends_at,
                   b.status, b.source, b.created_at
            from bookings b
            join slots s on s.id = b.slot_id
            join courts c on c.id = s.court_id
            """;

    public List<BookingView> bookingsOf(long userId) {
        return jdbc.query(BOOKING_VIEW_SQL + " where b.user_id = ? order by s.starts_at desc limit 100",
                (rs, i) -> bookingView(rs), userId);
    }

    public BookingView booking(long bookingId) {
        return jdbc.queryForObject(BOOKING_VIEW_SQL + " where b.id = ?", (rs, i) -> bookingView(rs), bookingId);
    }

    private static BookingView bookingView(ResultSet rs) throws SQLException {
        return new BookingView(
                rs.getLong("id"),
                rs.getLong("slot_id"),
                rs.getString("court_name"),
                instant(rs, "starts_at"),
                instant(rs, "ends_at"),
                rs.getString("status"),
                rs.getString("source"),
                instant(rs, "created_at"));
    }

    public String courtName(long slotId) {
        return jdbc.queryForObject(
                "select c.name from slots s join courts c on c.id = s.court_id where s.id = ?",
                String.class, slotId);
    }

    public BookingStats stats(Instant now) {
        return jdbc.queryForObject("""
                select count(*) as total,
                       count(*) filter (where b.status = 'ACTIVE' and s.starts_at > ?) as upcoming,
                       count(*) filter (where b.status = 'CANCELLED') as cancelled,
                       count(*) filter (where b.source = 'WAITLIST') as promoted,
                       count(distinct b.user_id) as members
                from bookings b join slots s on s.id = b.slot_id""",
                (rs, i) -> new BookingStats(rs.getLong("total"), rs.getLong("upcoming"),
                        rs.getLong("cancelled"), rs.getLong("promoted"), rs.getLong("members")),
                ts(now));
    }

    private SlotView slotView(ResultSet rs, long userId, Instant now) throws SQLException {
        long bookingId = rs.getLong("booking_id");
        boolean booked = !rs.wasNull();
        long bookedBy = rs.getLong("booked_by");
        int position = rs.getInt("my_position");
        boolean waiting = !rs.wasNull();
        Instant startsAt = instant(rs, "starts_at");
        return new SlotView(
                rs.getLong("id"),
                rs.getLong("court_id"),
                rs.getString("court_name"),
                startsAt,
                instant(rs, "ends_at"),
                booked ? "BOOKED" : "FREE",
                booked && bookedBy == userId ? bookingId : null,
                rs.getInt("waitlist_count"),
                waiting ? position : null,
                !now.isBefore(startsAt));
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(schedule.zone()).toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
