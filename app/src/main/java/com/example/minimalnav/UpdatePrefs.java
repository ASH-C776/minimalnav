package com.example.minimalnav;

import android.content.Context;
import android.content.SharedPreferences;

/** 更新检查的节流数据：单独一个 prefs 文件，不混进地址清单。 */
final class UpdatePrefs {

    private static final String FILE = "update_prefs";
    private static final String KEY_LAST_CHECK = "last_check_ms";
    private static final String KEY_DISMISSED = "dismissed_code";
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    private final SharedPreferences prefs;

    UpdatePrefs(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    long getLastCheckMs() {
        return prefs.getLong(KEY_LAST_CHECK, 0L);
    }

    void markChecked(long nowMs) {
        prefs.edit().putLong(KEY_LAST_CHECK, nowMs).apply();
    }

    /** 距上次检查是否还不满 24 小时。 */
    boolean withinThrottle(long nowMs) {
        long last = getLastCheckMs();
        return last > 0 && nowMs - last < CHECK_INTERVAL_MS;
    }

    /** 用户点过「取消」的版本号，同版本不反复打扰；更高版本仍会提示。 */
    int getDismissedCode() {
        return prefs.getInt(KEY_DISMISSED, 0);
    }

    void setDismissedCode(int code) {
        prefs.edit().putInt(KEY_DISMISSED, code).apply();
    }
}
