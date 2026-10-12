package com.lexflow.widget;

/** Reading styles for the note page. Colors match res/values(-night)/colors.xml. */
final class NoteCss {
    private NoteCss() {
    }

    static final String CSS = ""
            + "body.light{--bg:#F5F6F8;--fg:#1B1F24;--muted:#5A6370;--line:#DCE0E6;"
            + "--accent:#2D5B8A;--accent-soft:#E3ECF5;--panel:#FFFFFF;}"
            + "body.dark{--bg:#14171B;--fg:#E5E8EC;--muted:#9AA3AE;--line:#2C323A;"
            + "--accent:#8FB6E0;--accent-soft:#1F2A36;--panel:#1B1F25;}"
            + "*{box-sizing:border-box}"
            + "html{-webkit-text-size-adjust:100%}"
            + "body{margin:0;padding:20px 20px 32px;background:var(--bg);color:var(--fg);"
            + "font-family:sans-serif;font-size:16px;line-height:1.7;word-wrap:break-word}"
            + "header{padding-bottom:18px;margin-bottom:8px;border-bottom:1px solid var(--line)}"
            + ".tag{display:inline-block;font-size:12px;font-weight:700;letter-spacing:.08em;"
            + "color:var(--accent);background:var(--accent-soft);padding:2px 10px;border-radius:999px}"
            + "h1{font-size:23px;line-height:1.35;margin:10px 0 12px}"
            + ".core{margin:0;padding:12px 14px;background:var(--panel);border:1px solid var(--line);"
            + "border-radius:12px;font-size:15.5px}"
            + "h2{font-size:18px;margin:26px 0 8px;color:var(--accent)}"
            + "p{margin:8px 0}"
            + "ul,ol{margin:6px 0;padding-left:22px}"
            + "li{margin:4px 0}"
            + "li>ul,li>ol{margin:2px 0}"
            + "li>ul>li{color:var(--muted)}"
            + "strong{color:var(--fg)}"
            + "table{display:block;overflow-x:auto;border-collapse:collapse;margin:10px 0;"
            + "font-size:14px;line-height:1.55}"
            + "th,td{border:1px solid var(--line);padding:7px 9px;vertical-align:top;min-width:96px}"
            + "th{background:var(--accent-soft);text-align:left;white-space:nowrap}";
}
