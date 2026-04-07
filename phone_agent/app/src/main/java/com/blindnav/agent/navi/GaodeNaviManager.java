package com.blindnav.agent.navi;

import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeResult;

/**
 * 宏观导航代理引擎 (基于高德 SDK 11.1.000)
 * 负责接收树莓派指令，搜索坐标，并在后台静默算路播报
 */
public class GaodeNaviManager implements AMapNaviListener {
    private static final String TAG = "GaodeNaviManager";
    private Context context;
    @VisibleForTesting AMapNavi mAMapNavi;
    @VisibleForTesting GeocodeSearch geocodeSearch;
    @VisibleForTesting AMapLocationClient locationClient;
    @VisibleForTesting AMapLocationClientOption locationOption;
    @VisibleForTesting LocationCallback pendingLocationCallback;
    private NaviEventCallback naviEventCallback;

    // 6. 导航事件回调接口 — 用于向 SocketServerService 回传导航状态
    public interface NaviEventCallback {
        void onNaviEvent(String status, String event, int distance);
        void onArrived();
    }

    public void setNaviEventCallback(NaviEventCallback callback) {
        this.naviEventCallback = callback;
    }

    @VisibleForTesting
    NaviEventCallback getNaviEventCallback() {
        return naviEventCallback;
    }

    public interface LocationCallback {
        void onLocationSuccess(LocationSnapshot locationSnapshot);
        void onLocationFailure(String reason);
    }

    public static class LocationSnapshot {
        private final boolean success;
        private final double latitude;
        private final double longitude;
        private final String address;
        private final String city;
        private final String provider;
        private final String detail;

        private LocationSnapshot(boolean success, double latitude, double longitude, String address,
                                 String city, String provider, String detail) {
            this.success = success;
            this.latitude = latitude;
            this.longitude = longitude;
            this.address = address;
            this.city = city;
            this.provider = provider;
            this.detail = detail;
        }

        public static LocationSnapshot success(double latitude, double longitude, String address,
                                               String city, String provider, String detail) {
            return new LocationSnapshot(true, latitude, longitude, address, city, provider, detail);
        }

        public static LocationSnapshot failure(String detail) {
            return new LocationSnapshot(false, 0.0, 0.0, "", "", "", detail);
        }

        public boolean isSuccess() { return success; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getProvider() { return provider; }
        public String getDetail() { return detail; }
    }

    public GaodeNaviManager(Context context) {
        this.context = context.getApplicationContext();
        initSearchEngine();
        initNaviEngine();
        initLocationEngine();
    }

    @VisibleForTesting
    GaodeNaviManager(Context context, AMapNavi navi,
                      GeocodeSearch geocode,
                      AMapLocationClient loc) {
        this.context = context.getApplicationContext();
        this.mAMapNavi = navi;
        this.geocodeSearch = geocode;
        this.locationClient = loc;
    }

    @VisibleForTesting
    void setNaviEngine(AMapNavi navi) {
        this.mAMapNavi = navi;
    }

    @VisibleForTesting
    void setGeocodeProvider(GeocodeSearch geocode) {
        this.geocodeSearch = geocode;
    }

    @VisibleForTesting
    void setLocationClient(AMapLocationClient loc) {
        this.locationClient = loc;
    }

    // 1. 初始化地理编码引擎 (文字地名 -> 经纬度)
    @VisibleForTesting
    void initSearchEngine() {
        if (geocodeSearch == null) {
            try {
                geocodeSearch = new GeocodeSearch(context);
            } catch (AMapException e) {
                Log.e(TAG, "初始化地理编码引擎失败", e);
                return;
            }
        }
        geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
            @Override
            public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
                // 逆地理编码回调（经纬度转文字），暂时不用
            }

            @Override
            public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
                // 正地理编码回调（文字转经纬度）
                if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                    if (geocodeResult != null && geocodeResult.getGeocodeAddressList() != null
                            && geocodeResult.getGeocodeAddressList().size() > 0) {

                        // 获取搜索到的第一个结果的坐标
                        double lat = geocodeResult.getGeocodeAddressList().get(0).getLatLonPoint().getLatitude();
                        double lon = geocodeResult.getGeocodeAddressList().get(0).getLatLonPoint().getLongitude();
                        Log.d(TAG, "目标地点解析成功: 纬度=" + lat + ", 经度=" + lon);

                        // 拿到坐标后，调用步行算路函数
                        calculateWalkRoute(lat, lon);
                    }
                } else {
                    Log.e(TAG, "地址解析失败，错误码: " + rCode);
                }
            }
        });
    }

    // 2. 初始化导航引擎
    @VisibleForTesting
    void initNaviEngine() {
        if (mAMapNavi == null) {
            try {
                mAMapNavi = AMapNavi.getInstance(context);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            // 默认开启内置的语音播报
            mAMapNavi.setUseInnerVoice(true);
            // 注册导航事件监听器
            mAMapNavi.addAMapNaviListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2.5 步行算路 — 从当前位置导航至目标经纬度
    private void calculateWalkRoute(double endLat, double endLon) {
        Log.d(TAG, "开始步行算路至: " + endLat + ", " + endLon);
        NaviLatLng end = new NaviLatLng(endLat, endLon);
        // SDK 10.x: calculateWalkRoute(NaviLatLng to) — 起点自动使用 GPS 定位
        if (mAMapNavi != null) {
            mAMapNavi.calculateWalkRoute(end);
        }
    }

    // 3. 初始化定位引擎 (实时查询当前位置)
    @VisibleForTesting
    void initLocationEngine() {
        if (locationClient == null) {
            try {
                locationClient = new AMapLocationClient(context);
            } catch (Exception e) {
                Log.e(TAG, "初始化定位引擎失败", e);
                return;
            }
        }
        try {
            locationOption = new AMapLocationClientOption();
            locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            locationOption.setNeedAddress(true);
            locationOption.setOnceLocation(true);
            locationOption.setOnceLocationLatest(true);
            locationOption.setWifiScan(true);
            locationOption.setMockEnable(false);
            locationOption.setInterval(2000);

            locationClient.setLocationOption(locationOption);
            locationClient.setLocationListener(new AMapLocationListener() {
                @Override
                public void onLocationChanged(AMapLocation amapLocation) {
                    if (pendingLocationCallback == null) {
                        return;
                    }

                    if (amapLocation == null) {
                        pendingLocationCallback.onLocationFailure("定位返回为空");
                        return;
                    }

                    if (amapLocation.getErrorCode() == 0) {
                        LocationSnapshot snapshot = LocationSnapshot.success(
                                amapLocation.getLatitude(),
                                amapLocation.getLongitude(),
                                safeText(amapLocation.getAddress()),
                                safeText(amapLocation.getCity()),
                                safeText(amapLocation.getProvider()),
                                safeText(amapLocation.getLocationDetail()));
                        pendingLocationCallback.onLocationSuccess(snapshot);
                    } else {
                        String message = "定位失败: " + amapLocation.getErrorCode() + " / " + amapLocation.getErrorInfo();
                        pendingLocationCallback.onLocationFailure(message);
                    }

                    stopLocationQuery();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "初始化定位引擎失败", e);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    // 4. 供 UI 或其他模块调用的当前位置查询入口
    public void queryCurrentLocation(LocationCallback callback) {
        if (callback == null) {
            return;
        }

        if (locationClient == null) {
            callback.onLocationFailure("定位引擎未初始化");
            return;
        }

        pendingLocationCallback = callback;
        try {
            locationClient.startLocation();
        } catch (Exception e) {
            Log.e(TAG, "启动定位失败", e);
            stopLocationQuery();
            callback.onLocationFailure("启动定位失败: " + e.getMessage());
        }
    }

    public void stopLocationQuery() {
        try {
            if (locationClient != null) {
                locationClient.stopLocation();
            }
        } catch (Exception e) {
            Log.e(TAG, "停止定位失败", e);
        } finally {
            pendingLocationCallback = null;
        }
    }

    // 5. 供外部 (Socket 接收器) 调用的核心公开方法
    public void startNavigationTo(String destinationName, String cityName) {
        Log.d(TAG, "收到树莓派指令，准备导航至: " + destinationName);
        // 构造搜索条件并发出异步搜索请求
        GeocodeQuery query = new GeocodeQuery(destinationName, cityName);
        if (geocodeSearch != null) {
            geocodeSearch.getFromLocationNameAsyn(query);
        }
    }

    public void stopNavigation() {
        Log.d(TAG, "停止导航并释放位置服务");
        if (mAMapNavi != null) {
            mAMapNavi.stopNavi();
        }
        stopLocationQuery();
    }

    // 销毁时释放资源，防止内存泄漏
    public void destroy() {
        stopNavigation();
        if (locationClient != null) {
            locationClient.onDestroy();
            locationClient = null;
        }
        if (mAMapNavi != null) {
            mAMapNavi.removeAMapNaviListener(this);
            mAMapNavi.destroy();
        }
    }

    // ===================== AMapNaviListener 回调实现 (SDK 10.x) =====================

    @Override
    public void onCalculateRouteSuccess(int[] ints) {
        // 7a. 算路成功 (旧版回调，保留兼容)
    }

    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult result) {
        // 7b. 算路成功 (新版回调)，自动启动 GPS 步行导航
        Log.d(TAG, "步行算路成功，启动导航");
        if (mAMapNavi != null) {
            mAMapNavi.startNavi(NaviType.GPS);
        }
    }

    @Override
    public void onCalculateRouteFailure(AMapCalcRouteResult result) {
        Log.e(TAG, "步行算路失败: " + result.getErrorDetail());
    }

    @Override
    public void onCalculateRouteFailure(int errorInfo) {
        Log.e(TAG, "步行算路失败, errorInfo: " + errorInfo);
    }

    @Override
    public void onNaviInfoUpdate(NaviInfo naviInfo) {
        // 8. 导航过程中持续回调 — 提取转弯事件并回传给树莓派
        if (naviInfo == null || naviEventCallback == null) return;

        int iconType = naviInfo.getIconType();
        int distance = naviInfo.getCurStepRetainDistance();
        String event;

        switch (iconType) {
            case 2:  // 左转
                event = "TURN_LEFT";
                break;
            case 3:  // 右转
                event = "TURN_RIGHT";
                break;
            case 9:  // 左前方转
                event = "TURN_LEFT";
                break;
            case 10: // 右前方转
                event = "TURN_RIGHT";
                break;
            default:
                event = "AHEAD";
                break;
        }
        naviEventCallback.onNaviEvent("NAV_ACTIVE", event, distance);
    }

    @Override
    public void onArriveDestination() {
        // 9. 到达目的地
        Log.d(TAG, "已到达目的地");
        if (mAMapNavi != null) {
            mAMapNavi.stopNavi(); // 停止导航，释放持续的 GPS 定位调用
        }
        if (naviEventCallback != null) {
            naviEventCallback.onArrived();
        }
    }

    // ===================== AMapNaviListener 必须实现的空方法 (SDK 11.x) =====================
    @Override public void onInitNaviFailure() { Log.e(TAG, "导航引擎初始化失败"); }
    @Override public void onInitNaviSuccess() { Log.d(TAG, "导航引擎初始化成功"); }
    @Override public void onStartNavi(int type) { Log.d(TAG, "导航已启动, type=" + type); }
    @Override public void onTrafficStatusUpdate() {}
    @Override public void onLocationChange(AMapNaviLocation location) {}
    @Override public void onGetNavigationText(int type, String text) {}
    @Override public void onGetNavigationText(String s) {}
    @Override public void onEndEmulatorNavi() {}
    @Override public void onReCalculateRouteForYaw() {}
    @Override public void onReCalculateRouteForTrafficJam() {}
    @Override public void onArrivedWayPoint(int wayID) {}
    @Override public void onGpsOpenStatus(boolean enabled) {}
    @Override public void updateCameraInfo(AMapNaviCameraInfo[] infos) {}
    @Override public void onServiceAreaUpdate(AMapServiceAreaInfo[] infos) {}
    @Override public void showCross(AMapNaviCross cross) {}
    @Override public void hideCross() {}
    @Override public void showModeCross(AMapModelCross cross) {}
    @Override public void hideModeCross() {}
    @Override public void showLaneInfo(AMapLaneInfo[] laneInfos, byte[] laneBackgroundInfo, byte[] laneRecommendedInfo) {}
    @Override public void showLaneInfo(AMapLaneInfo laneInfo) {}
    @Override public void hideLaneInfo() {}
    @Override public void notifyParallelRoad(int parallelRoadType) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] infos) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo info) {}
    @Override public void updateAimlessModeStatistics(AimLessModeStat stat) {}
    @Override public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo info) {}
    @Override public void onPlayRing(int type) {}
    @Override public void onNaviRouteNotify(AMapNaviRouteNotifyData data) {}
    @Override public void onGpsSignalWeak(boolean weak) {}
    @Override public void updateIntervalCameraInfo(AMapNaviCameraInfo aMapNaviCameraInfo, AMapNaviCameraInfo aMapNaviCameraInfo1, int i) {}
}