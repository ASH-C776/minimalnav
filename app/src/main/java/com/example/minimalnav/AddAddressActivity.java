package com.example.minimalnav;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
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
    private EditText etLat;
    private EditText etLon;
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
        etLat = findViewById(R.id.etLat);
        etLon = findViewById(R.id.etLon);
        etPoiIdOnly = findViewById(R.id.etPoiIdOnly);
        coordSection = findViewById(R.id.coordSection);
        poiSection = findViewById(R.id.poiSection);
        btnCoord = findViewById(R.id.btnCoord);
        btnPoi = findViewById(R.id.btnPoi);

        buildPickers();
        applyMode(MODE_COORD);

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
            Double parsedLat = parseDouble(etLat.getText().toString());
            Double parsedLon = parseDouble(etLon.getText().toString());
            if (parsedLat == null || parsedLat < -90 || parsedLat > 90) {
                toast("纬度应在 -90 ~ 90 之间");
                return;
            }
            if (parsedLon == null || parsedLon < -180 || parsedLon > 180) {
                toast("经度应在 -180 ~ 180 之间");
                return;
            }
            lat = parsedLat;
            lon = parsedLon;
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
