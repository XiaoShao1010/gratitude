package com.blindnav.agent.navi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;

import android.content.Context;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeAddress;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener;
import com.amap.api.services.geocoder.GeocodeSearch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public class GaodeNaviManagerTest {

    private AMapLocationClient mockLocationClient;
    private GeocodeSearch mockGeocodeSearch;
    private AMapNavi mockAMapNavi;
    private AMapLocationListener capturedLocationListener;
    private OnGeocodeSearchListener capturedGeocodeListener;
    private GaodeNaviManager.LocationCallback successCallback;
    private GaodeNaviManager.LocationCallback failureCallback;
    private GaodeNaviManager.NaviEventCallback mockNaviEventCallback;
    private GaodeNaviManager manager;

    private static java.lang.reflect.Field getField(Class<?> cls, Object target, String name) throws Exception {
        java.lang.reflect.Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Before
    public void setUp() throws Exception {
        mockLocationClient = mock(AMapLocationClient.class);
        mockGeocodeSearch = mock(GeocodeSearch.class);
        mockAMapNavi = mock(AMapNavi.class);

        doAnswer(invocation -> {
            capturedLocationListener = invocation.getArgument(0);
            return null;
        }).when(mockLocationClient).setLocationListener(any());

        doNothing().when(mockLocationClient).startLocation();

        doAnswer(invocation -> {
            capturedGeocodeListener = invocation.getArgument(0);
            return null;
        }).when(mockGeocodeSearch).setOnGeocodeSearchListener(any());

        Context context = Robolectric.buildActivity(android.app.Activity.class).create().get();
        manager = new GaodeNaviManager(context, mockAMapNavi, mockGeocodeSearch, mockLocationClient);
        manager.initSearchEngine();
        manager.initNaviEngine();
        manager.initLocationEngine();

        successCallback = mock(GaodeNaviManager.LocationCallback.class);
        failureCallback = mock(GaodeNaviManager.LocationCallback.class);
        mockNaviEventCallback = mock(GaodeNaviManager.NaviEventCallback.class);
        manager.setNaviEventCallback(mockNaviEventCallback);
    }

    @Test
    public void queryCurrentLocation_success_invokesCallbackWithSnapshot() {
        manager.queryCurrentLocation(successCallback);
        assertThat(capturedLocationListener).isNotNull();

        AMapLocation mockLoc = mock(AMapLocation.class);
        when(mockLoc.getErrorCode()).thenReturn(0);
        when(mockLoc.getLatitude()).thenReturn(30.274152);
        when(mockLoc.getLongitude()).thenReturn(120.154479);
        when(mockLoc.getAddress()).thenReturn("浙江省杭州市西湖区文三路");
        when(mockLoc.getCity()).thenReturn("杭州市");
        when(mockLoc.getProvider()).thenReturn("gps");
        when(mockLoc.getLocationDetail()).thenReturn("detail");

        capturedLocationListener.onLocationChanged(mockLoc);

        verify(successCallback).onLocationSuccess(any(GaodeNaviManager.LocationSnapshot.class));
    }

    @Test
    public void queryCurrentLocation_errorCodeNonZero_invokesFailureCallback() {
        manager.queryCurrentLocation(failureCallback);
        assertThat(capturedLocationListener).isNotNull();

        AMapLocation mockLoc = mock(AMapLocation.class);
        when(mockLoc.getErrorCode()).thenReturn(12);
        when(mockLoc.getErrorInfo()).thenReturn("GPS定位失败");

        capturedLocationListener.onLocationChanged(mockLoc);

        verify(failureCallback).onLocationFailure("定位失败: 12 / GPS定位失败");
    }

    @Test
    public void queryCurrentLocation_nullAMapLocation_invokesFailureWithNullMessage() {
        manager.queryCurrentLocation(failureCallback);
        capturedLocationListener.onLocationChanged(null);

        verify(failureCallback).onLocationFailure("定位返回为空");
    }

    @Test
    public void queryCurrentLocation_nullCallback_doesNotCrash() {
        manager.queryCurrentLocation(null);
    }

    @Test
    public void queryCurrentLocation_nullLocationClient_invokesFailureCallback() {
        Context context = Robolectric.buildActivity(android.app.Activity.class).create().get();
        GaodeNaviManager managerNoLoc = new GaodeNaviManager(context, mockAMapNavi, mockGeocodeSearch, null);

        managerNoLoc.queryCurrentLocation(failureCallback);

        verify(failureCallback).onLocationFailure("定位引擎未初始化");
    }

    @Test
    public void onLocationChanged_nullCallback_doesNotCrash() {
        manager.queryCurrentLocation(null);

        AMapLocation mockLoc = mock(AMapLocation.class);
        when(mockLoc.getErrorCode()).thenReturn(0);

        capturedLocationListener.onLocationChanged(mockLoc);
    }

    @Test
    public void stopLocationQuery_clearsPendingCallback() throws Exception {
        manager.queryCurrentLocation(successCallback);
        assertThat(capturedLocationListener).isNotNull();

        manager.stopLocationQuery();

        verify(mockLocationClient).stopLocation();
        java.lang.reflect.Field f = getField(GaodeNaviManager.class, manager, "pendingLocationCallback");
        assertThat(f.get(manager)).isNull();
    }

    @Test
    public void setNaviEventCallback_storesCallback() {
        GaodeNaviManager.NaviEventCallback cb = mock(GaodeNaviManager.NaviEventCallback.class);
        manager.setNaviEventCallback(cb);

        assertThat(manager.getNaviEventCallback()).isSameAs(cb);
    }

    @Test
    public void onNaviInfoUpdate_TURN_LEFT_invokesCallbackWithTurnLeft() {
        NaviInfo mockInfo = mock(NaviInfo.class);
        when(mockInfo.getIconType()).thenReturn(2);
        when(mockInfo.getCurStepRetainDistance()).thenReturn(15);

        manager.onNaviInfoUpdate(mockInfo);

        verify(mockNaviEventCallback).onNaviEvent("NAV_ACTIVE", "TURN_LEFT", 15);
    }

    @Test
    public void onNaviInfoUpdate_TURN_RIGHT_invokesCallbackWithTurnRight() {
        NaviInfo mockInfo = mock(NaviInfo.class);
        when(mockInfo.getIconType()).thenReturn(3);
        when(mockInfo.getCurStepRetainDistance()).thenReturn(20);

        manager.onNaviInfoUpdate(mockInfo);

        verify(mockNaviEventCallback).onNaviEvent("NAV_ACTIVE", "TURN_RIGHT", 20);
    }

    @Test
    public void onNaviInfoUpdate_iconType9_invokesCallbackWithTurnLeft() {
        NaviInfo mockInfo = mock(NaviInfo.class);
        when(mockInfo.getIconType()).thenReturn(9);
        when(mockInfo.getCurStepRetainDistance()).thenReturn(10);

        manager.onNaviInfoUpdate(mockInfo);

        verify(mockNaviEventCallback).onNaviEvent("NAV_ACTIVE", "TURN_LEFT", 10);
    }

    @Test
    public void onNaviInfoUpdate_iconType10_invokesCallbackWithTurnRight() {
        NaviInfo mockInfo = mock(NaviInfo.class);
        when(mockInfo.getIconType()).thenReturn(10);
        when(mockInfo.getCurStepRetainDistance()).thenReturn(5);

        manager.onNaviInfoUpdate(mockInfo);

        verify(mockNaviEventCallback).onNaviEvent("NAV_ACTIVE", "TURN_RIGHT", 5);
    }

    @Test
    public void onNaviInfoUpdate_defaultIcon_invokesCallbackWithAhead() {
        NaviInfo mockInfo = mock(NaviInfo.class);
        when(mockInfo.getIconType()).thenReturn(99);
        when(mockInfo.getCurStepRetainDistance()).thenReturn(30);

        manager.onNaviInfoUpdate(mockInfo);

        verify(mockNaviEventCallback).onNaviEvent("NAV_ACTIVE", "AHEAD", 30);
    }

    @Test
    public void onNaviInfoUpdate_nullInfo_doesNotCrash() {
        manager.onNaviInfoUpdate(null);
    }

    @Test
    public void onNaviInfoUpdate_nullCallback_doesNotCrash() {
        manager.setNaviEventCallback(null);

        NaviInfo mockInfo = mock(NaviInfo.class);
        when(mockInfo.getIconType()).thenReturn(2);
        when(mockInfo.getCurStepRetainDistance()).thenReturn(15);

        manager.onNaviInfoUpdate(mockInfo);
    }

    @Test
    public void onArriveDestination_invokesArrivedCallback() {
        manager.onArriveDestination();

        verify(mockNaviEventCallback).onArrived();
    }

    @Test
    public void onArriveDestination_stopsNavi() {
        manager.onArriveDestination();

        verify(mockAMapNavi).stopNavi();
    }

    @Test
    public void onCalculateRouteSuccess_startsGpsNavi() {
        AMapCalcRouteResult mockResult = mock(AMapCalcRouteResult.class);

        manager.onCalculateRouteSuccess(mockResult);

        verify(mockAMapNavi).startNavi(NaviType.GPS);
    }

    @Test
    public void geocodeSuccess_triggersWalkRouteCalculation() {
        assertThat(capturedGeocodeListener).isNotNull();

        GeocodeResult mockResult = mock(GeocodeResult.class);
        GeocodeAddress mockAddress = mock(GeocodeAddress.class);
        LatLonPoint point = mock(LatLonPoint.class);
        when(point.getLatitude()).thenReturn(30.274152);
        when(point.getLongitude()).thenReturn(120.154479);
        when(mockAddress.getLatLonPoint()).thenReturn(point);
        when(mockResult.getGeocodeAddressList()).thenReturn(java.util.Collections.singletonList(mockAddress));

        capturedGeocodeListener.onGeocodeSearched(mockResult, AMapException.CODE_AMAP_SUCCESS);

        verify(mockAMapNavi).calculateWalkRoute(any());
    }

    @Test
    public void geocodeFailure_doesNotTriggerRouteCalculation() {
        assertThat(capturedGeocodeListener).isNotNull();

        GeocodeResult mockResult = mock(GeocodeResult.class);
        capturedGeocodeListener.onGeocodeSearched(mockResult, 999);

        verify(mockAMapNavi, never()).calculateWalkRoute(any());
    }

    @Test
    public void onCalculateRouteFailure_doesNotStartNavi() {
        AMapCalcRouteResult mockResult = mock(AMapCalcRouteResult.class);
        when(mockResult.getErrorDetail()).thenReturn("算路失败");

        manager.onCalculateRouteFailure(mockResult);

        verify(mockAMapNavi, never()).startNavi(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void destroy_cleansUpAllResources() {
        manager.destroy();

        verify(mockLocationClient).stopLocation();
        verify(mockLocationClient).onDestroy();
        verify(mockAMapNavi).removeAMapNaviListener(manager);
    }

    @Test
    public void destroy_canBeCalledMultipleTimes() {
        manager.destroy();
        manager.destroy();
    }
}
