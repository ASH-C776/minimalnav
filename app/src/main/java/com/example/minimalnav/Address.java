package com.example.minimalnav;

/**
 * 地址数据模型。支持两种录入形态：
 *   1) 坐标模式：lat/lon 为 GCJ-02 有效值；
 *   2) POI ID 模式：lat/lon 为 Double.NaN，仅有 poiId，点击时打开高德 POI 详情页。
 * colorIndex / iconIndex 均为 0..5，对应 Palette 中的调色板与图标数组。
 */
public class Address {
    private String name;
    private double lat;
    private double lon;
    private String poiId;
    private int colorIndex;
    private int iconIndex;

    public Address(String name, double lat, double lon, String poiId,
                   int colorIndex, int iconIndex) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.poiId = poiId == null ? "" : poiId;
        this.colorIndex = colorIndex;
        this.iconIndex = iconIndex;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    /** 是否带有有效坐标；false 表示纯 POI ID 地址。 */
    public boolean hasCoord() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    public String getPoiId() { return poiId; }
    public void setPoiId(String poiId) { this.poiId = poiId == null ? "" : poiId; }

    public int getColorIndex() { return colorIndex; }
    public void setColorIndex(int colorIndex) { this.colorIndex = colorIndex; }

    public int getIconIndex() { return iconIndex; }
    public void setIconIndex(int iconIndex) { this.iconIndex = iconIndex; }
}
