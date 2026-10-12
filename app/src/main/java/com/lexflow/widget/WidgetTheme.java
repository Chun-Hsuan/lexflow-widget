package com.lexflow.widget;

import android.content.Context;
import android.content.SharedPreferences;

/** The widget's background color, kept as hue / saturation / value so it can be tuned on the phone. */
final class WidgetTheme {
    private WidgetTheme() {
    }

    private static final String PREFS = "lexflow_theme";

    /** Near-black with a little deep green: #111714. */
    static final int DEF_HUE = 150;
    static final int DEF_SAT = 28;
    static final int DEF_VAL = 9;

    static int[] load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new int[]{
                p.getInt("hue", DEF_HUE),
                p.getInt("sat", DEF_SAT),
                p.getInt("val", DEF_VAL),
        };
    }

    static void save(Context c, int hue, int sat, int val) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("hue", hue).putInt("sat", sat).putInt("val", val).apply();
    }

    static int bgColor(Context c) {
        int[] v = load(c);
        return HsvColor.rgb(v[0], v[1], v[2]);
    }
}
