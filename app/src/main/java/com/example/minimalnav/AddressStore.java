package com.example.minimalnav;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** SharedPreferences + JSON 持久化；零数据库依赖，并提供清单导入导出。 */
public class AddressStore {
    private static final String PREFS = "minimal_nav";
    // V3：七类图标（每枚自带底色），iconIndex 0..6；V2 是旧的 6 色 6 图标格式
    private static final String KEY_V3 = "addresses_v3";
    private static final String KEY_V2 = "addresses_v2";
    private static final String KEY_V1 = "addresses_v1";
    private static final int EXPORT_VERSION = 4;

    private final SharedPreferences prefs;

    public AddressStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Address> load() {
        String json = prefs.getString(KEY_V3, null);
        if (json != null) {
            return parse(json, false);
        }

        // 旧数据一次性迁移：V2（6 色 6 图标）或更早的 V1，按语义映射到新七类
        String legacy = prefs.getString(KEY_V2, null);
        if (legacy == null) {
            legacy = prefs.getString(KEY_V1, null);
        }
        if (legacy != null) {
            List<Address> migrated = parse(legacy, true);
            save(migrated);
            return migrated;
        }

        // 首次启动示例数据
        List<Address> seeds = new ArrayList<>();
        seeds.add(new Address("家", 39.92848272, 116.39560823, "", 0, 0));
        seeds.add(new Address("公司", 39.98848272, 116.47560823, "", 0, 1));
        seeds.add(new Address("超市", 39.908860, 116.397390, "", 0, 4));
        save(seeds);
        return seeds;
    }

    /** 读取裸数组格式。legacy=true 时把旧 6 图标索引映射到新七类。 */
    private List<Address> parse(String json, boolean legacy) {
        List<Address> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                boolean hasCoord = o.optBoolean("hasCoord", true);
                double lat = hasCoord ? o.optDouble("lat", 0) : Double.NaN;
                double lon = hasCoord ? o.optDouble("lon", 0) : Double.NaN;
                String poiId = o.optString("poiId", "");
                // 既无坐标又无 ID 的坏条目直接跳过
                if (Double.isNaN(lat) && poiId.isEmpty()) {
                    continue;
                }
                int rawIcon = clamp(o.optInt("iconIndex", 0), 0, 5);
                int icon = legacy ? Palette.migrateOldIcon(rawIcon)
                        : clamp(o.optInt("iconIndex", 0), 0, Palette.COUNT - 1);
                list.add(new Address(
                        o.optString("name", ""),
                        lat,
                        lon,
                        poiId,
                        0, // 颜色选择已停用
                        icon));
            }
        } catch (JSONException e) {
            // 数据损坏时返回空列表，不崩溃；用户可重新添加
        }
        return list;
    }

    public void save(List<Address> list) {
        JSONArray arr = new JSONArray();
        for (Address a : list) {
            try {
                arr.put(toJsonObject(a));
            } catch (JSONException ignored) {
                // 理论上不会发生
            }
        }
        prefs.edit().putString(KEY_V3, arr.toString()).apply();
    }

    // ---------- 导入 / 导出 ----------

    /** 导出为带版本号的 JSON 文件，供 Storage Access Framework 写入。 */
    public static String exportJson(List<Address> list) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "MinimalNav");
        root.put("version", EXPORT_VERSION);
        root.put("exportedAt",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));

        JSONArray arr = new JSONArray();
        for (Address a : list) {
            arr.put(toJsonObject(a));
        }
        root.put("addresses", arr);
        return root.toString(2);
    }

    /**
     * 解析导出的 JSON。兼容旧版裸数组；跳过非法条目，不因单条坏数据整体失败。
     * 合法条目：名称非空，且（坐标有效 或 POI ID 非空）。
     * 旧版本导出中的 pinned 字段直接忽略。
     */
    public static List<Address> importJson(String json) throws JSONException {
        JSONArray arr;
        JSONObject root = new JSONObject(json);
        boolean legacyIcons;
        if (root.has("addresses")) {
            arr = root.getJSONArray("addresses");
            // version < 4 的导出使用旧的 6 图标索引
            legacyIcons = root.optInt("version", 1) < 4;
        } else {
            // 裸数组：旧格式，一律按旧索引映射
            arr = new JSONArray(json);
            legacyIcons = true;
        }

        List<Address> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name", "").trim();
            String poiId = o.optString("poiId", "").trim();
            boolean hasCoord = o.optBoolean("hasCoord", true);

            double lat = Double.NaN;
            double lon = Double.NaN;
            if (hasCoord) {
                lat = o.optDouble("lat", Double.NaN);
                lon = o.optDouble("lon", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)
                        || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    lat = Double.NaN;
                    lon = Double.NaN;
                }
            }

            if (name.isEmpty() || (Double.isNaN(lat) && poiId.isEmpty())) {
                continue;
            }

            int rawIcon = clamp(o.optInt("iconIndex", 0), 0, 5);
            int icon = legacyIcons ? Palette.migrateOldIcon(rawIcon)
                    : clamp(o.optInt("iconIndex", 0), 0, Palette.COUNT - 1);
            list.add(new Address(
                    name,
                    lat,
                    lon,
                    poiId,
                    0, // 颜色选择已停用
                    icon));
        }
        return list;
    }

    private static JSONObject toJsonObject(Address a) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", a.getName());
        o.put("hasCoord", a.hasCoord());
        // JSON 不接受 NaN，无坐标时写 0，读取时靠 hasCoord 判定
        o.put("lat", a.hasCoord() ? a.getLat() : 0);
        o.put("lon", a.hasCoord() ? a.getLon() : 0);
        o.put("poiId", a.getPoiId());
        o.put("colorIndex", a.getColorIndex());
        o.put("iconIndex", a.getIconIndex());
        return o;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
