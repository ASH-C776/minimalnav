package com.example.minimalnav;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;

import java.util.Locale;

/**
 * 添加地址页：只需要「坐标」一项。
 *
 * 不做 POI ID 录入——高德 App 没有「按 POI ID 打开详情 / 导航」的入口，
 * 只填 ID 的条目点击时只能落到浏览器网页，体验比多粘一次坐标更差。
 */
public class AddAddressActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    /** 保留仅用于兼容旧调用方；新版录入不再产生 POI ID。 */
    public static final String EXTRA_POI = "poiId";
    public static final String EXTRA_COLOR = "colorIndex";
    public static final String EXTRA_ICON = "iconIndex";
    /**
     * 编辑模式：传入被编辑条目的下标，保存后原样带回，主页据此原地替换。
     * 不带这个 extra（或为负数）就是新增。
     */
    public static final String EXTRA_POSITION = "position";

    private EditText etName;
    private EditText etCoord;
    private TextView tvCoordEcho;

    private final View[] colorViews = new View[6];
    private final ImageView[] iconViews = new ImageView[6];
    private int selectedColor;
    private int selectedIcon;
    /** >= 0 表示在编辑已有条目；-1 表示新增。 */
    private int editPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_address);

        // 与主页一致：绿色 Toolbar 上用浅色状态栏图标，浅灰底上用深色导航栏图标
        EdgeToEdge.enable(this, false, true);
        Toolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdge.top(toolbar, 0);
        setSupportActionBar(toolbar);

        // 表单页必须处理键盘：bottom = max(导航栏, ime)，软键盘弹出时不遮挡输入框
        ScrollView formScroll = findViewById(R.id.formScroll);
        EdgeToEdge.bottom(formScroll, 0, true);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etName = findViewById(R.id.etName);
        etCoord = findViewById(R.id.etCoord);
        tvCoordEcho = findViewById(R.id.tvCoordEcho);

        // 编辑模式与新增共用这一个页面：把原值预填进来，标题和按钮换成「编辑」的说法
        editPosition = getIntent().getIntExtra(EXTRA_POSITION, -1);
        if (isEditing()) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("编辑地址");
            }
            ((TextView) findViewById(R.id.btnSave)).setText("保存修改");
            prefillFromIntent();
        }

        buildPickers();

        // 边输入边回显识别结果：顺序搞反、少一位、粘错格式都能立刻看见
        etCoord.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCoordEcho();
            }
        });

        // 光标一进输入框就把提示语收走，避免和用户粘进来的坐标叠在一起
        final CharSequence coordHint = etCoord.getHint();
        etCoord.setOnFocusChangeListener((v, hasFocus) ->
                etCoord.setHint(hasFocus ? "" : coordHint));

        // 预填的坐标是直接 setText 进去的，监听器挂上之前不会触发回显，这里补一次
        if (isEditing() && !etCoord.getText().toString().trim().isEmpty()) {
            updateCoordEcho();
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> saveAndFinish());
    }

    private boolean isEditing() {
        return editPosition >= 0;
    }

    /** 把主页传来的原值填进表单：坐标按「经度,纬度」还原成可粘贴的样子。 */
    private void prefillFromIntent() {
        Intent intent = getIntent();
        etName.setText(intent.getStringExtra(EXTRA_NAME));
        double lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN);
        double lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN);
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            etCoord.setText(String.format(Locale.US, "%.6f,%.6f", lon, lat));
        }
        selectedColor = clampIndex(intent.getIntExtra(EXTRA_COLOR, 0));
        selectedIcon = clampIndex(intent.getIntExtra(EXTRA_ICON, 0));
    }

    private static int clampIndex(int value) {
        return value < 0 || value > 5 ? 0 : value;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveAndFinish() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            toast("请输入备注名");
            return;
        }

        double[] pair = parseCoordPair(etCoord.getText().toString());
        if (pair == null) {
            toast("坐标要两个数字，如 113.324076,23.111357");
            return;
        }

        Intent result = new Intent();
        result.putExtra(EXTRA_NAME, name);
        result.putExtra(EXTRA_LAT, pair[0]);
        result.putExtra(EXTRA_LON, pair[1]);
        result.putExtra(EXTRA_POI, "");
        result.putExtra(EXTRA_COLOR, selectedColor);
        result.putExtra(EXTRA_ICON, selectedIcon);
        result.putExtra(EXTRA_POSITION, editPosition);
        setResult(RESULT_OK, result);
        finish();
    }

    private void buildPickers() {
        LinearLayout colorRow = findViewById(R.id.colorRow);
        LinearLayout iconRow = findViewById(R.id.iconRow);

        for (int i = 0; i < 6; i++) {
            final int index = i;

            View swatch = new View(this);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(36), dp(36));
            cp.setMargins(0, 0, dp(10), 0);
            swatch.setLayoutParams(cp);
            swatch.setOnClickListener(v -> {
                selectedColor = index;
                refreshPickers();
            });
            colorRow.addView(swatch);
            colorViews[i] = swatch;

            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
            ip.setMargins(0, 0, dp(8), 0);
            icon.setLayoutParams(ip);
            icon.setImageResource(Palette.ICONS[i]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setOnClickListener(v -> {
                selectedIcon = index;
                refreshPickers();
            });
            iconRow.addView(icon);
            iconViews[i] = icon;
        }
        refreshPickers();
    }

    private void refreshPickers() {
        for (int i = 0; i < 6; i++) {
            GradientDrawable colorBg = new GradientDrawable();
            colorBg.setShape(GradientDrawable.OVAL);
            colorBg.setColor(Palette.COLORS[i]);
            colorBg.setStroke(i == selectedColor ? dp(3) : dp(1),
                    i == selectedColor ? 0xFF222222 : 0xFFD3D1C7);
            colorViews[i].setBackground(colorBg);

            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(dp(8));
            iconBg.setColor(i == selectedIcon ? 0xFFE8F5E9 : 0xFFF1EFE8);
            iconBg.setStroke(dp(1),
                    i == selectedIcon ? Palette.COLORS[selectedColor] : 0xFFD3D1C7);
            iconViews[i].setBackground(iconBg);

            Drawable drawable = iconViews[i].getDrawable();
            if (drawable != null) {
                drawable = DrawableCompat.wrap(drawable).mutate();
                DrawableCompat.setTint(drawable, Palette.COLORS[i]);
                iconViews[i].setImageDrawable(drawable);
            }
        }
    }

    /**
     * 解析粘贴进来的坐标对，返回 {纬度, 经度}；认不出来返回 null。
     *
     * 高德坐标拾取器复制出来是「经度,纬度」（113.324076,23.111357），
     * 而多数工具与程序内部用的是「纬度,经度」，两个顺序都得收：
     * 经度最大到 180、纬度最大到 90，谁超过 90 谁就只能是经度，靠这一点判序；
     * 两个数都不超过 90 时按高德约定「经度在前」。
     */
    private static double[] parseCoordPair(String raw) {
        if (raw == null) {
            return null;
        }
        String s = normalizeCoordText(raw);
        if (s.isEmpty()) {
            return null;
        }
        String[] parts = s.split("[,\\s]+");
        if (parts.length != 2) {
            return null;
        }
        Double parsedFirst = parseDouble(parts[0]);
        Double parsedSecond = parseDouble(parts[1]);
        if (parsedFirst == null || parsedSecond == null) {
            return null;
        }
        double a = parsedFirst;
        double b = parsedSecond;
        if (Math.abs(a) > 180 || Math.abs(b) > 180) {
            return null;
        }
        double lat;
        double lon;
        if (Math.abs(b) > 90) {
            // 第二个数超过 90，只可能是经度：粘的是「纬度,经度」
            lat = a;
            lon = b;
        } else {
            // 高德约定：第一个数是经度
            lon = a;
            lat = b;
        }
        if (Math.abs(lat) > 90) {
            return null;
        }
        return new double[]{lat, lon};
    }

    /** 统一全角字符与分隔符，丢掉括号等噪声，让手粘的坐标尽量能认出来。 */
    private static String normalizeCoordText(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '０' && c <= '９') {
                c = (char) (c - '０' + '0');
            } else if (c == '．' || c == '。') {
                c = '.';
            } else if (c == '，' || c == '、' || c == '；' || c == ';') {
                c = ',';
            } else if (c == '－' || c == '—' || c == '−') {
                c = '-';
            } else if (c == '＋') {
                c = '+';
            } else if (c == '（' || c == '）' || c == '(' || c == ')'
                    || c == '[' || c == ']' || c == '【' || c == '】') {
                continue;
            } else if (c == '\u3000' || c == '\t' || c == '\n' || c == '\r') {
                c = ' ';
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private void updateCoordEcho() {
        String raw = etCoord.getText().toString().trim();
        if (raw.isEmpty()) {
            tvCoordEcho.setVisibility(View.GONE);
            return;
        }
        tvCoordEcho.setVisibility(View.VISIBLE);
        double[] pair = parseCoordPair(raw);
        if (pair == null) {
            tvCoordEcho.setTextColor(0xFFD32F2F);
            tvCoordEcho.setText("认不出来。要「经度,纬度」两个数字，经度 -180~180、纬度 -90~90");
        } else {
            tvCoordEcho.setTextColor(0xFF2E7D32);
            tvCoordEcho.setText(String.format(Locale.US,
                    "纬度 %.6f · 经度 %.6f", pair[0], pair[1]));
        }
    }

    private static Double parseDouble(String s) {
        try {
            return Double.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
