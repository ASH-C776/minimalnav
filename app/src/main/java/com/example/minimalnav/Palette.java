package com.example.minimalnav;

/**
 * 七类地点图标调色板。
 *
 * 每枚图标自带专属底色与白色主体（矢量 drawable 内已写死），
 * 颜色选择栏已移除：一类地点对应一种固定颜色，不再由用户二次染色。
 * iconIndex 范围 0..6，顺序与 ICONS / ICON_NAMES 一致。
 */
public final class Palette {
    public static final int[] ICONS = {
            R.drawable.ic_community,   // 0 小区 · 苔绿
            R.drawable.ic_town,        // 1 城镇 · 钢蓝
            R.drawable.ic_village,     // 2 乡村 · 麦野橄榄
            R.drawable.ic_street,      // 3 街道 · 路赭
            R.drawable.ic_shopping,    // 4 购物 · 陶橙
            R.drawable.ic_food,        // 5 餐饮 · 朱砂
            R.drawable.ic_hospital     // 6 医院 · 青瓷蓝
    };

    public static final String[] ICON_NAMES = {
            "小区", "城镇", "乡村", "街道", "购物", "餐饮", "医院"
    };

    /** 图标个数，供旧索引迁移与边界裁剪使用。 */
    public static final int COUNT = ICONS.length;

    /**
     * 旧版 6 图标索引 → 新版 7 类索引。只在一次性数据迁移 / 导入旧清单时调用。
     * 旧顺序：0 家、1 公司、2 购物、3 餐饮、4 学校、5 医院。
     */
    public static int migrateOldIcon(int old) {
        switch (old) {
            case 0: return 0; // 家 → 小区
            case 1: return 1; // 公司 → 城镇
            case 2: return 4; // 购物
            case 3: return 5; // 餐饮
            case 4: return 1; // 学校 → 城镇
            case 5: return 6; // 医院
            default: return 0;
        }
    }

    private Palette() {}
}
