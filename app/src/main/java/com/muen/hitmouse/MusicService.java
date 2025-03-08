package com.muen.hitmouse;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;

public class MusicService extends Service {
    private MediaPlayer mediaPlayerBg;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayerBg = MediaPlayer.create(this, R.raw.back);
        mediaPlayerBg.setLooping(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaPlayerBg != null && !mediaPlayerBg.isPlaying()) {
            mediaPlayerBg.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayerBg != null) {
            if (mediaPlayerBg.isPlaying()) mediaPlayerBg.stop();
            mediaPlayerBg.release();
            mediaPlayerBg = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 添加停止音乐的方法（可选，通过 Intent 控制）
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf(); // 应用关闭时停止服务
    }
}