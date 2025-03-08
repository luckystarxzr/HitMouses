package com.muen.hitmouse;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameEngine {
    private static final int INITIAL_PLAY_TIME = 60000;
    private static final int TIME_TICK = 1000;
    private static final int MSG_UPDATE_UI = 0x101;
    private static final int MSG_GAME_OVER = 0x102;

    private final Context context;
    private final Handler handler;
    private final View[] holes;
    private final ImageView mouse;
    private final ImageView boom;
    private final ImageView hunter;
    private final TextView timeView;
    private final TextView scoreText;
    private final boolean isRandomMode;

    private int playTime = INITIAL_PLAY_TIME;
    private int count = 0;
    private volatile boolean isRunning = true;
    private volatile int currentHoleIndex = -1;
    private final AtomicBoolean isScheduled = new AtomicBoolean(false);

    public GameEngine(Context context, Handler handler, View[] holes, ImageView mouse, ImageView boom, ImageView hunter,
                      TextView timeView, TextView scoreText, boolean isRandomMode) {
        this.context = context;
        this.handler = handler;
        this.holes = holes;
        this.mouse = mouse;
        this.boom = boom;
        this.hunter = hunter;
        this.timeView = timeView;
        this.scoreText = scoreText;
        this.isRandomMode = isRandomMode;
    }

    public void startGame() {
        isRunning = true;
        playTime = INITIAL_PLAY_TIME;
        count = 0;
        currentHoleIndex = -1;
        scoreText.setText("得分: 0");
        timeView.setText(String.format("剩余时间: %d秒", playTime / 1000));
        handler.post(mouseRunnable);
        handler.post(timeRunnable);
    }

    public synchronized void hitMouse() {
        if (!isRunning) return;

        if (isRandomMode) {
            if (isHunterOnMouse(hunter, mouse)) {
                count++;
                mouse.setVisibility(View.INVISIBLE);
                boom.setX(mouse.getX() - 25f);
                boom.setY(mouse.getY() - 25f);
                boom.setVisibility(View.VISIBLE);
                showScoreAnimation(mouse);
                handler.postDelayed(() -> boom.setVisibility(View.INVISIBLE), 300);
                scoreText.setText(String.format("得分: %d", count));
            }
        } else {
            if (currentHoleIndex != -1 && holes.length > 0) {
                View currentHole = holes[currentHoleIndex];
                if (isHunterInHole(hunter, currentHole)) {
                    count++;
                    mouse.setVisibility(View.INVISIBLE);
                    boom.setX(mouse.getX() - 25f);
                    boom.setY(mouse.getY() - 25f);
                    boom.setVisibility(View.VISIBLE);
                    showScoreAnimation(mouse);
                    handler.postDelayed(() -> boom.setVisibility(View.INVISIBLE), 300);
                    scoreText.setText(String.format("得分: %d", count));
                }
            }
        }
    }

    private void showScoreAnimation(ImageView target) {
        TextView scoreAnim = new TextView(context);
        scoreAnim.setText("+1");
        scoreAnim.setTextColor(Color.WHITE);
        scoreAnim.setTextSize(20);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.startToStart = LayoutParams.PARENT_ID;
        params.topToTop = LayoutParams.PARENT_ID;
        params.setMargins((int) target.getX(), (int) target.getY(), 0, 0);
        ((ConstraintLayout) target.getParent()).addView(scoreAnim, params);

        scoreAnim.animate()
                .translationYBy(-50f)
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> ((ConstraintLayout) target.getParent()).removeView(scoreAnim))
                .start();
    }

    public void stopGame() {
        isRunning = false;
        handler.removeCallbacks(mouseRunnable);
        handler.removeCallbacks(timeRunnable);
        mouse.setVisibility(View.INVISIBLE);
        boom.setVisibility(View.INVISIBLE);
    }

    public synchronized int getCount() {
        return count;
    }

    public boolean isGameOver() {
        return playTime <= 0 || !isRunning;
    }

    private final Runnable mouseRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && playTime > 0) {
                int index = new Random().nextInt(isRandomMode ? 100 : holes.length);
                handler.sendMessage(handler.obtainMessage(MSG_UPDATE_UI, index, 0));
                handler.postDelayed(this, getSleepTime());
            }
        }
    };

    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && playTime > 0) {
                playTime -= TIME_TICK;
                timeView.setText(String.format("剩余时间: %d秒", playTime / 1000));
                if (playTime <= 0) {
                    handler.sendEmptyMessage(MSG_GAME_OVER);
                } else {
                    handler.postDelayed(this, TIME_TICK);
                }
            }
        }
    };

    private long getSleepTime() {
        if (playTime > 40000) return 1500;
        if (playTime > 20000) return 1000;
        return 800;
    }

    public void updateUI(int index) {
        if (!isRunning) return;

        if (isRandomMode) {
            mouse.post(() -> {
                int maxX = mouse.getRootView().getWidth() - mouse.getWidth();
                int maxY = mouse.getRootView().getHeight() - mouse.getHeight();
                if (maxX > 0 && maxY > 0) {
                    mouse.setX(new Random().nextInt(maxX));
                    mouse.setY(new Random().nextInt(maxY));
                }
            });
        } else {
            currentHoleIndex = index % holes.length;
            View currentHole = holes[currentHoleIndex];
            mouse.setX(currentHole.getX() + (currentHole.getWidth() - mouse.getWidth()) / 2f);
            mouse.setY(currentHole.getY() + (currentHole.getHeight() - mouse.getHeight()) / 2f);
        }
        boom.setVisibility(View.INVISIBLE);
        mouse.setVisibility(View.VISIBLE);
    }

    private boolean isHunterInHole(ImageView hunter, View hole) {
        float holeCenterX = hole.getX() + hole.getWidth() / 2f;
        float holeCenterY = hole.getY() + hole.getHeight() / 2f;
        float hunterX = hunter.getX() + hunter.getWidth() / 2f;
        float hunterY = hunter.getY() + hunter.getHeight() / 2f;

        float tolerance = 60f;
        return Math.abs(hunterX - holeCenterX) < tolerance &&
                Math.abs(hunterY - holeCenterY) < tolerance;
    }

    private boolean isHunterOnMouse(ImageView hunter, ImageView mouse) {
        if (mouse.getVisibility() != View.VISIBLE) return false;

        float mouseCenterX = mouse.getX() + mouse.getWidth() / 2f;
        float mouseCenterY = mouse.getY() + mouse.getHeight() / 2f;
        float hunterX = hunter.getX() + hunter.getWidth() / 2f;
        float hunterY = hunter.getY() + hunter.getHeight() / 2f;

        float tolerance = 60f;
        return Math.abs(hunterX - mouseCenterX) < tolerance &&
                Math.abs(hunterY - mouseCenterY) < tolerance;
    }
}