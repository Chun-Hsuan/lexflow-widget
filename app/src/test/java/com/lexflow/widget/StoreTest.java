package com.lexflow.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StoreTest {

    private static final long T0 = 1_760_000_000_000L;
    private static final String D1 = "2026-10-12";
    private static final String D2 = "2026-10-13";

    private static Store.Data data(int count) {
        List<Store.Note> notes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Store.Note n = new Store.Note();
            n.id = "n" + i;
            notes.add(n);
        }
        return Store.Data.of(notes, new JSONObject());
    }

    @Test
    public void firstNoteIsFirstInFile() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0, D1);
        assertEquals("n0", d.currentId());
    }

    @Test
    public void understoodMovesToNextAndSchedulesOneDay() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0, D1);
        assertEquals(1, Store.applyUnderstood(d, "n0", T0, D1));
        assertEquals("n1", d.currentId());
        assertEquals(T0 + Store.DAY, d.item("n0").optLong("due"));
    }

    @Test
    public void intervalsGrowThenCapAtThirtyDays() {
        Store.Data d = data(1);
        Store.ensureCurrent(d, T0, D1);
        int[] expected = {1, 3, 7, 14, 30, 30};
        for (int i = 0; i < expected.length; i++) {
            // One tap per day so the daily limit never gets in the way.
            assertEquals(expected[i], Store.applyUnderstood(d, "n0", T0, "2026-10-1" + i));
        }
    }

    @Test
    public void staleWidgetDoesNotMarkWrongNote() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0, D1);
        assertEquals(0, Store.applyUnderstood(d, "n2", T0, D1));
        assertEquals("n0", d.currentId());
    }

    @Test
    public void untouchedForADaySwapsAndSkippedGoesToBack() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0, D1);
        Store.ensureCurrent(d, T0 + Store.DAY - 1, D1);
        assertEquals("n0", d.currentId());
        Store.ensureCurrent(d, T0 + Store.DAY, D1);
        assertEquals("n1", d.currentId());
        assertEquals(0, d.item("n0").optInt("level", 0));
        // n1 read, then n2 read: skipped n0 comes back after the unread ones.
        Store.applyUnderstood(d, "n1", T0 + Store.DAY, D1);
        assertEquals("n2", d.currentId());
        Store.applyUnderstood(d, "n2", T0 + Store.DAY, D1);
        assertEquals("n0", d.currentId());
    }

    @Test
    public void dueReviewComesBeforeUnread() {
        Store.Data d = data(4);
        Store.ensureCurrent(d, T0, D1);
        Store.applyUnderstood(d, "n0", T0, D1); // n0 due at T0 + 24h
        assertEquals("n1", d.currentId());
        Store.applyUnderstood(d, "n1", T0 + 23 * Store.HOUR, D1); // n0 not due yet
        assertEquals("n2", d.currentId());
        assertEquals(1, Store.applyUnderstood(d, "n2", T0 + Store.DAY + 1, D1));
        assertEquals("n0", d.currentId()); // due review beats unread n3
    }

    @Test
    public void allReadAndNothingDueShowsSoonestReview() {
        Store.Data d = data(2);
        Store.ensureCurrent(d, T0, D1);
        Store.applyUnderstood(d, "n0", T0, D1);
        Store.applyUnderstood(d, "n1", T0 + 1000, D1);
        assertEquals("n0", d.currentId());
        assertNotEquals(0, Store.applyUnderstood(d, "n0", T0 + 2000, D1));
        assertEquals("n1", d.currentId());
    }

    @Test
    public void removedCurrentNoteIsReplaced() {
        Store.Data d = data(3);
        try {
            d.state.put("current", "gone");
            d.state.put("shownSince", T0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Store.ensureCurrent(d, T0 + 5, D1);
        assertEquals("n0", d.currentId());
    }

    @Test
    public void fiveDoneTapsFinishTheDay() {
        Store.dailyLimit = 5;
        Store.Data d = data(8);
        Store.ensureCurrent(d, T0, D1);
        for (int i = 0; i < 5; i++) {
            assertEquals(1, Store.applyUnderstood(d, d.currentId(), T0 + i, D1));
        }
        assertEquals(true, Store.isLocked(d, D1));
        String waiting = d.currentId();
        assertEquals(0, Store.applyUnderstood(d, waiting, T0 + 10, D1)); // sixth tap refused
        // Timer paused: even 23 hours later on the same day nothing swaps.
        Store.ensureCurrent(d, T0 + 23 * Store.HOUR, D1);
        assertEquals(waiting, d.currentId());
    }

    @Test
    public void nextDayUnlocksAndRestartsTimer() {
        Store.dailyLimit = 5;
        Store.Data d = data(8);
        Store.ensureCurrent(d, T0, D1);
        for (int i = 0; i < 5; i++) Store.applyUnderstood(d, d.currentId(), T0, D1);
        String waiting = d.currentId();
        long nextMorning = T0 + 30 * Store.HOUR; // more than 24h after it was picked
        Store.ensureCurrent(d, nextMorning, D2);
        assertEquals(false, Store.isLocked(d, D2));
        assertEquals(waiting, d.currentId()); // not skipped just because the day was locked
        assertEquals(nextMorning, d.state.optLong("shownSince"));
        assertEquals(1, Store.applyUnderstood(d, waiting, nextMorning + 1, D2));
        assertEquals(1, d.state.optInt("count", 0)); // count restarted for the new day
    }

    @Test
    public void noLimitNeverLocksAndClearsOldLock() {
        Store.dailyLimit = 5;
        Store.Data d = data(8);
        Store.ensureCurrent(d, T0, D1);
        for (int i = 0; i < 5; i++) Store.applyUnderstood(d, d.currentId(), T0, D1);
        assertEquals(true, Store.isLocked(d, D1));
        Store.dailyLimit = 0; // the no-limit build is installed over the old one
        assertEquals(false, Store.isLocked(d, D1));
        for (int i = 0; i < 7; i++) {
            assertNotEquals(0, Store.applyUnderstood(d, d.currentId(), T0 + 100, D1));
        }
        assertEquals(false, Store.isLocked(d, D1));
    }
}
