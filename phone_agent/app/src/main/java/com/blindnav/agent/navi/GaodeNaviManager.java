package com.blindnav.agent.navi;

import android.content.Context;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.navi.AMapNavi;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeResult;

/**
 * 宏观导航代理引擎 (基于高德新版 SDK 9.8.0)
 * 负责接收树莓派指令，搜索坐标，并在后台静默算路播报
 */
public class GaodeNaviManager {
    private static final String TAG = "GaodeNaviManager";
    private Context context;
    private AMapNavi mAMapNavi;
    private GeocodeSearch geocodeSearch;
    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;
    private LocationCallback pendingLocationCallback;

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

        public boolean isSuccess() {
            return success;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public String getAddress() {
            return address;
        }

        public String getCity() {
            return city;
        }

        public String getProvider() {
            return provider;
        }

        public String getDetail() {
            return detail;
        }
    }

    public GaodeNaviManager(Context context) {
        this.context = context.getApplicationContext();
        initSearchEngine();
        initNaviEngine();
        initLocationEngine();
    }

    // 1. 初始化地理编码引擎 (文字地名 -> 经纬度)
    private void initSearchEngine() {
        try {
            geocodeSearch = new GeocodeSearch(context);
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

                            // TODO: 拿到坐标后，调用步行算路函数
                            // calculateWalkRoute(lat, lon);
                        }
                    } else {
                        Log.e(TAG, "地址解析失败，错误码: " + rCode);
                    }
                }
            });
        } catch (AMapException e) {
            e.printStackTrace();
        }
    }

    // 2. 初始化导航引擎
    private void initNaviEngine() {
        try {
            mAMapNavi = AMapNavi.getInstance(context);
            // 默认开启内置的语音播报
            mAMapNavi.setUseInnerVoice(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 3. 初始化定位引擎 (实时查询当前位置)
    private void initLocationEngine() {
        try {
            locationClient = new AMapLocationClient(context);
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
        geocodeSearch.getFromLocationNameAsyn(query);
    }

    // 销毁时释放资源，防止内存泄漏
    public void destroy() {
        stopLocationQuery();
        if (locationClient != null) {
            locationClient.onDestroy();
            locationClient = null;
        }
        if (mAMapNavi != null) {
            mAMapNavi.destroy();
        }
    }
}