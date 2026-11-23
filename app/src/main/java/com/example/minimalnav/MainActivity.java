package com.example.minimalnav;

import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String GAODE_PACKAGE = "com.autonavi.minimap";
    private static final String SOURCE = "秒导航";
    private static final int REQ_ADD = 2001;
    private static final int REQ_EXPORT = 2002;
    private static final int REQ_IMPORT = 2003;
    private static final int REQ_EDIT = 2004;
    /** 开机自检延迟：3 秒后再联网，不和首屏抢资源。 */
    private static final long UPDATE_CHECK_DELAY_MS = 3000;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());

    private AddressStore store;
    private List<Address> addresses;
    private AddressAdapter adapter;
    private TextView tvEmpty;
    private View sheetOverlay;
    private View sheetBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-Edge：顶部栏为深绿色，状态栏区域延伸其背景，故状态栏用浅色图标；
        // 页面底部为浅灰背景，导航栏用深色图标。图标对比度随实际背景显式指定。
        EdgeToEdge.enable(this, false, true);
        EdgeToEdge.top(findViewById(R.id.header), 0);

        store = new AddressStore(this);
        addresses = store.load();

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setClipToPadding(false);
        // 行高由内容与 minHeight 决定，尺寸对 Adapter 不透明，告诉 RecyclerView 不必反复测量
        recyclerView.setHasFixedSize(true);
        // 列表底部：保留 88dp 给 FAB，再叠加手势条高度（禁止用 margin 代替 padding）
        EdgeToEdge.bottom(recyclerView, 88, false);

        adapter = new AddressAdapter(addresses,
                this::navigateDirect,      // 单击：直接一键导航
                this::showActionSheet);    // 长按：路线 / 上移 / 编辑 / 删除
        recyclerView.setAdapter(adapter);

        tvEmpty = findViewById(R.id.tvEmpty);
        updateEmpty();

        View fab = findViewById(R.id.btnAdd);
        EdgeToEdge.bottomMargin(fab, 16);
        fab.setOnClickListener(v ->
                startActivityForResult(new Intent(this, AddAddressActivity.class), REQ_ADD));

        // 工具栏「⋮」：不弹系统 PopupMenu，用与长按菜单同款的圆角浮层
        findViewById(R.id.btnMore).setOnClickListener(v -> showPopover(
                new String[]{"导出清单", "导入清单", "关于"}, true, which -> {
                    if (which == 0) {
                        startExport();
                    } else if (which == 1) {
                        startImport();
                    } else {
                        startActivity(new Intent(this, AboutActivity.class));
                    }
                }));

        // 冷启动可观测：列表绑定完成即上报 fully drawn，便于用 adb 量化首屏耗时
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().getDecorView().post(this::reportFullyDrawn);
        }

        updateHandler.postDelayed(this::autoCheckUpdate, UPDATE_CHECK_DELAY_MS);
    }

    /**
     * 开机静默自检：24 小时最多查一次，只在主页面弹窗，用户点过取消的版本不再打扰。
     * 任何失败都不出声 —— 更新检测不能影响正常导航。
     */
    private void autoCheckUpdate() {
        if (isFinishing()) {
            return;
        }
        UpdatePrefs prefs = new UpdatePrefs(this);
        long now = System.currentTimeMillis();
        if (prefs.withinThrottle(now)) {
            return;
        }
        // 先记为已检查，即使后续请求失败也不在当天反复重试
        prefs.markChecked(now);

        UpdateChecker.checkAsync(UpdatePrompt.currentVersionCode(this), info -> {
            if (info == null || isFinishing()) {
                return;
            }
            if (info.versionCode == prefs.getDismissedCode()) {
                return;
            }
            UpdatePrompt.show(this, info);
        });
    }

    @Override
    protected void onDestroy() {
        updateHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- 添加 / 导入 / 导出 ----------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == REQ_ADD) {
            Address added = new Address(
                    data.getStringExtra(AddAddressActivity.EXTRA_NAME),
                    data.getDoubleExtra(AddAddressActivity.EXTRA_LAT, Double.NaN),
                    data.getDoubleExtra(AddAddressActivity.EXTRA_LON, Double.NaN),
                    data.getStringExtra(AddAddressActivity.EXTRA_POI),
                    data.getIntExtra(AddAddressActivity.EXTRA_COLOR, 0),
                    data.getIntExtra(AddAddressActivity.EXTRA_ICON, 0));
            addresses.add(added);
            store.save(addresses);
            adapter.notifyItemInserted(addresses.size() - 1);
            updateEmpty();
            toast("已添加地址");
            return;
        }

        if (requestCode == REQ_EDIT) {
            applyEdit(data);
            return;
        }

        if (data.getData() == null) {
            return;
        }
        if (requestCode == REQ_EXPORT) {
            exportTo(data.getData());
        } else if (requestCode == REQ_IMPORT) {
            importFrom(data.getData());
        }
    }

    /** 打开与「添加地址」同一个页面，只是把原值预填进去；保存后回到这里原地替换。 */
    private void editAddress(Address address) {
        int index = addresses.indexOf(address);
        if (index < 0) {
            return;
        }
        Intent intent = new Intent(this, AddAddressActivity.class);
        intent.putExtra(AddAddressActivity.EXTRA_POSITION, index);
        intent.putExtra(AddAddressActivity.EXTRA_NAME, address.getName());
        intent.putExtra(AddAddressActivity.EXTRA_LAT, address.getLat());
        intent.putExtra(AddAddressActivity.EXTRA_LON, address.getLon());
        intent.putExtra(AddAddressActivity.EXTRA_COLOR, address.getColorIndex());
        intent.putExtra(AddAddressActivity.EXTRA_ICON, address.getIconIndex());
        startActivityForResult(intent, REQ_EDIT);
    }

    /**
     * 编辑结果落到原条目上：名称 / 坐标 / 颜色 / 图标整体替换。
     * poiId 保留——重新填了坐标的旧条目，跳转时仍可把 id 一起带给高德。
     */
    private void applyEdit(Intent data) {
        int index = data.getIntExtra(AddAddressActivity.EXTRA_POSITION, -1);
        if (index < 0 || index >= addresses.size()) {
            return;
        }
        Address target = addresses.get(index);
        double lat = data.getDoubleExtra(AddAddressActivity.EXTRA_LAT, Double.NaN);
        double lon = data.getDoubleExtra(AddAddressActivity.EXTRA_LON, Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return;
        }
        target.setName(data.getStringExtra(AddAddressActivity.EXTRA_NAME));
        target.setLat(lat);
        target.setLon(lon);
        target.setColorIndex(data.getIntExtra(AddAddressActivity.EXTRA_COLOR, target.getColorIndex()));
        target.setIconIndex(data.getIntExtra(AddAddressActivity.EXTRA_ICON, target.getIconIndex()));
        store.save(addresses);
        adapter.notifyItemChanged(index);
        toast("已保存修改");
    }

    /**
     * 导出：Android 10+ 直接写入公共 Download 目录（MediaStore，零权限、无系统弹窗）；
     * Android 9 及以下退回系统文件选择器，并用 EXTRA_INITIAL_URI 把起始位置指到 Download。
     */
    private void startExport() {
        String baseName = "秒导航" + new SimpleDateFormat("yyMMdd", Locale.CHINA).format(new Date());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(baseName);
        } else {
            startExportViaSaf(baseName + ".json");
        }
    }

    /** 写入 Download：文件名形如「秒导航261122.json」，同日重复导出自动加 (1)(2)。 */
    private void saveToDownloads(String baseName) {
        Uri uri = null;
        try {
            String fileName = uniqueDownloadName(baseName);
            String json = AddressStore.exportJson(addresses);

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("无法创建文件");
            }
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) {
                throw new IOException("无法写入文件");
            }
            try {
                out.write(json.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            toast("已导出 " + addresses.size() + " 条到 Download/" + fileName);
        } catch (Exception e) {
            // 写一半失败时删掉残留的空文件，避免 Download 里出现 0 字节的脏数据
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            toast("导出失败");
        }
    }

    private String uniqueDownloadName(String baseName) {
        String candidate = baseName + ".json";
        int index = 1;
        while (downloadNameExists(candidate)) {
            candidate = baseName + "(" + index + ").json";
            index++;
        }
        return candidate;
    }

    private boolean downloadNameExists(String displayName) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {displayName, Environment.DIRECTORY_DOWNLOADS + "%"};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID},
                selection, args, null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void startExportViaSaf(String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDocumentUri());
        }
        try {
            startActivityForResult(intent, REQ_EXPORT);
        } catch (ActivityNotFoundException e) {
            toast("当前系统不支持文件保存");
        }
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        // 文件选择器默认落在 Download，导出的备份文件就在那里
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDocumentUri());
        }
        try {
            startActivityForResult(intent, REQ_IMPORT);
        } catch (ActivityNotFoundException e) {
            toast("当前系统不支持文件选择");
        }
    }

    /** 公共 Download 目录的 DocumentsProvider URI，供文件选择器定位起始位置。 */
    private static Uri downloadsDocumentUri() {
        return Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"
                + Environment.DIRECTORY_DOWNLOADS);
    }

    private void exportTo(android.net.Uri uri) {
        try {
            String json = AddressStore.exportJson(addresses);
            OutputStream out = getContentResolver().openOutputStream(uri, "w");
            if (out == null) {
                throw new IOException("无法写入文件");
            }
            try {
                out.write(json.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            toast("已导出 " + addresses.size() + " 条地址");
        } catch (Exception e) {
            toast("导出失败");
        }
    }

    private void importFrom(android.net.Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("无法读取文件");
            }
            String json;
            try {
                json = readText(in);
            } finally {
                in.close();
            }
            final List<Address> incoming = AddressStore.importJson(json);
            if (incoming.isEmpty()) {
                toast("文件中没有有效地址");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("导入 " + incoming.size() + " 条地址")
                    .setMessage("合并：保留现有地址，跳过重复项。\n覆盖：清空现有地址，只保留文件中的地址。")
                    .setPositiveButton("合并", (dialog, which) -> applyImport(incoming, false))
                    .setNegativeButton("覆盖", (dialog, which) -> applyImport(incoming, true))
                    .setNeutralButton("取消", null)
                    .show();
        } catch (JSONException e) {
            toast("文件格式不正确");
        } catch (Exception e) {
            toast("导入失败");
        }
    }

    private void applyImport(List<Address> incoming, boolean replace) {
        boolean bulkReplace = replace;
        int start = addresses.size();
        int added = 0;
        if (replace) {
            addresses.clear();
            addresses.addAll(incoming);
        } else {
            Set<String> keys = new HashSet<>();
            for (Address a : addresses) {
                keys.add(dedupKey(a));
            }
            for (Address a : incoming) {
                if (keys.add(dedupKey(a))) {
                    addresses.add(a);
                    added++;
                }
            }
            toast("已导入 " + added + " 条，跳过 " + (incoming.size() - added) + " 条重复");
        }
        store.save(addresses);
        if (bulkReplace) {
            adapter.notifyDataSetChanged();
        } else if (added > 0) {
            adapter.notifyItemRangeInserted(start, added);
        }
        updateEmpty();
        if (replace) {
            toast("已覆盖导入 " + incoming.size() + " 条地址");
        }
    }

    private static String dedupKey(Address a) {
        String coord = a.hasCoord()
                ? String.format(Locale.US, "%.6f", a.getLat()) + "|"
                    + String.format(Locale.US, "%.6f", a.getLon())
                : "id";
        return a.getName() + "|" + coord + "|" + a.getPoiId();
    }

    private static String readText(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ---------- 导航 ----------

    private void navigate(Address address, boolean direct) {
        // 旧数据里可能有只填了 POI ID 的条目：高德不认 POI ID，只能提示补坐标
        if (!address.hasCoord()) {
            showMissingCoordDialog(address);
            return;
        }
        // 坐标地址：需要高德 App
        if (!gaodeAvailable()) {
            showInstallDialog();
            return;
        }
        String uri = direct ? buildNaviUri(address) : buildRouteUri(address);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage(GAODE_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showInstallDialog();
        }
    }

    /**
     * 只有 POI ID、没有坐标的旧条目。
     * 高德没有「按 POI ID 打开详情 / 导航」的 scheme，网页版也只认标准 B0 开头 ID，
     * 甩给浏览器只会落在地图首页，所以这里只提示用户补坐标。
     */
    private void showMissingCoordDialog(Address address) {
        new AlertDialog.Builder(this)
                .setTitle("这条地址缺少坐标")
                .setMessage("「" + address.getName() + "」只有 POI ID，高德无法直接导航。\n"
                        + "补上坐标后就能一键直达。")
                .setPositiveButton("去编辑", (dialog, which) -> editAddress(address))
                .setNegativeButton("知道了", null)
                .show();
    }

    /** 官方：androidamap://navi 直接开始导航，以当前位置为起点。 */
    private String buildNaviUri(Address a) {
        StringBuilder sb = new StringBuilder("androidamap://navi?sourceApplication=")
                .append(enc(SOURCE))
                .append("&poiname=").append(enc(a.getName()))
                .append("&lat=").append(a.getLat())
                .append("&lon=").append(a.getLon())
                .append("&dev=0")
                .append("&style=2");
        if (!TextUtils.isEmpty(a.getPoiId())) {
            sb.append("&poiid=").append(enc(a.getPoiId()));
        }
        return sb.toString();
    }

    /** 官方：amapuri://route/plan/ 驾车路线规划，t=0。 */
    private String buildRouteUri(Address a) {
        StringBuilder sb = new StringBuilder("amapuri://route/plan/?sourceApplication=")
                .append(enc(SOURCE))
                .append("&dlat=").append(a.getLat())
                .append("&dlon=").append(a.getLon())
                .append("&dname=").append(enc(a.getName()))
                .append("&dev=0")
                .append("&t=0");
        if (!TextUtils.isEmpty(a.getPoiId())) {
            sb.append("&did=").append(enc(a.getPoiId()));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return Uri.encode(s == null ? "" : s, null);
    }

    private boolean gaodeAvailable() {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("amapuri://route/plan/"));
        probe.setPackage(GAODE_PACKAGE);
        return probe.resolveActivity(getPackageManager()) != null;
    }

    private void showInstallDialog() {
        new AlertDialog.Builder(this)
                .setTitle("未安装高德地图")
                .setMessage("驾车导航需要高德地图 App，是否前往下载？")
                .setPositiveButton("去下载", (dialog, which) -> openMarket())
                .setNegativeButton("取消", null)
                .show();
    }

    private void openMarket() {
        try {
            Intent market = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + GAODE_PACKAGE));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(market);
        } catch (ActivityNotFoundException e) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/"));
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(web);
            } catch (ActivityNotFoundException ignored) {
                toast("请先安装高德地图");
            }
        }
    }

    // ---------- 浮层菜单：右侧 · 垂直居中偏下 ----------

    /** 单击：直接一键导航；纯 POI ID 地址走高德 Web POI 详情。 */
    private void navigateDirect(Address address) {
        navigate(address, true);
    }

    private interface OnPopoverPick {
        void onPick(int index);
    }

    private void showActionSheet(final Address address) {
        // 聚焦态：被操作的卡片放大 + 绿色描边，其余行淡出，明确当前操作对象
        adapter.setFocusPosition(addresses.indexOf(address));
        showPopover(new String[]{"路线", "编辑", "上移", "删除"}, false,
                which -> handleAction(address, which));
    }

    /**
     * 统一浮层：圆角磨砂玻璃底 + 竖排纯文字项。走 Activity 内覆盖层而不是系统 PopupMenu，
     * 这样才能自定义圆角、位置与动效，且不会破坏沉浸式导航栏。
     *
     * @param upperRight true = 贴在工具栏「⋮」按钮左下方；false = 右侧垂直居中偏下
     */
    private void showPopover(String[] labels, boolean upperRight, OnPopoverPick onPick) {
        ensureSheet();
        if (sheetBody == null) {
            return;
        }
        LinearLayout container = findViewById(R.id.actionContainer);
        container.removeAllViews();

        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView row = new TextView(this);
            row.setText(labels[i]);
            row.setTextSize(16);
            row.setGravity(Gravity.CENTER);
            row.setSingleLine(true);
            row.setPadding(dp(18), 0, dp(18), 0);
            row.setBackgroundResource(R.drawable.bg_popover_row);
            row.setTextColor("删除".equals(labels[i]) ? 0xFFC04545 : 0xFF1C1C1A);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
            lp.topMargin = i == 0 ? 0 : dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                hideSheet();
                onPick.onPick(which);
            });
            container.addView(row);
        }

        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) sheetBody.getLayoutParams();
        if (upperRight) {
            // 「⋮」左下方：右边缘比按钮再收进 12dp，顶部距工具栏 4dp
            flp.gravity = Gravity.TOP | Gravity.END;
            flp.topMargin = findViewById(R.id.header).getBottom() + dp(4);
            flp.rightMargin = dp(12);
            sheetBody.setTranslationY(0);
        } else {
            flp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            flp.topMargin = 0;
            flp.rightMargin = dp(24);
            sheetBody.setTranslationY(dp(56));
        }
        sheetBody.setLayoutParams(flp);

        // 覆盖层出现 + 浮层轻微放大，纯 View 动画，零新增依赖
        sheetOverlay.setVisibility(View.VISIBLE);
        sheetOverlay.setAlpha(1f);
        sheetBody.setScaleX(0.94f);
        sheetBody.setScaleY(0.94f);
        sheetBody.setAlpha(0f);
        sheetBody.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * 首次打开菜单时才膨胀覆盖层（ViewStub）。之后复用同一个实例，
     * 只有菜单项内容会被重建，避免每次点击都重新 inflate 整棵树。
     */
    private void ensureSheet() {
        if (sheetOverlay != null) {
            return;
        }
        android.view.ViewStub stub = findViewById(R.id.sheetStub);
        if (stub != null) {
            stub.inflate();
        }
        sheetOverlay = findViewById(R.id.sheetOverlay);
        sheetBody = findViewById(R.id.sheetRoot);
        if (sheetBody == null || sheetOverlay == null) {
            return;
        }
        sheetBody.setTranslationY(dp(56));
        sheetOverlay.setOnClickListener(v -> hideSheet());
    }

    private void hideSheet() {
        if (sheetOverlay == null || sheetOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        sheetOverlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            sheetOverlay.setVisibility(View.GONE);
            adapter.setFocusPosition(-1);
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (sheetOverlay != null && sheetOverlay.getVisibility() == View.VISIBLE) {
            hideSheet();
            return;
        }
        super.onBackPressed();
    }

    private void handleAction(Address address, int which) {
        switch (which) {
            case 0:
                navigate(address, false);   // 路线规划
                break;
            case 1:
                editAddress(address);
                break;
            case 2:
                moveUp(address);
                break;
            case 3:
                confirmDelete(address);
                break;
            default:
                break;
        }
    }

    /** 上移一格。 */
    private void moveUp(Address address) {
        int index = addresses.indexOf(address);
        if (index <= 0) {
            toast("已在最前");
            return;
        }
        Collections.swap(addresses, index, index - 1);
        store.save(addresses);
        // 只移动一行：避免了 notifyDataSetChanged 触发的全表重新绑定
        adapter.notifyItemMoved(index, index - 1);
    }

    private void confirmDelete(Address address) {
        new AlertDialog.Builder(this)
                .setTitle("删除地址")
                .setMessage("确定删除「" + address.getName() + "」？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int index = addresses.indexOf(address);
                    if (index < 0) {
                        return;
                    }
                    addresses.remove(index);
                    store.save(addresses);
                    adapter.notifyItemRemoved(index);
                    updateEmpty();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 工具 ----------

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics());
    }

    private void updateEmpty() {
        tvEmpty.setVisibility(addresses.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
