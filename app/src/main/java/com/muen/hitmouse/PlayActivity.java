package com.muen.hitmouse;

import android.content.Context;
import android.content.DialogInterface;
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

public class PlayActivity extends AppCompatActivity {
    private static final int MSG_UPDATE_UI = 0x101;
    private static final int MSG_GAME_OVER = 0x102;
    private static final String TAG = "PlayActivity";
    private static final long VIBRATION_DURATION = 50; // 震动持续时间（毫秒）

    private Object binding;
    private SharedPreferences sharedPreferences;
    private ExoPlayer exoplayer; // 替换 MediaPlayer
    private SoundPool soundPool;
    private int kickSoundId;
    private GameEngine gameEngine;
    private boolean isPaused = false;
    private boolean isRandomMode;
    private boolean isMuted = false;
    private Vibrator vibrator; // 震动服务

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_UI:
                    if (!isPaused) {
                        gameEngine.updateUI(msg.arg1);
                        if (!isMuted && exoplayer != null && exoplayer.isPlaying()) {
                            exoplayer.play();
                        }
                    }
                    break;
                case MSG_GAME_OVER:
                    gameOver();
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
        isRandomMode = getIntent().getBooleanExtra("isRandomMode", false);
        isMuted = sharedPreferences.getBoolean("isMuted", false);

        if (!isRandomMode) {
            binding = LayoutPlayEasyBinding.inflate(getLayoutInflater());
            setContentView(((LayoutPlayEasyBinding) binding).getRoot());
        } else {
            binding = LayoutPlayHardBinding.inflate(getLayoutInflater());
            setContentView(((LayoutPlayHardBinding) binding).getRoot());
        }

        initAudioPlayers();
        setupTouchListenersWithObserver();
        setupButtons();

        View[] holes;
        if (!isRandomMode) {
            LayoutPlayEasyBinding easyBinding = (LayoutPlayEasyBinding) binding;
            holes = new View[] {
                    easyBinding.hole1, easyBinding.hole2, easyBinding.hole3,
                    easyBinding.hole4, easyBinding.hole5, easyBinding.hole6,
                    easyBinding.hole7, easyBinding.hole8, easyBinding.hole9
            };
        } else {
            holes = new View[0];
        }

        gameEngine = new GameEngine(
                this,
                handler,
                holes,
                !isRandomMode ? ((LayoutPlayEasyBinding) binding).mouse : ((LayoutPlayHardBinding) binding).mouse,
                !isRandomMode ? ((LayoutPlayEasyBinding) binding).boom : ((LayoutPlayHardBinding) binding).boom,
                !isRandomMode ? ((LayoutPlayEasyBinding) binding).hunter : ((LayoutPlayHardBinding) binding).hunter,
                !isRandomMode ? ((LayoutPlayEasyBinding) binding).time : ((LayoutPlayHardBinding) binding).time,
                !isRandomMode ? ((LayoutPlayEasyBinding) binding).scoreText : ((LayoutPlayHardBinding) binding).scoreText,
                isRandomMode
        );

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); // 初始化震动服务

        Toast.makeText(this, isRandomMode ? "困难模式开始！" : "简单模式开始！", Toast.LENGTH_SHORT).show();
        gameEngine.startGame();
    }

    private void initAudioPlayers() {
        soundPool = new SoundPool.Builder().setMaxStreams(2).build();
        kickSoundId = soundPool.load(this, R.raw.kick, 1);

        // 初始化 ExoPlayer
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
            LayoutPlayEasyBinding easyBinding = (LayoutPlayEasyBinding) binding;
            setupTouchListener(easyBinding.easyLayout, easyBinding.hunter, easyBinding.mouse);
        } else {
            LayoutPlayHardBinding hardBinding = (LayoutPlayHardBinding) binding;
            setupTouchListener(hardBinding.hardLayout, hardBinding.hunter, hardBinding.mouse);
        }
    }

    private void setupTouchListener(View layout, ImageView hunter, ImageView mouse) {
        layout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                layout.setOnTouchListener((v, event) -> {
                    handleTouchEvent(event, hunter, mouse);
                    return true;
                });
            }
        });
    }

    private void handleTouchEvent(MotionEvent event, ImageView hunter, ImageView mouse) {
        Log.d(TAG, "Hunter class: " + hunter.getClass().getName() + ", Width: " + hunter.getWidth());
        Log.d(TAG, "Mouse class: " + mouse.getClass().getName() + ", Width: " + mouse.getWidth());

        float x = Math.max(0, Math.min(event.getX() - hunter.getWidth() / 2f, ((View) hunter.getParent()).getWidth() - hunter.getWidth()));
        float y = Math.max(0, Math.min(event.getY() - hunter.getHeight() / 2f, ((View) hunter.getParent()).getHeight() - hunter.getHeight()));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                hunter.setX(x);
                hunter.setY(y);
                if (!isPaused) {
                    if (!isMuted) {
                        playKickSound();
                    }
                    gameEngine.hitMouse();
                    showScoreAnimation(mouse); // 使用现有的 showScoreAnimation
                    vibrate(); // 震动反馈
                }
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
            LayoutPlayEasyBinding easyBinding = (LayoutPlayEasyBinding) binding;
            setupButtonListeners(easyBinding.btnPause, easyBinding.btnBack, easyBinding.btnMute);
        } else {
            LayoutPlayHardBinding hardBinding = (LayoutPlayHardBinding) binding;
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
            handler.removeCallbacksAndMessages(null);
            getPauseButton().setText("继续");
        } else {
            gameEngine.startGame();
            getPauseButton().setText("暂停");
        }
        isPaused = !isPaused;
    }

    private Button getPauseButton() {
        if (!isRandomMode) {
            return ((LayoutPlayEasyBinding) binding).btnPause;
        } else {
            return ((LayoutPlayHardBinding) binding).btnPause;
        }
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
        if (!isRandomMode) {
            return ((LayoutPlayEasyBinding) binding).btnMute;
        } else {
            return ((LayoutPlayHardBinding) binding).btnMute;
        }
    }

    private void gameOver() {
        gameEngine.stopGame();
        int count = gameEngine.getCount();
        long timestamp = System.currentTimeMillis();

        saveRecord(timestamp, count);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_over, null);
        builder.setView(dialogView);

        CardView cardView = (CardView) dialogView;
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        Button btnRestart = dialogView.findViewById(R.id.btn_restart);
        Button btnBack = dialogView.findViewById(R.id.btn_back);

        tvTitle.setText("游戏结束");
        tvMessage.setText("您一共打了 " + count + " 只地鼠");

        if (count >= 20) {
            cardView.setCardBackgroundColor(0xFFFFD700);
            tvTitle.setTextColor(0xFF333333);
            tvMessage.setTextColor(0xFF666666);
        } else if (count >= 10) {
            cardView.setCardBackgroundColor(0xFFFF00);
            tvTitle.setTextColor(0xFF333333);
            tvMessage.setTextColor(0xFF666666);
        } else {
            cardView.setCardBackgroundColor(0xFFFFFFFF);
            tvTitle.setTextColor(0xFF333333);
            tvMessage.setTextColor(0xFF666666);
        }

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        btnRestart.setOnClickListener(v -> {
            dialog.dismiss();
            recreate();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
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
        new AlertDialog.Builder(this)
                .setTitle("结束游戏")
                .setMessage("您确定要退出吗？")
                .setNeutralButton("否", null)
                .setNegativeButton("是", (dialog, which) -> {
                    gameEngine.stopGame();
                    finish();
                })
                .show();
    }

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
        stopMusicService();
        gameEngine.stopGame();
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
        scoreText.setText("+1");
        scoreText.setTextColor(0xFFFF0000); // 红色文字
        scoreText.setTextSize(20);
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
                .withEndAction(() -> ((ConstraintLayout) scoreText.getParent()).removeView(scoreText))
                .start();
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
}