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
    public static final int INITIAL_PLAY_TIME = 60000; // 初始游戏时间（60秒）
    private static final int TIME_TICK = 1000; // 时间减少的间隔（1秒）
    private static final int MSG_UPDATE_UI = 0x101; // 更新 UI 的消息标识
    private static final int MSG_GAME_OVER = 0x102; // 游戏结束的消息标识
    private static final int MULTI_MOUSE_START_TIME = 10000; // 开始增加多只地鼠的时间（10秒）
    private static final int MOUSE_INCREASE_INTERVAL = 10000; // 地鼠数量增加的间隔（10秒）
    public static final int MAX_MOUSE_COUNT = 5; // 最大地鼠数量

    private final Context context;
    private final Handler handler;
    private final View[] holes; // 简单模式下的地鼠洞数组
    private final List<ImageView> mice; // 地鼠视图列表
    private final ImageView boom; // 击中爆炸效果视图
    private final ImageView hunter; // 猎人（锤子）视图
    private final TextView timeView; // 时间显示文本
    private final TextView scoreText; // 分数显示文本
    private final boolean isRandomMode; // 是否为随机模式（困难模式）
    private final PlayActivity activity;

    private int playTime = INITIAL_PLAY_TIME; // 当前剩余时间
    private int count = 0; // 当前得分
    private volatile boolean isRunning = true; // 游戏是否运行
    private volatile boolean isPaused = false; // 游戏是否暂停
    private volatile int currentHoleIndex = -1; // 当前地鼠洞索引（未使用）
    private final AtomicBoolean isScheduled = new AtomicBoolean(false); // 调度标志（未使用）
    private int comboCount = 0; // 当前连击次数
    private int mouseCount = 1; // 当前活跃地鼠数量

    private List<Integer> currentHoleIndices = new ArrayList<>(); // 当前地鼠洞索引列表（简单模式）

    // 构造函数，初始化游戏引擎所需的所有视图和参数
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

    // 开始游戏，重置状态并启动计时和地鼠刷新任务
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
        handler.post(mouseRunnable); // 启动地鼠刷新任务
        handler.post(timeRunnable); // 启动计时任务
        Log.d("GameEngine", "Game started");
    }

    // 恢复游戏，从暂停状态继续
    public void resumeGame() {
        if (!isRunning || !isPaused) return;
        isPaused = false;
        handler.post(mouseRunnable);
        handler.post(timeRunnable);
        Log.d("GameEngine", "Game resumed");
    }

    // 暂停游戏，停止任务并隐藏地鼠
    public void stopGame() {
        isPaused = true;
        handler.removeCallbacks(mouseRunnable);
        handler.removeCallbacks(timeRunnable);
        for (ImageView mouse : mice) {
            mouse.setVisibility(View.INVISIBLE);
        }
        boom.setVisibility(View.INVISIBLE);
        Log.d("GameEngine", "Game stopped");
    }

    // 结束游戏，清理所有任务并隐藏视图
    public void endGame() {
        isRunning = false;
        isPaused = false;
        handler.removeCallbacksAndMessages(null); // 清理所有消息和回调
        for (ImageView mouse : mice) {
            mouse.setVisibility(View.INVISIBLE);
        }
        boom.setVisibility(View.INVISIBLE);
        Log.d("GameEngine", "Game ended");
    }

    // 处理击中地鼠的逻辑，同步保护避免多线程问题
    public synchronized ImageView hitMouse(ImageView hunter) {
        if (!isRunning || isPaused) return null; // 游戏未运行或暂停时不处理

        ImageView hitMouse = null;
        synchronized (mice) {
            if (isRandomMode) {
                // 随机模式：检查猎人与所有地鼠的位置
                for (ImageView mouse : mice) {
                    if (isHunterOnMouse(hunter, mouse)) {
                        hitMouse = mouse;
                        break;
                    }
                }
            } else {
                // 简单模式：检查猎人与当前洞中的地鼠
                // 修复：限制循环范围，避免 currentHoleIndices 或 mice 越界
                int limit = Math.min(mouseCount, Math.min(mice.size(), currentHoleIndices.size()));
                for (int i = 0; i < limit; i++) {
                    View currentHole = holes[currentHoleIndices.get(i)];
                    if (isHunterInHole(hunter, currentHole)) {
                        hitMouse = mice.get(i);
                        break;
                    }
                }
            }
        }

        // 如果击中，更新得分和 UI
        if (hitMouse != null) {
            comboCount++;
            int score = (comboCount >= 3) ? 2 : 1; // 连击3次以上双倍得分
            count += score;
            hitMouse.setVisibility(View.INVISIBLE); // 隐藏被击中的地鼠
            boom.setX(hitMouse.getX() - 25f);
            boom.setY(hitMouse.getY() - 25f);
            boom.setVisibility(View.VISIBLE); // 显示爆炸效果
            handler.postDelayed(() -> boom.setVisibility(View.INVISIBLE), 300); // 300ms 后隐藏爆炸
            scoreText.setText(String.format("得分: %d", count));
        }
        return hitMouse;
    }

    // 获取最后一次击中的得分
    public int getLastScore() {
        return (comboCount >= 3) ? 2 : 1;
    }

    // 显示得分动画（未与 PlayActivity 完全同步，可能需要调整）
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
                .withEndAction(() -> {
                    if (scoreAnim.getParent() != null) {
                        ((ConstraintLayout) scoreAnim.getParent()).removeView(scoreAnim);
                    }
                })
                .start();
    }

    // 获取当前得分，同步保护
    public synchronized int getCount() {
        return count;
    }

    // 判断游戏是否结束
    public boolean isGameOver() {
        return playTime <= 0 || !isRunning;
    }

    // 地鼠刷新任务，定期更新地鼠位置
    private final Runnable mouseRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isPaused && playTime > 0) {
                int index = new Random().nextInt(isRandomMode ? 100 : holes.length);
                handler.sendMessage(handler.obtainMessage(MSG_UPDATE_UI, index, 0));
                handler.postDelayed(this, getSleepTime()); // 根据游戏进度调整刷新间隔
            }
        }
    };

    // 计时任务，控制时间减少和地鼠数量增加
    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isPaused && playTime > 0) {
                playTime -= TIME_TICK;
                timeView.setText(String.format("剩余时间: %d秒", playTime / 1000));
                Log.d("GameEngine", "Time remaining: " + playTime / 1000 + "s, Mouse Count: " + mouseCount);

                // 动态增加地鼠数量
                int elapsedTime = INITIAL_PLAY_TIME - playTime;
                if (elapsedTime >= MULTI_MOUSE_START_TIME) {
                    int expectedMouseCount = Math.min(MAX_MOUSE_COUNT, 1 + (elapsedTime - MULTI_MOUSE_START_TIME) / MOUSE_INCREASE_INTERVAL);
                    if (mouseCount < expectedMouseCount) {
                        synchronized (mice) {
                            while (mice.size() < expectedMouseCount) {
                                activity.addMouseView(); // 添加新地鼠视图
                            }
                            mouseCount = expectedMouseCount;
                        }
                        Log.d("GameEngine", "Updated Mouse Count to: " + mouseCount);
                    }
                }

                // 时间耗尽时结束游戏
                if (playTime <= 0) {
                    Log.d("GameEngine", "Time up, sending game over message");
                    handler.sendEmptyMessage(MSG_GAME_OVER);
                } else {
                    handler.postDelayed(this, TIME_TICK);
                }
            }
        }
    };

    // 计算地鼠刷新间隔，随游戏进度加快
    private long getSleepTime() {
        long baseTime = playTime > 40000 ? 1500 : playTime > 20000 ? 1000 : 800;
        return Math.max(500, baseTime - (mouseCount * 100) - (count / 10));
    }

    // 更新地鼠位置和 UI
    public void updateUI(int index) {
        if (!isRunning || isPaused) return;

        Log.d("GameEngine", "Updating UI, Mouse Count: " + mouseCount + ", Mice Size: " + mice.size());
        synchronized (mice) {
            if (isRandomMode) {
                // 随机模式：地鼠随机出现在屏幕上
                for (int i = 0; i < mouseCount && i < mice.size(); i++) {
                    ImageView mouse = mice.get(i);
                    int finalI = i;
                    mouse.post(() -> {
                        int maxX = mouse.getRootView().getWidth() - mouse.getWidth();
                        int maxY = mouse.getRootView().getHeight() - mouse.getHeight();
                        if (maxX <= 0 || maxY <= 0) {
                            Log.w("GameEngine", "View not ready, skipping update for mouse " + finalI);
                            return;
                        }
                        mouse.setX(new Random().nextInt(maxX));
                        mouse.setY(new Random().nextInt(maxY));
                        mouse.setVisibility(View.VISIBLE);
                        Log.d("GameEngine", "Mouse " + finalI + " set visible at (" + mouse.getX() + ", " + mouse.getY() + ")");
                    });
                }
            } else {
                // 简单模式：地鼠出现在固定洞中
                currentHoleIndices.clear();
                List<Integer> usedIndices = new ArrayList<>();
                // 修复：确保 currentHoleIndices 与 mice 和 mouseCount 同步
                int limit = Math.min(mouseCount, mice.size());
                for (int i = 0; i < limit; i++) {
                    int holeIndex;
                    do {
                        holeIndex = new Random().nextInt(holes.length);
                    } while (usedIndices.contains(holeIndex)); // 确保洞不重复
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
            boom.setVisibility(View.INVISIBLE); // 隐藏爆炸效果
        }
    }

    // 检查猎人是否在洞内（简单模式）
    private boolean isHunterInHole(ImageView hunter, View hole) {
        float holeCenterX = hole.getX() + hole.getWidth() / 2f;
        float holeCenterY = hole.getY() + hole.getHeight() / 2f;
        float hunterX = hunter.getX() + hunter.getWidth() / 2f;
        float hunterY = hunter.getY() + hunter.getHeight() / 2f;

        float tolerance = 60f; // 容差范围
        return Math.abs(hunterX - holeCenterX) < tolerance &&
                Math.abs(hunterY - holeCenterY) < tolerance;
    }

    // 检查猎人是否击中地鼠（随机模式）
    private boolean isHunterOnMouse(ImageView hunter, ImageView mouse) {
        if (mouse.getVisibility() != View.VISIBLE) return false; // 未显示的地鼠不算击中

        float mouseCenterX = mouse.getX() + mouse.getWidth() / 2f;
        float mouseCenterY = mouse.getY() + mouse.getHeight() / 2f;
        float hunterX = hunter.getX() + hunter.getWidth() / 2f;
        float hunterY = hunter.getY() + hunter.getHeight() / 2f;

        float tolerance = 60f; // 容差范围
        return Math.abs(hunterX - mouseCenterX) < tolerance &&
                Math.abs(hunterY - mouseCenterY) < tolerance;
    }
}