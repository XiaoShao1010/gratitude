package com.blindnav.agent;

import android.content.Intent;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class MyFragment extends Fragment {
    private UserSessionManager sessionManager;
    private TextView textUserValue;
    private TextView textTokenValue;
    private TextView textSectionTitle;
    private TextView textSectionBody;
    private MaterialButton buttonLogin;
    private MaterialButton buttonLogout;
    private MaterialButton buttonAccount;
    private MaterialButton buttonDevice;
    private MaterialButton buttonData;
    private MaterialButton buttonSectionAction;
    private MaterialCardView cardInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new UserSessionManager(requireContext());

        textUserValue = view.findViewById(R.id.text_user_value);
        textTokenValue = view.findViewById(R.id.text_token_value);
        textSectionTitle = view.findViewById(R.id.text_section_title);
        textSectionBody = view.findViewById(R.id.text_section_body);
        buttonLogin = view.findViewById(R.id.button_login);
        buttonLogout = view.findViewById(R.id.button_logout);
        buttonAccount = view.findViewById(R.id.button_account);
        buttonDevice = view.findViewById(R.id.button_device);
        buttonData = view.findViewById(R.id.button_data);
        buttonSectionAction = view.findViewById(R.id.button_section_action);
        cardInfo = view.findViewById(R.id.card_info);

        buttonLogin.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startActivity(new Intent(requireContext(), LoginActivity.class));
        });

        buttonLogout.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            sessionManager.logout();
            Toast.makeText(requireContext(), R.string.toast_logout_success, Toast.LENGTH_SHORT).show();
            refreshAccountState();
        });

        buttonSectionAction.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Toast.makeText(requireContext(), textSectionBody.getText(), Toast.LENGTH_SHORT).show();
        });

        buttonAccount.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showSection(
                    R.string.profile_section_account_title,
                    R.string.profile_section_account_body,
                    R.string.profile_action_account_placeholder);
        });

        buttonDevice.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showSection(
                    R.string.profile_section_device_title,
                    R.string.profile_section_device_body,
                    R.string.action_device_binding);
        });

        buttonData.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showSection(
                    R.string.profile_section_data_title,
                    R.string.profile_section_data_body,
                    R.string.action_profile_preferences);
        });

        refreshAccountState();
        showSection(
                R.string.profile_section_account_title,
                R.string.profile_section_account_body,
            R.string.profile_action_account_placeholder);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sessionManager != null) {
            refreshAccountState();
        }
    }

    private void refreshAccountState() {
        boolean loggedIn = sessionManager.isLoggedIn();
        if (loggedIn) {
            textUserValue.setText(getString(R.string.home_user_format, sessionManager.getDisplayName()));
            textTokenValue.setText(getString(R.string.profile_logged_in_hint));
            buttonLogin.setVisibility(View.GONE);
            buttonLogout.setVisibility(View.VISIBLE);
        } else {
            textUserValue.setText(R.string.user_unknown);
            textTokenValue.setText(R.string.profile_logged_out_hint);
            buttonLogin.setVisibility(View.VISIBLE);
            buttonLogout.setVisibility(View.GONE);
        }
    }

    private void showSection(int titleResId, int bodyResId, int actionResId) {
        textSectionTitle.setText(titleResId);
        textSectionBody.setText(bodyResId);
        buttonSectionAction.setText(actionResId);
        cardInfo.setVisibility(View.VISIBLE);
    }
}