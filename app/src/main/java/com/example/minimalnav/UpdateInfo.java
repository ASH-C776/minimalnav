package com.example.minimalnav;

import java.util.List;

/**
 * 一个可下载的新版本。
 *
 * {@link #parseVersionCode(String)} 必须与 app/build.gradle 的 versionCode 公式一字不差：
 * {@code major * 10000 + minor * 100 + patch}。判反了会出现「提示降级」或「永远提示有新版」。
 */
final class UpdateInfo {

    final String versionName;
    final int versionCode;
    final String downloadUrl;
    /** "gitee" 或 "github"，只用于优先级与日志。 */
    final String source;

    UpdateInfo(String versionName, int versionCode, String downloadUrl, String source) {
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.downloadUrl = downloadUrl;
        this.source = source;
    }

    /**
     * tag 形如 v1.2.0 / 1.2 / v1.2.0-beta，统一折算成 int。
     * 后缀（-beta、+meta）一律剥掉；非数字、超过三位、单段超过 9999 均判为脏数据返回 null。
     */
    static Integer parseVersionCode(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int dash = s.indexOf('-');
        if (dash >= 0) {
            s = s.substring(0, dash);
        }
        int plus = s.indexOf('+');
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        if (s.isEmpty()) {
            return null;
        }
        String[] parts = s.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return null;
        }
        int[] nums = new int[3];
        for (int i = 0; i < parts.length; i++) {
            try {
                nums[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (nums[i] < 0 || nums[i] > 9999) {
                return null;
            }
        }
        return nums[0] * 10000 + nums[1] * 100 + nums[2];
    }

    /** 取比当前版本更高里最大的那个；版本相同时优先 Gitee（国内可达、下载快）。 */
    static UpdateInfo pickLatest(List<UpdateInfo> candidates, int currentCode) {
        UpdateInfo best = null;
        for (UpdateInfo c : candidates) {
            if (c.versionCode <= currentCode) {
                continue;
            }
            if (best == null || c.versionCode > best.versionCode) {
                best = c;
            } else if (c.versionCode == best.versionCode
                    && "gitee".equals(c.source) && !"gitee".equals(best.source)) {
                best = c;
            }
        }
        return best;
    }
}
