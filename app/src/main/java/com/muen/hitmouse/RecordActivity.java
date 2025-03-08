package com.muen.hitmouse;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.muen.hitmouse.databinding.ActivityRecordBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class RecordActivity extends AppCompatActivity {
    private static final String TAG = "RecordActivity";
    private ActivityRecordBinding binding;
    private SharedPreferences sharedPreferences;
    private RecordAdapter recordAdapter;
    private List<RecordEntry> allRecords; // 存储所有记录
    private List<RecordEntry> currentPageRecords; // 当前页显示的记录
    private int currentPage = 0; // 当前页码（从 0 开始）
    private int pageSize = 10; // 每页显示的记录数
    private int totalPages; // 总页数

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            binding = ActivityRecordBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            Log.d(TAG, "绑定初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "绑定初始化错误", e);
            finish();
            return;
        }

        sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);
        allRecords = loadRecordData(); // 加载所有记录
        setupRecyclerView();
        setupPagination();
    }

    private void setupRecyclerView() {
        try {
            if (binding.recyclerView == null) {
                Log.e(TAG, "recyclerView 为 null");
                return;
            }
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
            updateCurrentPageRecords(); // 初始化当前页数据
            recordAdapter = new RecordAdapter(currentPageRecords);
            binding.recyclerView.setAdapter(recordAdapter);
            Log.d(TAG, "RecyclerView 设置成功");
        } catch (Exception e) {
            Log.e(TAG, "设置 RecyclerView 错误", e);
        }
    }

    private void setupPagination() {
        // 计算总页数
        totalPages = (int) Math.ceil((double) allRecords.size() / pageSize);
        Log.d(TAG, "总记录数: " + allRecords.size() + ", 总页数: " + totalPages);

        // 初始按钮状态
        updateButtonState();

        // 上一页按钮点击事件
        binding.btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                updateCurrentPageRecords();
                recordAdapter.updateData(currentPageRecords);
                updateButtonState();
                binding.recyclerView.scrollToPosition(0); // 滚动到顶部
                Log.d(TAG, "切换到第 " + (currentPage + 1) + " 页");
            }
        });

        // 下一页按钮点击事件
        binding.btnNext.setOnClickListener(v -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateCurrentPageRecords();
                recordAdapter.updateData(currentPageRecords);
                updateButtonState();
                binding.recyclerView.scrollToPosition(0); // 滚动到顶部
                Log.d(TAG, "切换到第 " + (currentPage + 1) + " 页");
            }
        });
    }

    private void updateCurrentPageRecords() {
        // 计算当前页的记录范围
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, allRecords.size());
        currentPageRecords = new ArrayList<>(allRecords.subList(start, end));
    }

    private void updateButtonState() {
        // 控制按钮启用/禁用状态并更新页码
        binding.btnPrevious.setEnabled(currentPage > 0);
        binding.btnNext.setEnabled(currentPage < totalPages - 1);
        binding.tvPageInfo.setText("第 " + (currentPage + 1) + "/" + totalPages + " 页");
    }

    private List<RecordEntry> loadRecordData() {
        List<RecordEntry> records = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String key = "record_score_" + i;
            long timestamp = sharedPreferences.getLong("record_time_" + i, 0);
            int score = sharedPreferences.getInt(key, 0);
            if (score > 0 && timestamp > 0) {
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
                records.add(new RecordEntry(date, score));
                Log.d(TAG, "加载记录: " + date + ", 得分: " + score);
            }
        }
        Collections.sort(records, (r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
        return records;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}

class RecordEntry {
    private String date;
    private int score;
    private long timestamp;

    public RecordEntry(String date, int score) {
        this.date = date;
        this.score = score;
        this.timestamp = System.currentTimeMillis();
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

class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.RecordViewHolder> {

    private List<RecordEntry> records;

    public RecordAdapter(List<RecordEntry> records) {
        this.records = records;
    }

    @Override
    public RecordViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record_card, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecordViewHolder holder, int position) {
        RecordEntry record = records.get(position);
        holder.tvDate.setText(record.getDate());
        holder.tvScore.setText(String.valueOf(record.getScore()) + " 只");
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void updateData(List<RecordEntry> newRecords) {
        this.records = newRecords;
        notifyDataSetChanged();
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvScore;

        public RecordViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvScore = itemView.findViewById(R.id.tv_score);
        }
    }
}