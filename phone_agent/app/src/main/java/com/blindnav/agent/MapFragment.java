package com.blindnav.agent;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.blindnav.agent.navi.GaodeNaviManager;
import com.google.android.material.button.MaterialButton;

public class MapFragment extends Fragment {
    private GaodeNaviManager gaodeNaviManager;
    private MapView mapView;
    private AMap aMap;
    private Marker currentMarker;
    private TextView textLocation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        gaodeNaviManager = new GaodeNaviManager(requireContext());
        mapView = view.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.moveCamera(CameraUpdateFactory.zoomTo(16f));

        textLocation = view.findViewById(R.id.text_location_value);
        MaterialButton buttonCurrentLocation = view.findViewById(R.id.button_current_location);
        MaterialButton buttonMapPlaceholder = view.findViewById(R.id.button_map_placeholder);

        buttonCurrentLocation.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            textLocation.setText(R.string.location_querying);
            gaodeNaviManager.queryCurrentLocation(new GaodeNaviManager.LocationCallback() {
                @Override
                public void onLocationSuccess(GaodeNaviManager.LocationSnapshot locationSnapshot) {
                    requireActivity().runOnUiThread(() -> updateMapLocation(locationSnapshot));
                }

                @Override
                public void onLocationFailure(String reason) {
                    requireActivity().runOnUiThread(() -> textLocation.setText(
                            getString(R.string.location_failed, reason)));
                }
            });
        });

        buttonMapPlaceholder.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Toast.makeText(requireContext(), R.string.toast_map_placeholder, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateMapLocation(GaodeNaviManager.LocationSnapshot locationSnapshot) {
        textLocation.setText(getString(R.string.location_format,
                locationSnapshot.getCity(),
                locationSnapshot.getAddress(),
                locationSnapshot.getLatitude(),
                locationSnapshot.getLongitude()));

        if (aMap == null) {
            return;
        }

        LatLng latLng = new LatLng(locationSnapshot.getLatitude(), locationSnapshot.getLongitude());
        if (currentMarker != null) {
            currentMarker.remove();
        }

        currentMarker = aMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(locationSnapshot.getCity())
                .snippet(locationSnapshot.getAddress())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        if (gaodeNaviManager != null) {
            gaodeNaviManager.destroy();
            gaodeNaviManager = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }
}