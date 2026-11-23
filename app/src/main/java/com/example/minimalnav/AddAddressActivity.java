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

/** 添加地址页：坐标录入 / POI ID 录入两种模式，均不依赖地图 SDK 或 Key。 */
public class AddAddressActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    public static final String EXTRA_POI = "poiId";
    public static final String EXTRA_COLOR = "colorIndex";
    public static final String EXTRA_ICON = "iconIndex";

    private static final int MODE_COORD = 0;
    private static final int MODE_POI = 1;

    private EditText etName;
    private EditText etCoord;
    private TextView tvCoordEcho;
    private EditText etPoiIdOnly;
    private View coordSection;
    private View poiSection;
    private TextView btnCoord;
    private TextView btnPoi;
    private int mode = MODE_COORD;

    private final View[] colorViews = new View[6];
    private final ImageView[] iconViews = new ImageView[6];
    private int selectedColor;
    private int selectedIcon;

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
        etPoiIdOnly = findViewById(R.id.etPoiIdOnly);
        coordSection = findViewById(R.id.coordSection);
        poiSection = findViewById(R.id.poiSection);
        btnCoord = findViewById(R.id.btnCoord);
        btnPoi = findViewById(R.id.btnPoi);

        buildPickers();
        applyMode(MODE_COORD);

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

        btnCoord.setOnClickListener(v -> applyMode(MODE_COORD));
        btnPoi.setOnClickListener(v -> applyMode(MODE_POI));
        findViewById(R.id.btnSave).setOnClickListener(v -> saveAndFinish());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyMode(int newMode) {
        mode = newMode;
        boolean coord = mode == MODE_COORD;
        coordSection.setVisibility(coord ? View.VISIBLE : View.GONE);
        poiSection.setVisibility(coord ? View.GONE : View.VISIBLE);

        btnCoord.setBackgroundResource(coord ? R.drawable.bg_add : R.drawable.bg_card);
        btnCoord.setTextColor(coord ? 0xFFFFFFFF : 0xFF2E7D32);
        btnPoi.setBackgroundResource(coord ? R.drawable.bg_card : R.drawable.bg_add);
        btnPoi.setTextColor(coord ? 0xFF2E7D32 : 0xFFFFFFFF);
    }

    private void saveAndFinish() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            toast("请输入备注名");
            return;
        }

        double lat = Double.NaN;
        double lon = Double.NaN;
        String poi = "";

        if (mode == MODE_COORD) {
            double[] pair = parseCoordPair(etCoord.getText().toString());
            if (pair == null) {
                toast("坐标要两个数字，如 113.248456,23.147297");
                return;
            }
            lat = pair[0];
            lon = pair[1];
        } else {
            poi = etPoiIdOnly.getText().toString().trim();
            if (TextUtils.isEmpty(poi)) {
                toast("请输入 POI ID");
                return;
            }
        }

        Intent result = new Intent();
        result.putExtra(EXTRA_NAME, name);
        result.putExtra(EXTRA_LAT, lat);
        result.putExtra(EXTRA_LON, lon);
        result.putExtra(EXTRA_POI, poi);
        result.putExtra(EXTRA_COLOR, selectedColor);
        result.putExtra(EXTRA_ICON, selectedIcon);
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
     * 高德坐标拾取器复制出来是「经度,纬度」（113.248456,23.147297），
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
