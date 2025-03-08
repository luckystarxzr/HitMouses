package com.muen.hitmouse;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.muen.hitmouse.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SharedPreferences sharedPreferences;
    private Intent musicIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);

        setupListeners();
        setupMusic();
    }

    private void setupListeners() {
        binding.exit.setOnClickListener(v -> finish());
        binding.btnEasy.setOnClickListener(v -> startGame(false));
        binding.btnHard.setOnClickListener(v -> startGame(true));
        binding.btnViewRecords.setOnClickListener(v -> viewRecords());
    }

    private void setupMusic() {
        boolean isMuted = sharedPreferences.getInt("music", 0) == 1;
        musicIntent = new Intent(this, MusicService.class);
        if (!isMuted) startService(musicIntent);
        binding.cbMusic.setChecked(isMuted);

        binding.cbMusic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                stopService(musicIntent);
                saveMusicPreference(1);
            } else {
                startService(musicIntent);
                saveMusicPreference(0);
            }
        });
    }

    private void viewRecords() {
        Intent intent = new Intent(this, RecordActivity.class);
        startActivity(intent);
    }

    private void startGame(boolean isRandomMode) {
        Intent intent = new Intent(this, PlayActivity.class);
        intent.putExtra("isRandomMode", isRandomMode);
        startActivity(intent);
    }

    private void saveMusicPreference(int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("music", value);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(musicIntent);
    }
}