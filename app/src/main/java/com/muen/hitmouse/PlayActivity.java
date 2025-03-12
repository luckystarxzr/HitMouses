package com.muen.hitmouse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.muen.hitmouse.databinding.LayoutPlayEasyBinding;
import com.muen.hitmouse.databinding.LayoutPlayHardBinding;

import java.util.ArrayList;
import java.util.List;

public class PlayActivity extends AppCompatActivity {
    private static final int MSG_UPDATE_UI = 0x101;
    private static final int MSG_GAME_OVER = 0x102;
    private static final String TAG = "PlayActivity";
    private static final long VIBRATION_DURATION = 50;
    private static final long HIT_COOLDOWN = 100; // 击中冷却时间，防止过快连击
    private static final int MAX_COMBO = 5; // 最大连击次数限制

    private LayoutPlayEasyBinding easyBinding;
    private LayoutPlayHardBinding hardBinding;
    private SharedPreferences sharedPreferences;
    private ExoPlayer exoplayer;
    private SoundPool soundPool;
    private int kickSoundId;
    private GameEngine gameEngine;
    private boolean isPaused = false;
    private boolean isRandomMode;
    private boolean isMuted = false;
    private Vibrator vibrator;
    private long lastHitTime = 0; // 上次击中时间，用于冷却判断
    private int comboCount = 0; // 当前连击次数
    private TextView comboText;
    private List<ImageView> mouseViews;

    // Handler 用于处理 UI 更新和游戏结束消息
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_UI:
                    if (!isPaused) {
                        gameEngine.updateUI(msg.arg1); // 更新地鼠位置等 UI
                        if (!isMuted && exoplayer != null && !exoplayer.isPlaying()) {
                            exoplayer.play(); // 确保背景音乐在未静音时播放
                        }
                    }
                    break;
                case MSG_GAME_OVER:
                    Log.d(TAG, "Received MSG_GAME_OVER, showing game over dialog");
                    gameOver(); // 处理游戏结束逻辑
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);
        isRandomMode = getIntent().getBooleanExtra("isRandomMode", false); // 从 Intent 获取模式
        isMuted = sharedPreferences.getBoolean("isMuted", false); // 从 SharedPreferences 获取静音设置

        // 根据模式加载不同的布局
        if (!isRandomMode) {
            easyBinding = LayoutPlayEasyBinding.inflate(getLayoutInflater());
            setContentView(easyBinding.getRoot());
        } else {
            hardBinding = LayoutPlayHardBinding.inflate(getLayoutInflater());
            setContentView(hardBinding.getRoot());
        }

        initAudioPlayers(); // 初始化音频播放器
        setupTouchListenersWithObserver(); // 设置触摸监听器
        setupButtons(); // 设置按钮监听器

        // 初始化地鼠洞数组，仅在简单模式下使用
        View[] holes;
        if (!isRandomMode) {
            holes = new View[] {
                    easyBinding.hole1, easyBinding.hole2, easyBinding.hole3,
                    easyBinding.hole4, easyBinding.hole5, easyBinding.hole6,
                    easyBinding.hole7, easyBinding.hole8, easyBinding.hole9
            };
        } else {
            holes = new View[0]; // 困难模式下无固定洞
        }

        mouseViews = new ArrayList<>();
        ImageView initialMouse = !isRandomMode ? easyBinding.mouse : hardBinding.mouse;
        mouseViews.add(initialMouse); // 添加初始地鼠视图

        // 初始化 GameEngine，传入必要的视图和参数
        gameEngine = new GameEngine(
                this,
                handler,
                holes,
                mouseViews,
                !isRandomMode ? easyBinding.boom : hardBinding.boom,
                !isRandomMode ? easyBinding.hunter : hardBinding.hunter,
                !isRandomMode ? easyBinding.time : hardBinding.time,
                !isRandomMode ? easyBinding.scoreText : hardBinding.scoreText,
                isRandomMode
        );

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        Toast.makeText(this, isRandomMode ? "困难模式开始！" : "简单模式开始！", Toast.LENGTH_SHORT).show();
        gameEngine.startGame(); // 启动游戏
    }

    // 初始化音频播放器，包括 SoundPool 和 ExoPlayer
    private void initAudioPlayers() {
        soundPool = new SoundPool.Builder().setMaxStreams(2).build();
        kickSoundId = soundPool.load(this, R.raw.kick, 1); // 加载击中音效

        exoplayer = new ExoPlayer.Builder(this).build();
        MediaItem mediaItem = MediaItem.fromUri("android.resource://" + getPackageName() + "/" + R.raw.start);
        exoplayer.setMediaItem(mediaItem);
        exoplayer.prepare();

        if (isMuted) {
            if (exoplayer.isPlaying()) {
                exoplayer.pause();
            }
            stopMusicService();
        } else {
            exoplayer.play();
            startMusicService();
        }
    }

    private void setupTouchListenersWithObserver() {
        if (!isRandomMode) {
            setupTouchListener(easyBinding.easyLayout, easyBinding.hunter);
        } else {
            setupTouchListener(hardBinding.hardLayout, hardBinding.hunter);
        }
    }

    // 设置触摸监听器，确保布局加载完成后再绑定
    private void setupTouchListener(View layout, ImageView hunter) {
        layout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                layout.setOnTouchListener((v, event) -> {
                    handleTouchEvent(event, hunter); // 处理触摸事件
                    return true;
                });
            }
        });
    }

    // 处理触摸事件，控制猎人移动和击中地鼠
    private void handleTouchEvent(MotionEvent event, ImageView hunter) {
        long currentTime = System.currentTimeMillis();
        float x = Math.max(0, Math.min(event.getX() - hunter.getWidth() / 2f, ((View) hunter.getParent()).getWidth() - hunter.getWidth()));
        float y = Math.max(0, Math.min(event.getY() - hunter.getHeight() / 2f, ((View) hunter.getParent()).getHeight() - hunter.getHeight()));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                hunter.setX(x); // 更新猎人位置
                hunter.setY(y);
                // 检查是否可以击中（未暂停、冷却时间已过、连击未达上限）
                if (!isPaused && currentTime - lastHitTime >= HIT_COOLDOWN && comboCount < MAX_COMBO) {
                    ImageView hitMouse = gameEngine.hitMouse(hunter);
                    if (hitMouse != null) {
                        if (!isMuted) {
                            playKickSound(); // 播放击中音效
                        }
                        comboCount++;
                        showScoreAnimation(hitMouse); // 显示得分动画
                        showComboCounter(hitMouse); // 显示连击计数
                        vibrate(); // 震动反馈
                        lastHitTime = currentTime;
                        Log.d(TAG, "击中成功，连击次数: " + comboCount);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                comboCount = 0; // 重置连击
                removeComboCounter();
                Log.d(TAG, "连击重置");
                break;
        }
    }

    private void playKickSound() {
        if (soundPool != null) {
            soundPool.play(kickSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private void setupButtons() {
        if (!isRandomMode) {
            setupButtonListeners(easyBinding.btnPause, easyBinding.btnBack, easyBinding.btnMute);
        } else {
            setupButtonListeners(hardBinding.btnPause, hardBinding.btnBack, hardBinding.btnMute);
        }
    }

    private void setupButtonListeners(Button pauseButton, Button backButton, Button muteButton) {
        pauseButton.setOnClickListener(v -> togglePause());
        backButton.setOnClickListener(v -> onBackPressed());
        muteButton.setOnClickListener(v -> toggleMusic());
        muteButton.setText(isMuted ? "开启音乐" : "关闭音乐");
    }

    private void togglePause() {
        if (!isPaused) {
            gameEngine.stopGame();
            handler.removeCallbacksAndMessages(null); // 移除所有回调，避免残余任务
            getPauseButton().setText("继续");
        } else {
            gameEngine.resumeGame();
            getPauseButton().setText("暂停");
        }
        isPaused = !isPaused;
        Log.d(TAG, "Game paused: " + isPaused);
    }

    private Button getPauseButton() {
        return !isRandomMode ? easyBinding.btnPause : hardBinding.btnPause;
    }

    private void toggleMusic() {
        isMuted = !isMuted;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isMuted", isMuted);
        editor.apply();

        if (isMuted) {
            if (exoplayer != null && exoplayer.isPlaying()) {
                exoplayer.pause();
            }
            stopMusicService();
        } else {
            if (exoplayer != null && !exoplayer.isPlaying()) {
                exoplayer.play();
            }
            startMusicService();
        }

        getMuteButton().setText(isMuted ? "开启音乐" : "关闭音乐");
    }

    private Button getMuteButton() {
        return !isRandomMode ? easyBinding.btnMute : hardBinding.btnMute;
    }

    // 处理游戏结束逻辑，显示得分对话框
    private void gameOver() {
        gameEngine.endGame(); // 结束游戏引擎
        int count = gameEngine.getCount();
        long timestamp = System.currentTimeMillis();

        Log.d(TAG, "Game over, score: " + count + ", time: " + timestamp);

        saveRecord(timestamp, count); // 保存游戏记录

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView;
        try {
            dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_over, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inflate game over dialog: " + e.getMessage());
            finish(); // 如果对话框加载失败，直接结束活动
            return;
        }
        builder.setView(dialogView);

        CardView cardView = (CardView) dialogView;
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        Button btnRestart = dialogView.findViewById(R.id.btn_restart);
        Button btnBack = dialogView.findViewById(R.id.btn_back);

        // 检查对话框视图是否正确加载
        if (tvTitle == null || tvMessage == null || btnRestart == null || btnBack == null) {
            Log.e(TAG, "Game over dialog views not found");
            finish();
            return;
        }

        int highScore = sharedPreferences.getInt("high_score", 0);
        String rating = count >= 166 ? "地鼠大师" : count >= 99 ? "地鼠猎手" : "地鼠新手";
        if (count > highScore) {
            sharedPreferences.edit().putInt("high_score", count).apply();
            tvTitle.setText("新纪录！");
            tvMessage.setText("得分: " + count + "\n评级: " + rating + "\n打破最高分: " + highScore);
        } else {
            tvTitle.setText("游戏结束");
            tvMessage.setText("得分: " + count + "\n评级: " + rating + "\n最高分: " + highScore);
        }

        if (count >= 30) {
            cardView.setCardBackgroundColor(0xFFFFD700); // 金色
        } else if (count >= 15) {
            cardView.setCardBackgroundColor(0xFFC0C0C0); // 银色
        } else {
            cardView.setCardBackgroundColor(0xFFFFFFFF); // 白色
        }
        tvTitle.setTextColor(0xFF333333);
        tvMessage.setTextColor(0xFF666666);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        btnRestart.setOnClickListener(v -> {
            Log.d(TAG, "Restart clicked");
            dialog.dismiss();
            recreate(); // 重启活动
        });

        btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Back clicked");
            dialog.dismiss();
            finish(); // 返回主页面
        });

        try {
            dialog.show();
            Log.d(TAG, "Game over dialog shown");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show game over dialog: " + e.getMessage());
            finish();
        }
    }

    private void saveRecord(long timestamp, int score) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (int i = 49; i > 0; i--) {
            int prevScore = sharedPreferences.getInt("record_score_" + (i - 1), 0);
            long prevTime = sharedPreferences.getLong("record_time_" + (i - 1), 0);
            if (prevScore > 0) {
                editor.putInt("record_score_" + i, prevScore);
                editor.putLong("record_time_" + i, prevTime);
            }
        }
        editor.putInt("record_score_0", score);
        editor.putLong("record_time_0", timestamp);
        editor.apply();
    }

    @Override
    public void onBackPressed() {
        gameEngine.stopGame();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_game, null);
        builder.setView(dialogView);

        CardView cardView = (CardView) dialogView;
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        tvTitle.setText("退出游戏");
        tvMessage.setText("您确定要退出吗？");

        cardView.setCardBackgroundColor(0xFFFFFFFF);
        cardView.setRadius(16f);
        cardView.setCardElevation(8f);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        btnCancel.setOnClickListener(v -> {
            Log.d(TAG, "Cancel button clicked");
            gameEngine.resumeGame();
            isPaused = false;
            dialog.dismiss();
            getPauseButton().setText("暂停");
        });

        btnConfirm.setOnClickListener(v -> {
            Log.d(TAG, "Confirm button clicked");
            gameEngine.endGame();
            finish();
        });

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.gravity = android.view.Gravity.CENTER;
        dialog.getWindow().setAttributes(params);

        dialog.show();
    }

    // 释放资源，防止内存泄漏
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            Log.d(TAG, "SoundPool 释放成功");
        }
        if (exoplayer != null) {
            exoplayer.stop();
            exoplayer.release();
            exoplayer = null;
            Log.d(TAG, "ExoPlayer 释放成功");
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null); // 清理 Handler 任务
        }
        stopMusicService();
        gameEngine.endGame();
        removeComboCounter();
        Log.d(TAG, "Activity destroyed");
    }

    private void startMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
    }

    private void stopMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        stopService(intent);
    }

    private void showScoreAnimation(ImageView target) {
        TextView scoreText = new TextView(this);
        scoreText.setText(comboCount >= 3 ? "+" + comboCount + " 双倍!" : "+" + comboCount + " 连击!");
        scoreText.setTextColor(0xFFFF0000);
        scoreText.setTextSize(comboCount >= 3 ? 24 : 20);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.leftMargin = (int) target.getX() + target.getWidth() / 2;
        params.topMargin = (int) target.getY() - 50;
        ((ConstraintLayout) target.getParent()).addView(scoreText, params);

        scoreText.animate()
                .translationYBy(-50f)
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> {
                    if (scoreText.getParent() != null) {
                        ((ConstraintLayout) scoreText.getParent()).removeView(scoreText);
                    }
                })
                .start();
    }

    private void showComboCounter(ImageView target) {
        ConstraintLayout parentLayout = (ConstraintLayout) target.getParent();

        if (comboText == null) {
            comboText = new TextView(this);
            comboText.setTextColor(0xFFFF0000);
            comboText.setTextSize(24);
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params.leftMargin = (int) target.getX() + target.getWidth() + 10;
            params.topMargin = (int) target.getY() + target.getHeight() / 2;
            parentLayout.addView(comboText, params);
        }

        comboText.setText("连击 x" + comboCount);
    }

    private void removeComboCounter() {
        if (comboText != null && comboText.getParent() != null) {
            ((ConstraintLayout) comboText.getParent()).removeView(comboText);
            comboText = null;
        }
    }

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATION_DURATION);
            }
        }
    }

    // 添加新的地鼠视图，同步保护避免多线程问题
    public void addMouseView() {
        synchronized (mouseViews) {
            if (mouseViews.size() < GameEngine.MAX_MOUSE_COUNT) {
                ConstraintLayout layout = (ConstraintLayout) mouseViews.get(0).getParent();
                ImageView newMouse = new ImageView(this);
                newMouse.setImageResource(R.drawable.mouse);
                newMouse.setLayoutParams(new ConstraintLayout.LayoutParams(
                        mouseViews.get(0).getWidth(), mouseViews.get(0).getHeight()));
                newMouse.setVisibility(View.INVISIBLE);
                layout.addView(newMouse);
                mouseViews.add(newMouse);
                Log.d(TAG, "Added new mouse view, Total mice: " + mouseViews.size());
            } else {
                Log.d(TAG, "Max mouse count reached: " + GameEngine.MAX_MOUSE_COUNT);
            }
        }
    }
}