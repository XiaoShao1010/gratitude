package com.blindnav.agent.navi;

import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class LocationSnapshotTest {

    @Test
    public void success_factory_createsSnapshotWithCorrectFields() {
        GaodeNaviManager.LocationSnapshot snap = GaodeNaviManager.LocationSnapshot.success(
                30.274152, 120.154479,
                "浙江省杭州市西湖区文三路",
                "杭州市",
                "gps",
                "detail");

        assertThat(snap.isSuccess()).isTrue();
        assertThat(snap.getLatitude()).isEqualTo(30.274152);
        assertThat(snap.getLongitude()).isEqualTo(120.154479);
        assertThat(snap.getAddress()).isEqualTo("浙江省杭州市西湖区文三路");
        assertThat(snap.getCity()).isEqualTo("杭州市");
        assertThat(snap.getProvider()).isEqualTo("gps");
        assertThat(snap.getDetail()).isEqualTo("detail");
    }

    @Test
    public void success_factory_allFieldsAreRetrievable() {
        GaodeNaviManager.LocationSnapshot snap = GaodeNaviManager.LocationSnapshot.success(
                1.0, 2.0, "addr", "city", "provider", "detail");

        assertThat(snap.getLatitude()).isEqualTo(1.0);
        assertThat(snap.getLongitude()).isEqualTo(2.0);
        assertThat(snap.getAddress()).isEqualTo("addr");
        assertThat(snap.getCity()).isEqualTo("city");
        assertThat(snap.getProvider()).isEqualTo("provider");
        assertThat(snap.getDetail()).isEqualTo("detail");
    }

    @Test
    public void failure_factory_createsSnapshotWithZeroCoordsAndEmptyStrings() {
        GaodeNaviManager.LocationSnapshot snap = GaodeNaviManager.LocationSnapshot.failure("GPS定位失败");

        assertThat(snap.isSuccess()).isFalse();
        assertThat(snap.getLatitude()).isZero();
        assertThat(snap.getLongitude()).isZero();
        assertThat(snap.getAddress()).isEmpty();
        assertThat(snap.getCity()).isEmpty();
        assertThat(snap.getProvider()).isEmpty();
        assertThat(snap.getDetail()).isEqualTo("GPS定位失败");
    }

    @Test
    public void failure_factory_acceptsAnyErrorMessage() {
        GaodeNaviManager.LocationSnapshot snap = GaodeNaviManager.LocationSnapshot.failure("错误码: 12 / GPS定位失败");

        assertThat(snap.isSuccess()).isFalse();
        assertThat(snap.getDetail()).isEqualTo("错误码: 12 / GPS定位失败");
    }

    @Test
    public void isSuccess_returnsTrueOnlyOnSuccessFactory() {
        GaodeNaviManager.LocationSnapshot successSnap = GaodeNaviManager.LocationSnapshot.success(1, 2, "a", "b", "c", "d");
        GaodeNaviManager.LocationSnapshot failureSnap = GaodeNaviManager.LocationSnapshot.failure("err");

        assertThat(successSnap.isSuccess()).isTrue();
        assertThat(failureSnap.isSuccess()).isFalse();
    }
}
