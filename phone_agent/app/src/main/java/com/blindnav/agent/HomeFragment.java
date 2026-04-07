package com.blindnav.agent;

import android.os.Bundle;
import android.content.Intent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.blindnav.agent.navi.GaodeNaviManager;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {
    private boolean isNavigating = false;
    private GaodeNaviManager gaodeNaviManager;
    private TextView textStatus;
    private TextView textGuidance;
    private MaterialButton buttonPrimary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        gaodeNaviManager = new GaodeNaviManager(requireContext());

        textStatus = view.findViewById(R.id.text_status_value);
        textGuidance = view.findViewById(R.id.text_guidance_value);
        buttonPrimary = view.findViewById(R.id.button_primary);
        MaterialButton buttonReconnect = view.findViewById(R.id.button_reconnect);
        MaterialButton buttonVoiceTest = view.findViewById(R.id.button_voice_test);
        MaterialButton buttonNaviTest = view.findViewById(R.id.button_navi_test);

        buttonPrimary.setOnClickListener(v -> {
            isNavigating = !isNavigating;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            updatePrimaryState();
            int messageRes = isNavigating ? R.string.toast_navigation_started : R.string.toast_navigation_stopped;
            Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show();
        });

        buttonReconnect.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Toast.makeText(requireContext(), getString(R.string.toast_reconnect_placeholder), Toast.LENGTH_SHORT).show();
        });

        buttonVoiceTest.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Toast.makeText(requireContext(), getString(R.string.toast_voice_test_placeholder), Toast.LENGTH_SHORT).show();
        });

        buttonNaviTest.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startActivity(new Intent(requireContext(), NaviTestActivity.class));
        });

        updatePrimaryState();
    }

    @Override
    public void onDestroyView() {
        if (gaodeNaviManager != null) {
            gaodeNaviManager.destroy();
            gaodeNaviManager = null;
        }
        super.onDestroyView();
    }

    private void updatePrimaryState() {
        if (isNavigating) {
            textStatus.setText(R.string.state_navigating);
            textGuidance.setText(R.string.guidance_running);
            buttonPrimary.setText(R.string.action_stop_navigation);
        } else {
            textStatus.setText(R.string.state_ready);
            textGuidance.setText(R.string.guidance_ready);
            buttonPrimary.setText(R.string.action_start_navigation);
        }
    }
}