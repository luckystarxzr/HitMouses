package com.muen.hitmouse;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameEngine {
    private static final int INITIAL_PLAY_TIME = 60000;
    private static final int TIME_TICK = 1000;
    private static final int MSG_UPDATE_UI = 0x101;
    private static final int MSG_GAME_OVER = 0x102;
    private static final int MULTI_MOUSE_START_TIME = 10000; 
    private static final int MOUSE_INCREASE_INTERVAL = 10000;
    private static final int MAX_MOUSE_COUNT = 5; // 地鼠数量上限

    private final Context context;
    private final Handler handler;
    private final View[] holes;
    private final List<ImageView> mice;
    private final ImageView boom;
    private final ImageView hunter;
    private final TextView timeView;
    private final TextView scoreText;
    private final boolean isRandomMode;
    private final PlayActivity activity;

    private int playTime = INITIAL_PLAY_TIME;
    private int count = 0;
    private volatile boolean isRunning = true;
    private volatile boolean isPaused = false;
    private volatile int currentHoleIndex = -1;
    private final AtomicBoolean isScheduled = new AtomicBoolean(false);
    private int comboCount = 0;
    private int mouseCount = 1;

    public GameEngine(Context context, Handler handler, View[] holes, List<ImageView> mice, ImageView boom, ImageView hunter,
                      TextView timeView, TextView scoreText, boolean isRandomMode) {
        this.context = context;
        this.handler = handler;
        this.holes = holes;
        this.mice = mice;
        this.boom = boom;
        this.hunter = hunter;
        this.timeView = timeView;
        this.scoreText = scoreText;
        this.isRandomMode = isRandomMode;
        this.activity = (PlayActivity) context;
    }

    public void startGame() {
        isRunning = true;
        isPaused = false;
        playTime = INITIAL_PLAY_TIME;
        count = 0;
        comboCount = 0;
        mouseCount = 1;
        currentHoleIndex = -1;
        scoreText.setText("得分: 0");
        timeView.setText(String.format("剩余时间: %d秒", playTime / 1000));
        handler.post(mouseRunnable);
        handler.post(timeRunnable);
    }

    public void resumeGame() {
        if (!isRunning || !isPaused) return;
        isPaused = false;
        handler.post(mouseRunnable);
        handler.post(timeRunnable);
    }

    public void stopGame() {
        isPaused = true;
        handler.removeCallbacks(mouseRunnable);
        handler.removeCallbacks(timeRunnable);
        for (ImageView mouse : mice) {
            mouse.setVisibility(View.INVISIBLE);
        }
        boom.setVisibility(View.INVISIBLE);
    }

    public void endGame() {
        isRunning = false;
        isPaused = false;
        handler.removeCallbacks(mouseRunnable);
        handler.removeCallbacks(timeRunnable);
        for (ImageView mouse : mice) {
            mouse.setVisibility(View.INVISIBLE);
        }
        boom.setVisibility(View.INVISIBLE);
    }

    public synchronized ImageView hitMouse(ImageView hunter) {
        if (!isRunning || isPaused) return null;

        ImageView hitMouse = null;
        if (isRandomMode) {
            for (ImageView mouse : mice) {
                if (isHunterOnMouse(hunter, mouse)) {
                    hitMouse = mouse;
                    break;
                }
            }
        } else {
            for (int i = 0; i < mouseCount && i < mice.size(); i++) {
                View currentHole = holes[currentHoleIndices.get(i)];
                if (isHunterInHole(hunter, currentHole)) {
                    hitMouse = mice.get(i);
                    break;
                }
            }
        }

        if (hitMouse != null) {
            comboCount++;
            int score = (comboCount >= 3) ? 2 : 1;
            count += score;
            hitMouse.setVisibility(View.INVISIBLE);
            boom.setX(hitMouse.getX() - 25f);
            boom.setY(hitMouse.getY() - 25f);
            boom.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> boom.setVisibility(View.INVISIBLE), 300);
            scoreText.setText(String.format("得分: %d", count));
        }
        return hitMouse;
    }

    public int getLastScore() {
        return (comboCount >= 3) ? 2 : 1;
    }

    private void showScoreAnimation(ImageView target) {
        TextView scoreAnim = new TextView(context);
        scoreAnim.setText("+1");
        scoreAnim.setTextColor(Color.WHITE);
        scoreAnim.setTextSize(20);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.startToStart = LayoutParams.PARENT_ID;
        params.topToTop = LayoutParams.PARENT_ID;
        params.setMargins((int) target.getX() + target.getWidth() / 2, (int) target.getY() - 50, 0, 0);
        ((ConstraintLayout) target.getParent()).addView(scoreAnim, params);

        scoreAnim.animate()
                .translationYBy(-50f)
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> ((ConstraintLayout) target.getParent()).removeView(scoreAnim))
                .start();
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
            if (isRunning && !isPaused && playTime > 0) {
                int index = new Random().nextInt(isRandomMode ? 100 : holes.length);
                handler.sendMessage(handler.obtainMessage(MSG_UPDATE_UI, index, 0));
                handler.postDelayed(this, getSleepTime());
            }
        }
    };

    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isPaused && playTime > 0) {
                playTime -= TIME_TICK;
                timeView.setText(String.format("剩余时间: %d秒", playTime / 1000));

                int elapsedTime = INITIAL_PLAY_TIME - playTime;
                Log.d("GameEngine", "Elapsed Time: " + elapsedTime + ", Mouse Count: " + mouseCount + ", Mice Size: " + mice.size());
                if (elapsedTime >= MULTI_MOUSE_START_TIME) {
                    int expectedMouseCount = Math.min(MAX_MOUSE_COUNT, 1 + (elapsedTime - MULTI_MOUSE_START_TIME) / MOUSE_INCREASE_INTERVAL);
                    Log.d("GameEngine", "Expected Mouse Count: " + expectedMouseCount);
                    if (mouseCount < expectedMouseCount) {
                        while (mice.size() < expectedMouseCount) {
                            activity.addMouseView();
                        }
                        mouseCount = expectedMouseCount;
                        Log.d("GameEngine", "Updated Mouse Count to: " + mouseCount + ", Mice Size: " + mice.size());
                    }
                }

                if (playTime <= 0) {
                    handler.sendEmptyMessage(MSG_GAME_OVER);
                } else {
                    handler.postDelayed(this, TIME_TICK);
                }
            }
        }
    };

    private long getSleepTime() {
        long baseTime = playTime > 40000 ? 1500 : playTime > 20000 ? 1000 : 800;
        return Math.max(500, baseTime - (mouseCount * 100) - (count / 10));
    }

    private List<Integer> currentHoleIndices = new ArrayList<>();

    public void updateUI(int index) {
        if (!isRunning || isPaused) return;

        Log.d("GameEngine", "Updating UI, Mouse Count: " + mouseCount + ", Mice Size: " + mice.size());
        if (isRandomMode) {
            for (int i = 0; i < mouseCount && i < mice.size(); i++) {
                ImageView mouse = mice.get(i);
                int finalI = i;
                mouse.post(() -> {
                    int maxX = mouse.getRootView().getWidth() - mouse.getWidth();
                    int maxY = mouse.getRootView().getHeight() - mouse.getHeight();
                    if (maxX > 0 && maxY > 0) {
                        mouse.setX(new Random().nextInt(maxX));
                        mouse.setY(new Random().nextInt(maxY));
                        mouse.setVisibility(View.VISIBLE);
                        Log.d("GameEngine", "Mouse " + finalI + " set visible at (" + mouse.getX() + ", " + mouse.getY() + ")");
                    }
                });
            }
        } else {
            currentHoleIndices.clear();
            List<Integer> usedIndices = new ArrayList<>();
            for (int i = 0; i < mouseCount && i < mice.size(); i++) {
                int holeIndex;
                do {
                    holeIndex = new Random().nextInt(holes.length);
                } while (usedIndices.contains(holeIndex));
                usedIndices.add(holeIndex);
                currentHoleIndices.add(holeIndex);
                View currentHole = holes[holeIndex];
                ImageView mouse = mice.get(i);
                mouse.setX(currentHole.getX() + (currentHole.getWidth() - mouse.getWidth()) / 2f);
                mouse.setY(currentHole.getY() + (currentHole.getHeight() - mouse.getHeight()) / 2f);
                mouse.setVisibility(View.VISIBLE);
                Log.d("GameEngine", "Mouse " + i + " set visible at hole " + holeIndex);
            }
        }
        boom.setVisibility(View.INVISIBLE);
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