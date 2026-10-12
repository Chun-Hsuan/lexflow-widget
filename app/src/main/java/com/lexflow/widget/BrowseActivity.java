package com.lexflow.widget;

/**
 * A note opened from the library. Same page as {@link DetailActivity}, but the button is an
 * outline "Close" and nothing here touches the schedule or the daily limit.
 * A separate class so it lives in the library's task, while review pages get their own.
 */
public class BrowseActivity extends DetailActivity {
    @Override
    protected boolean browsing() {
        return true;
    }
}
