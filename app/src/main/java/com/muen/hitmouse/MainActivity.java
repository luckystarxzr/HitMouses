package com.muen.hitmouse;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.muen.hitmouse.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding; // 用于绑定布局文件中的视图
    private SharedPreferences sharedPreferences; // 用于存储用户偏好设置（如音乐开关状态）
    private Intent musicIntent; // 用于控制背景音乐服务的 Intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置全屏显示，隐藏状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 使用 ViewBinding 初始化布局
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化 SharedPreferences，用于读取和保存用户设置
        sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);

        // 设置按钮点击事件监听器
        setupListeners();
        // 初始化背景音乐相关设置
        setupMusic();
    }

    // 设置各个按钮的点击事件监听器
    private void setupListeners() {
        // 退出按钮：点击后关闭当前 Activity
        binding.exit.setOnClickListener(v -> finish());
        // 简单模式按钮：启动游戏并传递非随机模式参数
        binding.btnEasy.setOnClickListener(v -> startGame(false));
        // 困难模式按钮：启动游戏并传递随机模式参数
        binding.btnHard.setOnClickListener(v -> startGame(true));
        // 查看记录按钮：跳转到记录查看页面
        binding.btnViewRecords.setOnClickListener(v -> viewRecords());
    }

    // 初始化背景音乐设置
    private void setupMusic() {
        // 从 SharedPreferences 读取音乐开关状态，1 表示静音，0 表示播放
        boolean isMuted = sharedPreferences.getInt("music", 0) == 1;
        // 创建用于控制背景音乐服务的 Intent
        musicIntent = new Intent(this, MusicService.class);
        // 如果未静音，则启动音乐服务
        if (!isMuted) startService(musicIntent);
        // 设置音乐开关复选框的初始状态
        binding.cbMusic.setChecked(isMuted);

        // 为音乐开关复选框设置监听器，根据用户选择控制音乐播放
        binding.cbMusic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 用户勾选静音，停止音乐服务并保存设置
                stopService(musicIntent);
                saveMusicPreference(1);
            } else {
                // 用户取消静音，启动音乐服务并保存设置
                startService(musicIntent);
                saveMusicPreference(0);
            }
        });
    }

    // 跳转到记录查看页面
    private void viewRecords() {
        Intent intent = new Intent(this, RecordActivity.class);
        startActivity(intent);
    }

    // 启动游戏页面并传递游戏模式参数
    private void startGame(boolean isRandomMode) {
        Intent intent = new Intent(this, PlayActivity.class);
        // 传递是否为随机模式的参数给游戏页面
        intent.putExtra("isRandomMode", isRandomMode);
        startActivity(intent);
    }

    // 保存音乐偏好设置到 SharedPreferences
    private void saveMusicPreference(int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // 保存音乐开关状态，1 表示静音，0 表示播放
        editor.putInt("music", value);
        editor.apply(); // 异步保存数据
    }

    // Activity 销毁时停止背景音乐服务
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(musicIntent); // 确保退出时停止音乐服务
    }
}