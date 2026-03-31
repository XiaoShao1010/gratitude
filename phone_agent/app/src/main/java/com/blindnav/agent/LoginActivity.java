package com.blindnav.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {
    private UserSessionManager sessionManager;
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new UserSessionManager(this);
        if (sessionManager.isLoggedIn()) {
            openHome();
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        usernameLayout = findViewById(R.id.layout_username);
        passwordLayout = findViewById(R.id.layout_password);
        usernameInput = findViewById(R.id.input_username);
        passwordInput = findViewById(R.id.input_password);
        MaterialButton loginButton = findViewById(R.id.button_login);
        MaterialButton guestButton = findViewById(R.id.button_guest);

        loginButton.setOnClickListener(v -> performLogin(v));
        guestButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            sessionManager.saveLogin("guest", getString(R.string.login_guest_display_name), "guest-token");
            Toast.makeText(this, R.string.toast_guest_login_success, Toast.LENGTH_SHORT).show();
            openHome();
            finish();
        });
    }

    private void performLogin(android.view.View view) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        String username = usernameInput.getText() == null ? "" : usernameInput.getText().toString().trim();
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString().trim();

        usernameLayout.setError(null);
        passwordLayout.setError(null);

        if (username.isEmpty()) {
            usernameLayout.setError(getString(R.string.login_username_required));
            return;
        }

        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.login_password_required));
            return;
        }

        sessionManager.saveLogin(username, username, "local-token:" + username);
        Toast.makeText(this, getString(R.string.toast_login_success, username), Toast.LENGTH_SHORT).show();
        openHome();
        finish();
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
    }
}