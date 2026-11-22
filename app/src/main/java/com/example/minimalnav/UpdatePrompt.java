package com.example.minimalnav;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;

/**
 * 更新弹窗三态：确认 → 下载进度 → 失败重试。
 * 下载完成后用 FileProvider 生成 content:// 交给系统安装器（targetSdk 30+ 传 file:// 会报解析包错误）。
 */
final class UpdatePrompt {

    private UpdatePrompt() {
    }

    static void show(Activity activity, UpdateInfo info) {
        if (!isAlive(activity)) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本 v" + info.versionName)
                .setMessage("当前版本 v" + currentVersionName(activity) + "，是否现在更新？")
                .setPositiveButton("更新", (dialog, which) -> download(activity, info))
                .setNegativeButton("取消", (dialog, which) ->
                        new UpdatePrefs(activity).setDismissedCode(info.versionCode))
                .show();
    }

    private static void download(Activity activity, UpdateInfo info) {
        if (!isAlive(activity)) {
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        box.setPadding(padding, dp(activity, 8), padding, 0);

        TextView label = new TextView(activity);
        label.setText("正在下载… 0%");
        label.setTextSize(14);
        label.setTextColor(0xFF3A463C);
        box.addView(label);

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 6));
        barParams.topMargin = dp(activity, 14);
        box.addView(bar, barParams);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("更新到 v" + info.versionName)
                .setView(box)
                .setCancelable(false)
                .create();
        dialog.show();

        UpdateChecker.downloadAsync(activity, info, new UpdateChecker.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                if (!isAlive(activity) || !dialog.isShowing()) {
                    return;
                }
                label.setText("正在下载… " + percent + "%");
                bar.setProgress(percent);
            }

            @Override
            public void onSuccess(File apk) {
                if (!isAlive(activity)) {
                    return;
                }
                dialog.dismiss();
                install(activity, apk);
            }

            @Override
            public void onFailure() {
                if (!isAlive(activity)) {
                    return;
                }
                dialog.dismiss();
                showRetry(activity, info);
            }
        });
    }

    private static void showRetry(Activity activity, UpdateInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle("下载失败")
                .setMessage("检查网络后再试。")
                .setPositiveButton("重试", (dialog, which) -> download(activity, info))
                .setNegativeButton("取消", null)
                .show();
    }

    private static void install(Activity activity, File apk) {
        // 兜底：Release 资产可能传错版本（真出过一次），装前先比对 versionCode，
        // 不比当前新就直接收口，别把「相同 / 更低」的包丢给系统安装器。
        int apkCode = apkVersionCode(activity, apk);
        int curCode = currentVersionCode(activity);
        if (apkCode > 0 && apkCode <= curCode) {
            Toast.makeText(activity, "安装包版本不高于当前版本，已取消", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "无法启动系统安装器", Toast.LENGTH_SHORT).show();
        }
    }

    static String currentVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** 读取待安装 APK 的 versionCode，读不到返回 0（0 视为未知，不拦截）。 */
    private static int apkVersionCode(Context context, File apk) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null) {
                return 0;
            }
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) info.getLongVersionCode()
                    : info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static int currentVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) info.getLongVersionCode()
                    : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static boolean isAlive(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics());
    }
}
