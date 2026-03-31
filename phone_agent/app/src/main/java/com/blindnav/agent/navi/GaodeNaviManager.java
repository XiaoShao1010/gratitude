package com.blindnav.agent.navi;

import android.content.Context;
import android.util.Log;

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

    public GaodeNaviManager(Context context) {
        this.context = context.getApplicationContext();
        initSearchEngine();
        initNaviEngine();
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

    // 3. 供外部 (Socket 接收器) 调用的核心公开方法
    public void startNavigationTo(String destinationName, String cityName) {
        Log.d(TAG, "收到树莓派指令，准备导航至: " + destinationName);
        // 构造搜索条件并发出异步搜索请求
        GeocodeQuery query = new GeocodeQuery(destinationName, cityName);
        geocodeSearch.getFromLocationNameAsyn(query);
    }

    // 销毁时释放资源，防止内存泄漏
    public void destroy() {
        if (mAMapNavi != null) {
            mAMapNavi.destroy();
        }
    }
}