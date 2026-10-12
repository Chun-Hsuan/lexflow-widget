package com.lexflow.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StoreTest {

    private static final long T0 = 1_760_000_000_000L;

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
        Store.ensureCurrent(d, T0);
        assertEquals("n0", d.currentId());
    }

    @Test
    public void understoodMovesToNextAndSchedulesOneDay() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0);
        assertEquals(1, Store.applyUnderstood(d, "n0", T0));
        assertEquals("n1", d.currentId());
        assertEquals(T0 + Store.DAY, d.item("n0").optLong("due"));
    }

    @Test
    public void intervalsGrowThenCapAtThirtyDays() {
        Store.Data d = data(1);
        Store.ensureCurrent(d, T0);
        int[] expected = {1, 3, 7, 14, 30, 30};
        for (int e : expected) {
            assertEquals(e, Store.applyUnderstood(d, "n0", T0));
        }
    }

    @Test
    public void staleWidgetDoesNotMarkWrongNote() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0);
        assertEquals(0, Store.applyUnderstood(d, "n2", T0));
        assertEquals("n0", d.currentId());
    }

    @Test
    public void untouchedForADaySwapsAndSkippedGoesToBack() {
        Store.Data d = data(3);
        Store.ensureCurrent(d, T0);
        Store.ensureCurrent(d, T0 + Store.DAY - 1);
        assertEquals("n0", d.currentId());
        Store.ensureCurrent(d, T0 + Store.DAY);
        assertEquals("n1", d.currentId());
        assertEquals(0, d.item("n0").optInt("level", 0));
        // n1 read, then n2 read: skipped n0 comes back after the unread ones.
        Store.applyUnderstood(d, "n1", T0 + Store.DAY);
        assertEquals("n2", d.currentId());
        Store.applyUnderstood(d, "n2", T0 + Store.DAY);
        assertEquals("n0", d.currentId());
    }

    @Test
    public void dueReviewComesBeforeUnread() {
        Store.Data d = data(4);
        Store.ensureCurrent(d, T0);
        Store.applyUnderstood(d, "n0", T0); // n0 due at T0 + 24h
        assertEquals("n1", d.currentId());
        Store.applyUnderstood(d, "n1", T0 + 23 * Store.HOUR); // n0 not due yet
        assertEquals("n2", d.currentId());
        assertEquals(1, Store.applyUnderstood(d, "n2", T0 + Store.DAY + 1));
        assertEquals("n0", d.currentId()); // due review beats unread n3
    }

    @Test
    public void allReadAndNothingDueShowsSoonestReview() {
        Store.Data d = data(2);
        Store.ensureCurrent(d, T0);
        Store.applyUnderstood(d, "n0", T0);
        Store.applyUnderstood(d, "n1", T0 + 1000);
        assertEquals("n0", d.currentId());
        assertNotEquals(0, Store.applyUnderstood(d, "n0", T0 + 2000));
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
        Store.ensureCurrent(d, T0 + 5);
        assertEquals("n0", d.currentId());
    }
}
