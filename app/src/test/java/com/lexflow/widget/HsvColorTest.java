package com.lexflow.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HsvColorTest {
    @Test
    public void defaultIsNearBlackWithALittleGreen() {
        int c = HsvColor.rgb(150, 28, 9);
        assertEquals("#111714", HsvColor.hex(c));
    }

    @Test
    public void zeroValueIsBlack() {
        assertEquals("#000000", HsvColor.hex(HsvColor.rgb(150, 80, 0)));
    }

    @Test
    public void zeroSaturationIsGray() {
        assertEquals("#262626", HsvColor.hex(HsvColor.rgb(200, 0, 15)));
    }

    @Test
    public void primaryHues() {
        assertEquals("#FF0000", HsvColor.hex(HsvColor.rgb(0, 100, 100)));
        assertEquals("#00FF00", HsvColor.hex(HsvColor.rgb(120, 100, 100)));
        assertEquals("#0000FF", HsvColor.hex(HsvColor.rgb(240, 100, 100)));
        assertEquals("#FF0000", HsvColor.hex(HsvColor.rgb(360, 100, 100)));
    }
}
