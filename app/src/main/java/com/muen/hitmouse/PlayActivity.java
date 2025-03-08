package com.muen.hitmouse;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.muen.hitmouse.databinding.LayoutPlayEasyBinding;
import com.muen.hitmouse.databinding.LayoutPlayHardBinding;

public class PlayActivity extends AppCompatActivity {
    private static final int MSG_UPDATE_UI = 0x101;
    private static final int MSG_GAME_OVER = 0x102;
    private static final String TAG = "PlayActivity";

    private Object binding;
    private SharedPreferences sharedPreferences;
    private MediaPlayer mediaPlayerStart;
    private SoundPool soundPool;
    private int kickSoundId;
    private GameEngine gameEngine;
    private boolean isPaused = false;
    private boolean isRandomMode;
    private boolean isMuted = false;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_UI:
                    if (!isPaused) {
                        gameEngine.updateUI(msg.arg1);
                        if (!isMuted && mediaPlayerStart != null && !mediaPlayerStart.isPlaying()) {
                            mediaPlayerStart.start();
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

        initMediaPlayers();
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

        // 显示模式提示
        Toast.makeText(this, isRandomMode ? "困难模式开始！" : "简单模式开始！", Toast.LENGTH_SHORT).show();
        gameEngine.startGame();
    }

    private void initMediaPlayers() {
        soundPool = new SoundPool.Builder().setMaxStreams(2).build();
        kickSoundId = soundPool.load(this, R.raw.kick, 1);
        mediaPlayerStart = MediaPlayer.create(this, R.raw.start);
        if (isMuted) {
            if (mediaPlayerStart != null && mediaPlayerStart.isPlaying()) {
                mediaPlayerStart.pause();
            }
            stopMusicService(); // 静音时停止背景音乐
        } else {
            startMusicService(); // 非静音时启动背景音乐
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
                    if (isHunterOnTarget(hunter, mouse)) {
                        gameEngine.hitMouse();
                    }
                }
                break;
        }
    }

    private void playKickSound() {
        if (soundPool != null) {
            soundPool.play(kickSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private boolean isHunterOnTarget(ImageView hunter, ImageView target) {
        if (target.getVisibility() != View.VISIBLE) return false;

        float targetCenterX = target.getX() + target.getWidth() / 2f;
        float targetCenterY = target.getY() + target.getHeight() / 2f;
        float hunterX = hunter.getX() + hunter.getWidth() / 2f;
        float hunterY = hunter.getY() + hunter.getHeight() / 2f;

        float tolerance = 60f;
        return Math.abs(hunterX - targetCenterX) < tolerance &&
                Math.abs(hunterY - targetCenterY) < tolerance;
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
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePause();
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        muteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMusic();
            }
        });
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
            if (mediaPlayerStart != null && mediaPlayerStart.isPlaying()) {
                mediaPlayerStart.pause();
            }
            stopMusicService(); // 停止背景音乐
        } else {
            if (mediaPlayerStart != null && !mediaPlayerStart.isPlaying()) {
                mediaPlayerStart.start();
            }
            startMusicService(); // 启动背景音乐
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

        // 记录比赛分数和时间戳
        saveRecord(timestamp, count);

        new AlertDialog.Builder(this)
                .setTitle("游戏结束")
                .setMessage("您一共打了 " + count + " 只地鼠")
                .setNegativeButton("再来一局", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        recreate();
                    }
                })
                .setNeutralButton("返回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void saveRecord(long timestamp, int score) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // 移动现有记录，插入新记录到开头
        for (int i = 49; i > 0; i--) {
            int prevScore = sharedPreferences.getInt("record_score_" + (i - 1), 0);
            long prevTime = sharedPreferences.getLong("record_time_" + (i - 1), 0);
            if (prevScore > 0) {
                editor.putInt("record_score_" + i, prevScore);
                editor.putLong("record_time_" + i, prevTime);
            }
        }
        // 插入新记录
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
                .setNegativeButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        gameEngine.stopGame();
                        finish();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        releaseMediaPlayer(mediaPlayerStart);
        stopMusicService(); // 销毁时停止服务
        gameEngine.stopGame();
    }

    private void releaseMediaPlayer(MediaPlayer player) {
        if (player != null) {
            if (player.isPlaying()) player.stop();
            player.release();
            player = null;
        }
    }

    private void startMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
    }

    private void stopMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        stopService(intent);
    }
}