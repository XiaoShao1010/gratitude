package com.blindnav.agent;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_HOME = "home";
    private static final String TAG_MAP = "map";
    private static final String TAG_MY = "my";

    private Fragment homeFragment;
    private Fragment mapFragment;
    private Fragment myFragment;
    private TextView textAppTitle;
    private TextView textAppSubtitle;
    private TextView textShellState;
    private TextView textShellLocation;
    private TextView textShellLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textAppTitle = findViewById(R.id.text_app_title);
        textAppSubtitle = findViewById(R.id.text_app_subtitle);
        textShellState = findViewById(R.id.text_shell_state);
        textShellLocation = findViewById(R.id.text_shell_location);
        textShellLogin = findViewById(R.id.text_shell_login);

        textAppTitle.setText(R.string.app_header_title);
        textAppSubtitle.setText(R.string.app_header_subtitle);

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            mapFragment = new MapFragment();
            myFragment = new MyFragment();

            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .add(R.id.fragment_container, mapFragment, TAG_MAP)
                    .hide(mapFragment)
                    .add(R.id.fragment_container, myFragment, TAG_MY)
                    .hide(myFragment)
                    .commitNow();
        } else {
            homeFragment = fragmentManager.findFragmentByTag(TAG_HOME);
            mapFragment = fragmentManager.findFragmentByTag(TAG_MAP);
            myFragment = fragmentManager.findFragmentByTag(TAG_MY);
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                updateShellHeader(R.string.shell_subtitle_home, R.string.shell_state_online, R.string.shell_state_location_ready, R.string.shell_state_profile);
                switchFragment(homeFragment);
                return true;
            }
            if (item.getItemId() == R.id.nav_map) {
                updateShellHeader(R.string.shell_subtitle_map, R.string.shell_state_online, R.string.shell_state_location_ready, R.string.shell_state_profile);
                switchFragment(mapFragment);
                return true;
            }
            if (item.getItemId() == R.id.nav_my) {
                updateShellHeader(R.string.shell_subtitle_my, R.string.shell_state_online, R.string.shell_state_location_ready, R.string.shell_state_profile);
                switchFragment(myFragment);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    private void switchFragment(Fragment targetFragment) {
        if (homeFragment == null || mapFragment == null || myFragment == null || targetFragment == null) {
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .hide(homeFragment)
                .hide(mapFragment)
                .hide(myFragment)
                .show(targetFragment)
                .commit();
    }

    private void updateShellHeader(int subtitleResId, int stateResId, int locationResId, int loginResId) {
        textAppTitle.setText(R.string.app_header_title);
        textAppSubtitle.setText(subtitleResId);
        textShellState.setText(stateResId);
        textShellLocation.setText(locationResId);
        textShellLogin.setText(loginResId);
    }
}
