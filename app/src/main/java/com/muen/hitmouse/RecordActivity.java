package com.muen.hitmouse;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.muen.hitmouse.databinding.ActivityRecordBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class RecordActivity extends AppCompatActivity {
    private static final String TAG = "RecordActivity"; // 日志标签，用于调试
    private ActivityRecordBinding binding; // 用于绑定布局文件中的视图
    private SharedPreferences sharedPreferences; // 用于存储用户记录数据
    private RecordAdapter recordAdapter; // RecyclerView 的适配器
    private List<RecordEntry> allRecords; // 存储所有记录
    private List<RecordEntry> currentPageRecords; // 当前页显示的记录
    private int currentPage = 0; // 当前页码（从 0 开始）
    private int pageSize = 10; // 每页显示的记录数
    private int totalPages; // 总页数

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置全屏显示，隐藏状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            // 使用 ViewBinding 初始化布局
            binding = ActivityRecordBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            Log.d(TAG, "绑定初始化成功");
        } catch (Exception e) {
            // 捕获绑定异常，避免应用崩溃
            Log.e(TAG, "绑定初始化错误", e);
            finish();
            return;
        }

        // 初始化 SharedPreferences，用于读取用户记录
        sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);
        allRecords = loadRecordData(); // 加载所有记录
        setupRecyclerView(); // 设置 RecyclerView
        setupPagination(); // 设置分页功能
        setupBackButton(); // 设置返回按钮
    }

    // 设置 RecyclerView，包括布局管理器和适配器
    private void setupRecyclerView() {
        try {
            if (binding.recyclerView == null) {
                Log.e(TAG, "recyclerView 为 null");
                return;
            }
            // 设置线性布局管理器
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
            // 启用 RecyclerView 的视图回收池，提高性能
            binding.recyclerView.setRecycledViewPool(new RecyclerView.RecycledViewPool());
            updateCurrentPageRecords(); // 初始化当前页数据
            recordAdapter = new RecordAdapter(currentPageRecords); // 创建适配器
            binding.recyclerView.setAdapter(recordAdapter); // 设置适配器
            Log.d(TAG, "RecyclerView 设置成功");
        } catch (Exception e) {
            Log.e(TAG, "设置 RecyclerView 错误", e);
        }
    }

    // 设置分页功能，包括按钮点击事件和页面状态更新
    private void setupPagination() {
        // 计算总页数，使用向上取整
        totalPages = (int) Math.ceil((double) allRecords.size() / pageSize);
        Log.d(TAG, "总记录数: " + allRecords.size() + ", 总页数: " + totalPages);

        updateButtonState(); // 初始化按钮状态

        // 设置“上一页”按钮点击事件
        binding.btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                updateCurrentPageRecords(); // 更新当前页数据
                recordAdapter.updateData(currentPageRecords); // 更新适配器数据
                updateButtonState(); // 更新按钮状态
                binding.recyclerView.scrollToPosition(0); // 滚动到顶部
                Log.d(TAG, "切换到第 " + (currentPage + 1) + " 页");
            }
        });

        // 设置“下一页”按钮点击事件
        binding.btnNext.setOnClickListener(v -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateCurrentPageRecords(); // 更新当前页数据
                recordAdapter.updateData(currentPageRecords); // 更新适配器数据
                updateButtonState(); // 更新按钮状态
                binding.recyclerView.scrollToPosition(0); // 滚动到顶部
                Log.d(TAG, "切换到第 " + (currentPage + 1) + " 页");
            }
        });
    }

    // 设置返回按钮点击事件
    private void setupBackButton() {
        binding.btnBack.setOnClickListener(v -> {
            Log.d(TAG, "点击返回按钮");
            finish(); // 关闭当前 Activity
        });
    }

    // 更新当前页显示的记录数据
    private void updateCurrentPageRecords() {
        int start = currentPage * pageSize; // 计算当前页起始索引
        int end = Math.min(start + pageSize, allRecords.size()); // 计算当前页结束索引
        currentPageRecords = new ArrayList<>(allRecords.subList(start, end)); // 截取当前页记录
    }

    // 更新分页按钮和页面信息的状态
    private void updateButtonState() {
        binding.btnPrevious.setEnabled(currentPage > 0); // 上一页按钮是否可用
        binding.btnNext.setEnabled(currentPage < totalPages - 1); // 下一页按钮是否可用
        binding.tvPageInfo.setText("第 " + (currentPage + 1) + "/" + totalPages + " 页"); // 更新页面信息
    }

    // 从 SharedPreferences 加载记录数据
    private List<RecordEntry> loadRecordData() {
        List<RecordEntry> records = new ArrayList<>();
        // 遍历存储的记录，最多支持 50 条
        for (int i = 0; i < 50; i++) {
            String key = "record_score_" + i;
            long timestamp = sharedPreferences.getLong("record_time_" + i, 0);
            int score = sharedPreferences.getInt(key, 0);
            // 仅加载有效记录（得分和时间戳均大于 0）
            if (score > 0 && timestamp > 0) {
                // 格式化时间戳为日期字符串
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
                records.add(new RecordEntry(date, score));
                Log.d(TAG, "加载记录: " + date + ", 得分: " + score);
            }
        }
        // 按时间戳降序排序（最新记录在前）
        Collections.sort(records, (r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
        return records;
    }

    // 处理物理返回键点击事件
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish(); // 关闭当前 Activity
    }
}

// 记录实体类，存储日期、得分和时间戳
class RecordEntry {
    private String date; // 记录日期
    private int score; // 得分
    private long timestamp; // 时间戳

    public RecordEntry(String date, int score) {
        this.date = date;
        this.score = score;
        this.timestamp = System.currentTimeMillis(); // 当前时间戳
    }

    public String getDate() {
        return date;
    }

    public int getScore() {
        return score;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

// RecyclerView 适配器，用于显示记录列表
class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.RecordViewHolder> {

    private List<RecordEntry> records; // 记录数据列表

    public RecordAdapter(List<RecordEntry> records) {
        this.records = records;
    }

    @Override
    public RecordViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // 加载记录卡片布局
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record_card, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecordViewHolder holder, int position) {
        RecordEntry record = records.get(position);
        // 设置日期和得分文本
        holder.tvDate.setText(record.getDate());
        holder.tvScore.setText(String.valueOf(record.getScore()) + " 只");

        // 根据得分设置卡片样式
        int score = record.getScore();
        if (score >= 20) {
            // 高分（≥20）：金色背景，较大图标
            holder.cardView.setCardBackgroundColor(0xFFFFD700);
            holder.tvDate.setTextColor(0xFF333333);
            holder.tvScore.setTextColor(0xFF333333);
            holder.ivIcon.getLayoutParams().height = 32;
            holder.ivIcon.getLayoutParams().width = 32;
            holder.ivIcon.requestLayout();
        } else if (score >= 10) {
            // 中分（10-19）：黄色背景，中等图标
            holder.cardView.setCardBackgroundColor(0xFFFF00);
            holder.tvDate.setTextColor(0xFF333333);
            holder.tvScore.setTextColor(0xFF333333);
            holder.ivIcon.getLayoutParams().height = 24;
            holder.ivIcon.getLayoutParams().width = 24;
            holder.ivIcon.requestLayout();
        } else {
            // 低分（<10）：白色背景，较小图标
            holder.cardView.setCardBackgroundColor(0xFFFFFFFF);
            holder.tvDate.setTextColor(0xFF333333);
            holder.tvScore.setTextColor(0xFF666666);
            holder.ivIcon.getLayoutParams().height = 24;
            holder.ivIcon.getLayoutParams().width = 24;
            holder.ivIcon.requestLayout();
        }

        // 为卡片添加点击动画效果（缩小后恢复）
        holder.cardView.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start())
                    .start();
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        // 启用 RecyclerView 的视图回收池，提高性能
        recyclerView.setRecycledViewPool(new RecyclerView.RecycledViewPool());
    }

    // 使用 DiffUtil 更新数据，提高列表刷新效率
    public void updateData(List<RecordEntry> newRecords) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(this.records, newRecords));
        this.records = newRecords;
        diffResult.dispatchUpdatesTo(this); // 高效更新列表
    }

    // DiffUtil 回调类，用于比较新旧数据差异
    private static class DiffCallback extends DiffUtil.Callback {
        private final List<RecordEntry> oldList;
        private final List<RecordEntry> newList;

        public DiffCallback(List<RecordEntry> oldList, List<RecordEntry> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // 判断两条记录是否为同一项（基于时间戳）
            return oldList.get(oldItemPosition).getTimestamp() == newList.get(newItemPosition).getTimestamp();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            // 判断两条记录的内容是否相同
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }
    }

    // ViewHolder 类，用于缓存卡片视图
    static class RecordViewHolder extends RecyclerView.ViewHolder {
        CardView cardView; // 卡片视图
        TextView tvDate, tvScore; // 日期和得分文本
        ImageView ivIcon; // 图标

        public RecordViewHolder(View itemView) {
            super(itemView);
            // 初始化卡片中的各个视图
            cardView = itemView.findViewById(R.id.card_view);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvScore = itemView.findViewById(R.id.tv_score);
            ivIcon = itemView.findViewById(R.id.iv_icon);
        }
    }
}