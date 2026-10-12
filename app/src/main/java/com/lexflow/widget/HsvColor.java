package com.lexflow.widget;

/** HSV to RGB without Android classes, so it can be unit tested. hue 0-360, sat/val 0-100. */
final class HsvColor {
    private HsvColor() {
    }

    static int rgb(int hue, int sat, int val) {
        float h = (((hue % 360) + 360) % 360) / 60f;
        float s = Math.max(0, Math.min(100, sat)) / 100f;
        float v = Math.max(0, Math.min(100, val)) / 100f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static int to255(float x) {
        return Math.round(x * 255f);
    }
}
