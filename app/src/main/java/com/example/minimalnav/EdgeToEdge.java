package com.example.minimalnav;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 统一的 Edge-to-Edge（边到边）适配工具，View 体系、零新增依赖。
 *
 * 规范依据：《安卓沉浸式显示适配规范 v1.1》§4 —— targetSdk 35+ 强制 Edge-to-Edge，
 * 系统栏透明、内容绘制到其后方，必须通过 Insets 避让，且图标对比度需随背景显式设置。
 *
 * 使用顺序：
 *   1) Activity.onCreate 中 setContentView 之后调用 enable()
 *   2) 对 Toolbar 调 top()，对底部容器调 bottom()，对 FAB 调 bottomMargin()
 *   3) Dialog / BottomSheet 单独调用 dialogBottom()
 *
 * 不使用 enableEdgeToEdge()（需新增 activity-ktx 依赖）；手动写法与之等价，
 * 但图标对比度由调用方按实际背景色指定，避免绿色 Toolbar 上出现深色图标。
 */
public final class EdgeToEdge {

    private EdgeToEdge() {
    }

    /**
     * 开启边到边。必须在 setContentView 之后、任何 Insets 监听之前调用。
     *
     * @param lightStatusBarIcons true=深色图标（浅色背景）；false=浅色图标（深色背景）
     * @param lightNavIcons       true=深色图标（浅色背景）；false=浅色图标（深色背景）
     */
    public static void enable(Activity activity,
                              boolean lightStatusBarIcons,
                              boolean lightNavIcons) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        // 刘海：Android 15+ 对非浮动窗口强制 ALWAYS；这里显式设置以兼容 API 33/34
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(lp);
        }
        // 关闭 DecorView 的默认填充，内容延伸到系统栏后方（API 35+ 系统已强制，显式写法兼容低版本）
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(lightStatusBarIcons);
            controller.setAppearanceLightNavigationBars(lightNavIcons);
        }
        // 关闭系统自动加的半透明导航条遮罩，避免底部出现灰条。
        // 用 Window 原生方法（API 29+），避免依赖 core 1.12+ 的 compat 方法。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * 顶部避让：背景延伸、标题下移。取 systemBars.top 与 displayCutout.top 的较大值。
     *
     * @param basePaddingDp 原有顶部内边距（dp），会叠加到 insets 之上
     */
    public static void top(View view, int basePaddingDp) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top;
            int base = dp(v, basePaddingDp);
            v.setPadding(v.getPaddingLeft(), Math.max(bars, cutout) + base,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /**
     * 底部避让：取 navigationBars.bottom 与 ime.bottom 的较大值（三键导航为 0 也不写死像素）。
     *
     * @param basePaddingDp 原有底部内边距（dp）
     * @param includeIme    是否叠加键盘高度（表单页必须 true）
     */
    public static void bottom(View view, int basePaddingDp, boolean includeIme) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int ime = includeIme ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            int base = dp(v, basePaddingDp);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), Math.max(nav, ime) + base);
            return insets;
        });
    }

    /** 悬浮按钮避让：marginBottom = 原值 + navigationBars.bottom，不贴手势条。 */
    public static void bottomMargin(View view, int baseMarginDp) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = dp(v, baseMarginDp) + nav;
            v.setLayoutParams(lp);
            return insets;
        });
    }

    /**
     * 底部面板专用：Dialog 是独立 Window，不继承 Activity 的 Insets。
     * 单独对面板根布局做手势条避让；Dialog 窗口本身保持默认 decor，由系统裁剪。
     */
    public static void sheetBottom(View sheetRoot) {
        ViewCompat.setOnApplyWindowInsetsListener(sheetRoot, (v, insets) -> {
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), dp(v, 16) + nav);
            return insets;
        });
    }

    private static int dp(View view, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, view.getResources().getDisplayMetrics());
    }
}
