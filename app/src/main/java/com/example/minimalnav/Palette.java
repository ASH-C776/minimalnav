package com.example.minimalnav;

/** 6 色 + 6 图标调色板，颜色纯视觉区分，无语义。 */
public final class Palette {
    public static final int[] COLORS = {
            0xFF2E7D32, // 绿
            0xFF1565C0, // 蓝
            0xFFEF6C00, // 橙
            0xFF6A1B9A, // 紫
            0xFFC62828, // 红
            0xFF00838F  // 青
    };

    public static final int[] ICONS = {
            R.drawable.ic_home,
            R.drawable.ic_work,
            R.drawable.ic_shopping,
            R.drawable.ic_restaurant,
            R.drawable.ic_school,
            R.drawable.ic_hospital
    };

    private Palette() {}
}
