package com.example.minimalnav;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 在线更新：读 Gitee / GitHub 的公开 Release 列表，找出比当前版本更高的正式版。
 *
 * 三条硬规则（照 fn-share-tv 踩坑总结）：
 *   1. 失败全部静默 —— 网络不通、API 改结构、解析失败一律吞掉，绝不冒到界面上；
 *   2. 只认非 prerelease / 非 draft 且带 .apk 附件的 Release，避免把测试包推给用户；
 *   3. 两端并发、任一端失败不影响另一端，同版本时 Gitee 优先。
 *
 * 零依赖：用 JDK 自带的 HttpURLConnection，JSON 用系统内置的 org.json。
 */
final class UpdateChecker {

    /** 改仓库地址只改这两行。 */
    static final String GITEE_REPO = "kijeye/minimalnav";
    static final String GITHUB_REPO = "ASH-C776/minimalnav";

    private static final String GITEE_API =
            "https://gitee.com/api/v5/repos/" + GITEE_REPO + "/releases";
    private static final String GITHUB_API =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases";

    private static final String USER_AGENT = "MiaoNav-Updater";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int BUFFER_SIZE = 64 * 1024;
    /** 小于 1KB 一律判定为坏包（0 字节文件、HTML 错误页）。 */
    private static final long MIN_APK_BYTES = 1024;

    interface CheckCallback {
        /** info 为 null 表示已是最新，或两端都查不到。 */
        void onResult(UpdateInfo info);
    }

    interface DownloadCallback {
        void onProgress(int percent);

        void onSuccess(File apk);

        void onFailure();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {
    }

    /** 后台查两端 Release，结果回主线程。 */
    static void checkAsync(int currentCode, CheckCallback callback) {
        new Thread(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<UpdateInfo> all = new ArrayList<>();
            try {
                Future<List<UpdateInfo>> gitee = pool.submit(() -> fetch(GITEE_API, "gitee"));
                Future<List<UpdateInfo>> github = pool.submit(() -> fetch(GITHUB_API, "github"));

                // Gitee 是国内主源，先取；最多等 10 秒
                boolean giteeHit = false;
                try {
                    List<UpdateInfo> giteeResult = gitee.get(10, TimeUnit.SECONDS);
                    all.addAll(giteeResult);
                    giteeHit = UpdateInfo.pickLatest(giteeResult, currentCode) != null;
                } catch (Exception ignored) {
                }

                // Gitee 已有新版时只给 GitHub 4 秒补充机会：国内它常连不上，
                // 不能为了“取两端最高版本”让用户盯着空界面干等。
                int waitSeconds = giteeHit ? 4 : 12;
                try {
                    all.addAll(github.get(waitSeconds, TimeUnit.SECONDS));
                } catch (Exception ignored) {
                }
            } finally {
                pool.shutdownNow();
            }
            UpdateInfo best = UpdateInfo.pickLatest(all, currentCode);
            MAIN.post(() -> callback.onResult(best));
        }, "update-check").start();
    }

    private static List<UpdateInfo> fetch(String apiUrl, String source) {
        List<UpdateInfo> result = new ArrayList<>();
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return result;
            }
            in = conn.getInputStream();
            JSONArray releases = new JSONArray(readText(in));
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null) {
                    continue;
                }
                // 测试包、草稿一律跳过，避免把半成品推给用户
                if (release.optBoolean("prerelease") || release.optBoolean("draft")) {
                    continue;
                }
                String tag = release.optString("tag_name", "");
                Integer code = UpdateInfo.parseVersionCode(tag);
                if (code == null) {
                    continue;
                }
                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) {
                    continue;
                }
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject asset = assets.optJSONObject(j);
                    if (asset == null) {
                        continue;
                    }
                    String name = asset.optString("name", "");
                    String url = asset.optString("browser_download_url", "");
                    // 只认 .apk，排除 Gitee 自动附带的 source.zip / tar.gz
                    if (name.toLowerCase(Locale.US).endsWith(".apk") && !url.isEmpty()) {
                        result.add(new UpdateInfo(plainVersionName(tag), code, url, source));
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 静默：更新检测不能变成用户可见的报错
        } finally {
            closeQuietly(in);
            if (conn != null) {
                conn.disconnect();
            }
        }
        return result;
    }

    /** v1.2.0-beta → 1.2.0 */
    private static String plainVersionName(String tag) {
        String s = tag.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int dash = s.indexOf('-');
        if (dash > 0) {
            s = s.substring(0, dash);
        }
        int plus = s.indexOf('+');
        if (plus > 0) {
            s = s.substring(0, plus);
        }
        return s;
    }

    /**
     * 下载到 filesDir/update/，64KB 缓冲逐块写。
     * 两道校验：有 Content-Length 时必须对得上；文件不得小于 1KB。失败删半截文件。
     */
    static void downloadAsync(Context context, UpdateInfo info, DownloadCallback callback) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            File dir = new File(appContext.getFilesDir(), "update");
            if (!dir.exists() && !dir.mkdirs()) {
                MAIN.post(callback::onFailure);
                return;
            }
            File apk = new File(dir, "秒导航-" + info.versionName + ".apk");
            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                conn = (HttpURLConnection) new URL(info.downloadUrl).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setInstanceFollowRedirects(true);
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + conn.getResponseCode());
                }
                long total = conn.getContentLength();
                in = new BufferedInputStream(conn.getInputStream());
                out = new FileOutputStream(apk);
                byte[] buffer = new byte[BUFFER_SIZE];
                long done = 0L;
                int lastPercent = -1;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        // 进度只在整数变化时上报，避免每 64KB 都刷一次界面
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final int value = percent;
                            MAIN.post(() -> callback.onProgress(value));
                        }
                    }
                }
                out.flush();
                closeQuietly(out);
                out = null;
                closeQuietly(in);
                in = null;

                long length = apk.length();
                if (total > 0 && length != total) {
                    throw new IOException("size mismatch");
                }
                if (length < MIN_APK_BYTES) {
                    throw new IOException("file too small");
                }
                MAIN.post(() -> callback.onSuccess(apk));
            } catch (Throwable t) {
                closeQuietly(out);
                closeQuietly(in);
                if (apk.exists() && !apk.delete()) {
                    apk.deleteOnExit();
                }
                MAIN.post(callback::onFailure);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, "update-download").start();
    }

    private static String readText(InputStream in) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = buffered.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), "UTF-8");
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
