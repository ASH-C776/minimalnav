package com.example.minimalnav;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 关于页：全屏单页，内容一屏排完不滚动。
 *
 * 版本号从 PackageManager 实时读取，与 build.gradle 的 versionName / versionCode 自动同步，
 * 不在布局里写死。检查更新走 {@link UpdateChecker}，与主页开机自检共用同一套逻辑。
 */
public class AboutActivity extends AppCompatActivity {

    private static final String GITHUB_URL = "https://github.com/" + UpdateChecker.GITHUB_REPO;

    private TextView tvUpdateState;
    private View rowUpdate;
    /** 本次已查到的可更新版本，再次点击「检查更新」直接复用，不重复联网。 */
    private UpdateInfo pendingUpdate;
    private boolean checking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 绿色 Toolbar 上用浅色状态栏图标；页脚区是浅灰底，导航栏用深色图标
        EdgeToEdge.enable(this, false, true);
        Toolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdge.top(toolbar, 0);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvFooter = findViewById(R.id.tvFooter);
        EdgeToEdge.bottom(tvFooter, 4, false);

        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(readVersionLabel());

        rowUpdate = findViewById(R.id.rowUpdate);
        tvUpdateState = findViewById(R.id.tvUpdateState);
        rowUpdate.setOnClickListener(v -> {
            if (pendingUpdate != null) {
                UpdatePrompt.show(this, pendingUpdate);
            } else {
                checkUpdate();
            }
        });

        findViewById(R.id.rowGithub).setOnClickListener(v -> openGithub());
    }

    /** 「版本 1.0（构建 1）」，读不到时回落到布局里的静态文案。 */
    private String readVersionLabel() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            int code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) info.getLongVersionCode()
                    : info.versionCode;
            return "版本 " + info.versionName + "（构建 " + code + "）";
        } catch (PackageManager.NameNotFoundException e) {
            return "版本 1.0.0（构建 10000）";
        }
    }

    /** 手动检查：绕开 24 小时节流，点一次就一定联网查一次。 */
    private void checkUpdate() {
        if (checking) {
            return;
        }
        checking = true;
        rowUpdate.setEnabled(false);
        tvUpdateState.setTextColor(0xFF8A968C);
        tvUpdateState.setText("检查中…");

        UpdateChecker.checkAsync(UpdatePrompt.currentVersionCode(this), info -> {
            checking = false;
            rowUpdate.setEnabled(true);
            if (isFinishing()) {
                return;
            }
            new UpdatePrefs(this).markChecked(System.currentTimeMillis());
            if (info == null) {
                pendingUpdate = null;
                tvUpdateState.setTextColor(0xFF3E6B47);
                tvUpdateState.setText("已是最新");
                Toast.makeText(this, "已是最新版本", Toast.LENGTH_SHORT).show();
            } else {
                pendingUpdate = info;
                tvUpdateState.setTextColor(0xFF2E7D32);
                tvUpdateState.setText("发现 v" + info.versionName);
                UpdatePrompt.show(this, info);
            }
        });
    }

    private void openGithub() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }
}
