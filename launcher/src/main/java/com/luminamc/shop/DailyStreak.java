package com.luminamc.shop;

import com.luminamc.config.LauncherConfig;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The daily login-streak economy. One claim per calendar day (reset at 00:00
 * Europe/Berlin); the per-day reward grows week over week for 12 weeks, then
 * holds at its maximum forever. Missing a day resets the streak to day 1.
 */
public final class DailyStreak {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final int WEEKS = 12;

    /**
     * Total tokens earned across a full week (the seven daily claims add up to this);
     * week 12 is the (flat) maximum. Each day pays {@code total / 7}, with day 7
     * carrying the remainder so the week sums exactly.
     */
    private static final long[] WEEK_TOTAL =
            {100, 200, 350, 550, 800, 1100, 1500, 2000, 2600, 3300, 4100, 5000};

    /** Box / node states. */
    public static final int LOCKED = 0, DONE = 1, READY = 2;

    private final LauncherConfig cfg;
    private final TokenEconomy wallet;

    public DailyStreak(LauncherConfig cfg) {
        this.cfg = cfg;
        this.wallet = new TokenEconomy(cfg);
    }

    // ── dates ──────────────────────────────────────────────────────────────

    public LocalDate today() { return LocalDate.now(ZONE); }

    private LocalDate lastClaim() {
        try {
            return (cfg.lastClaimDate == null || cfg.lastClaimDate.isBlank())
                    ? null : LocalDate.parse(cfg.lastClaimDate);
        } catch (Exception e) { return null; }
    }

    public boolean claimedToday() { return today().equals(lastClaim()); }
    public boolean canClaim() { return !claimedToday(); }

    /** True when the last claim was exactly yesterday (the streak is alive). */
    private boolean continuing() {
        LocalDate l = lastClaim();
        return l != null && l.equals(today().minusDays(1)) && cfg.streakDay > 0;
    }

    // ── progression maths ────────────────────────────────────────────────────

    public int streakDay() { return cfg.streakDay; }

    /** The day index the next claim will award (1-based). */
    public int nextDay() {
        if (claimedToday()) return Math.max(1, cfg.streakDay);
        return continuing() ? cfg.streakDay + 1 : 1;
    }

    /** The day index the chain/tree should centre on right now. */
    private int refDay() { return claimedToday() ? Math.max(1, cfg.streakDay) : nextDay(); }

    /** Raw (uncapped) week number currently shown — for the "Week X" label. */
    public int displayWeek() { return (int) Math.ceil(refDay() / 7.0); }

    /** The whole-week total for a given week (what the 7 daily claims add up to). */
    public long weeklyTotal(int week) {
        int w = Math.min(WEEKS, Math.max(1, week));
        return WEEK_TOTAL[w - 1];
    }

    /**
     * Reward for a single day, splitting the week's total across its 7 days. Day 7
     * carries the rounding remainder, so the seven days sum to exactly the weekly total.
     */
    public long dailyReward(int week, int dayInWeek) {
        long total = weeklyTotal(week);
        long base = total / 7;
        return dayInWeek >= 7 ? base + (total - base * 7) : base;
    }

    /** Reward the next claim will grant. */
    public long nextReward() {
        int nd = nextDay();
        return dailyReward((int) Math.ceil(nd / 7.0), ((nd - 1) % 7) + 1);
    }

    /** State of each of the 7 boxes in the current week. */
    public int[] weekBoxes() {
        int[] b = new int[7];
        int dayInWeek = ((refDay() - 1) % 7) + 1;
        boolean claimed = claimedToday();
        for (int i = 0; i < 7; i++) {
            int d = i + 1;
            if (claimed) b[i] = d <= dayInWeek ? DONE : LOCKED;
            else         b[i] = d < dayInWeek ? DONE : (d == dayInWeek ? READY : LOCKED);
        }
        return b;
    }

    /** State of a node (week 1..12) in the progress tree. */
    public int weekState(int week) {
        if (cfg.streakDay >= week * 7) return DONE;     // every day of this week claimed
        if (week == Math.min(WEEKS, displayWeek())) return READY;
        return LOCKED;
    }

    // ── claim ─────────────────────────────────────────────────────────────────

    public record ClaimResult(long reward, int day, int week) {}

    /** Claims today's bonus. Returns the result, or {@code null} if already claimed today. */
    public ClaimResult claim() {
        if (claimedToday()) return null;
        int day = continuing() ? cfg.streakDay + 1 : 1;
        int week = Math.min(WEEKS, (int) Math.ceil(day / 7.0));
        long reward = dailyReward(week, ((day - 1) % 7) + 1);
        cfg.streakDay = day;
        cfg.lastClaimDate = today().toString();
        wallet.grantLogged("Daily bonus · Day " + day, reward);   // adds tokens + log + save
        return new ClaimResult(reward, day, week);
    }

    // ── reset countdown ───────────────────────────────────────────────────────

    public Duration untilReset() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
        return Duration.between(now, next);
    }

    public String countdown() {
        Duration d = untilReset();
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}
