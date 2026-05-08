package com.bank.backend.scheduled.service;

import com.bank.backend.scheduled.domain.Frequency;
import com.bank.backend.scheduled.domain.ScheduledTransfer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Computes the next firing instant for a ScheduledTransfer.
 *
 * Anchored to a fixed time of day (10:00 local) so we don't fire during
 * end-of-day batch windows or just-past-midnight when the cron host is
 * still booting. Real banks use a less arbitrary time but the principle
 * is the same: deterministic, predictable.
 *
 * "From" is the moment to start computing forward from. Usually `now()`
 * for the initial computation when creating a schedule, or
 * `currentNextRunAt + 1ms` when advancing after a successful run.
 */
public final class FrequencyMath {

    private static final LocalTime FIRE_TIME = LocalTime.of(10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private FrequencyMath() {}

    public static Instant computeNextRun(ScheduledTransfer s, Instant from) {
        LocalDate fromDate = LocalDateTime.ofInstant(from, ZONE).toLocalDate();
        LocalDate startDate = s.getStartDate();

        // Never fire before startDate
        LocalDate searchFrom = fromDate.isBefore(startDate) ? startDate : fromDate;

        LocalDate target = switch (s.getFrequency()) {
            case ONCE    -> startDate;
            case DAILY   -> dailyNext(searchFrom, from);
            case WEEKLY  -> weeklyNext(searchFrom, s.getDayOfWeek(), from);
            case MONTHLY -> monthlyNext(searchFrom, s.getDayOfMonth(), from);
        };

        return target.atTime(FIRE_TIME).atZone(ZONE).toInstant();
    }

    /** Daily: today if it's still before fire-time, else tomorrow. */
    private static LocalDate dailyNext(LocalDate searchFrom, Instant from) {
        Instant today10am = searchFrom.atTime(FIRE_TIME).atZone(ZONE).toInstant();
        return from.isBefore(today10am) ? searchFrom : searchFrom.plusDays(1);
    }

    /** Weekly: next occurrence of dayOfWeek (1=Mon..7=Sun) on or after searchFrom. */
    private static LocalDate weeklyNext(LocalDate searchFrom, Integer dayOfWeek, Instant from) {
        if (dayOfWeek == null) {
            throw new IllegalStateException("WEEKLY schedule missing dayOfWeek");
        }
        DayOfWeek targetDow = DayOfWeek.of(dayOfWeek);
        int diff = (targetDow.getValue() - searchFrom.getDayOfWeek().getValue() + 7) % 7;
        LocalDate candidate = searchFrom.plusDays(diff);

        // If today IS the target day, only fire if we're still before fire-time
        Instant candidate10am = candidate.atTime(FIRE_TIME).atZone(ZONE).toInstant();
        if (diff == 0 && !from.isBefore(candidate10am)) {
            return candidate.plusWeeks(1);
        }
        return candidate;
    }

    /** Monthly: dayOfMonth this month if not yet passed, else next month. */
    private static LocalDate monthlyNext(LocalDate searchFrom, Integer dayOfMonth, Instant from) {
        if (dayOfMonth == null) {
            throw new IllegalStateException("MONTHLY schedule missing dayOfMonth");
        }
        // dayOfMonth capped at 28 by DB constraint, so always valid
        LocalDate thisMonth = searchFrom.withDayOfMonth(dayOfMonth);
        if (thisMonth.isBefore(searchFrom)) {
            return thisMonth.plusMonths(1);
        }
        Instant thisMonth10am = thisMonth.atTime(FIRE_TIME).atZone(ZONE).toInstant();
        if (thisMonth.equals(searchFrom) && !from.isBefore(thisMonth10am)) {
            return thisMonth.plusMonths(1);
        }
        return thisMonth;
    }
}