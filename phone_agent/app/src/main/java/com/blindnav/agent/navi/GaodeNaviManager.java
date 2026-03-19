package com.blindnav.agent.navi;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.amap.api.navi.AmapNaviCallback;
import com.amap.api.navi.AmapNaviListener;
import com.amap.api.navi.NaviSetting;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviInfo;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviPath;
import com.amap.api.navi.model.AMapNaviStep;
import com.amap.api.navi.model.AimLessModeScan;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.StartPoint;
import com.amap.api.navi.model.WalkRouteCalculateResult;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.autonavi.tbt.TruckInfo;

import java.util.List;

public class GaodeNaviManager implements AmapNaviListener {
    private static final String TAG = "GaodeNavi";
    private Context context;
    private NaviCallback callback;
    private GeocodeSearch geocodeSearch;
    private double currentLat = 30.2741;
    private double currentLon = 120.1633;

    public interface NaviCallback {
        void onNaviStatus(String status, String event, int distance);
        void onArrived();
    }

    public GaodeNaviManager(Context ctx) {
        this.context = ctx;
        this.geocodeSearch = new GeocodeSearch(ctx);
    }

    public void geocodeAndNavigate(String address, NaviCallback cb) {
        this.callback = cb;
        try {
            GeocodeSearch.GeocodeSearchListener listener = new GeocodeSearch.GeocodeSearchListener() {
                @Override
                public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result, int code) {
                    if (code == AMapException.CODE_OK && result.getGeocodeAddressList().size() > 0) {
                        double lat = result.getGeocodeAddressList().get(0).getLatLonPoint().getLatitude();
                        double lon = result.getGeocodeAddressList().get(0).getLatLonPoint().getLongitude();
                        calculateWalkRoute(lon, lat);
                    }
                }
                @Override
                public void onRegeocodeSearched(RegeocodeResult result, int code) {}
            };
            geocodeSearch.setOnGeocodeSearchListener(listener);
            geocodeSearch.getFromLocationNameAsyn(new GeocodeSearch.GeocodeQuery(address, ""));
        } catch (Exception e) {
            Log.e(TAG, "Geocode failed", e);
        }
    }

    private void calculateWalkRoute(double endLon, double endLat) {
        new android.os.Handler(Looper.getMainLooper()).post(() -> {
            try {
                com.amap.api.navi.AmapNaviWalk amapNaviWalk =
                    com.amap.api.navi.AmapNaviWalk.getInstance(context);
                amapNaviWalk.setAmapNaviListener(this);
                StartPoint start = new StartPoint(currentLon, currentLat);
                com.amap.api.navi.model.NaviLatLng end = new com.amap.api.navi.model.NaviLatLng(endLat, endLon);
                amapNaviWalk.calculateWalkRoute(start, end);
            } catch (Exception e) {
                Log.e(TAG, "Route calc failed", e);
            }
        });
    }

    @Override
    public void onInitNaviFailure() {}

    @Override
    public void onInitNaviSuccess() {}

    @Override
    public void onStartNavi(int type) {}

    @Override
    public void onNaviInfoUpdate(NaviInfo naviInfo) {
        if (callback == null) return;
        int iconType = naviInfo.getIconType();
        int distance = (int) naviInfo.getCurStepRetainDistance();
        String event;
        switch (iconType) {
            case 0: event = "AHEAD"; break;
            case 1: event = "TURN_LEFT"; break;
            case 2: event = "TURN_RIGHT"; break;
            case 3: event = "AHEAD"; break;
            default: event = "AHEAD"; break;
        }
        callback.onNaviStatus("NAV_ACTIVE", event, distance);
    }

    @Override
    public void onInfoNotification(int notificationType, int detail) {}

    @Override
    public void onCalculateRouteFailure(int errorInfo) {}

    @Override
    public void onCalculateRouteSuccess(WalkRouteCalculateResult result) {
        if (callback != null && result.getPaths().size() > 0) {
            callback.onNaviStatus("NAV_ACTIVE", "AHEAD", 100);
        }
    }

    @Override
    public void onRecalculateRouteForYaw() {}

    @Override
    public void onRecalculateRouteForAmapPage() {}

    @Override
    public void onArrived() {
        if (callback != null) callback.onArrived();
    }

    @Override
    public void onNaviEnd() {}

    @Override
    public void onNaviTurnSuccess() {}

    @Override
    public void onNaviCrossGenerated() {}

    @Override
    public void onNaviSdkNofifyError() {}

    @Override
    public void showCross() {}

    @Override
    public void hideCross() {}

    @Override
    public void showModeCross() {}

    @Override
    public void hideModeCross() {}

    @Override
    public void updateCameraInfo() {}

    @Override
    public void updateIntervalCameraInfo() {}

    @Override
    public void showLaneInfo() {}

    @Override
    public void hideLaneInfo() {}

    @Override
    public void showLaneInfoSuccess() {}

    @Override
    public void showLaneInfoFail() {}

    @Override
    public void onCalculateRouteSuccess(int[] routeIds) {}

    @Override
    public void onNaviPageNative() {}

    @Override
    public void onNaviInfoInit() {}

    @Override
    public void updateAimlessModeState(AimLessModeScan scan) {}

    @Override
    public void onServiceAreaUpdate(AimLessModeScan scan) {}

    @Override
    public void showReminderInfo() {}

    @Override
    public void onCalculateRouteFailure(AMapCalcRouteResult result) {}

    @Override
    public void onNaviLocationChange(AMapNaviLocation location) {}

    @Override
    public void onTbtRoutePlaned() {}

    @Override
    public void showBroadcastMessage() {}

    @Override
    public void notifyTbtRoutePlaned() {}

    @Override
    public void onNaviSetting() {}

    @Override
    public void onNaviCancel() {}

    @Override
    public boolean isNaviSettingListenerOpen() {
        return false;
    }

    @Override
    public void showZoomControl() {}

    @Override
    public void hideZoomControl() {}

    @Override
    public void startFloatView() {}

    @Override
    public void stopFloatView() {}

    @Override
    public void showEndPoint() {}

    @Override
    public void hideEndPoint() {}

    @Override
    public void showAlternativeList() {}

    @Override
    public void hudModeSwitch() {}

    @Override
    public void onTBTClientInit() {}

    @Override
    public void onTBTClientUnInit() {}

    @Override
    public void retrieveNaviPolicyInfo() {}

    @Override
    public void notifyTruckInfoUpdate() {}

    @Override
    public void onTruckInfoReqSuccess() {}

    @Override
    public void onTruckInfoReqFail() {}

    @Override
    public void onNaviRoutePlaned() {}
}
