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
        setupBackButton(); // 设置返回按钮
    }

    private void setupRecyclerView() {
        try {
            if (binding.recyclerView == null) {
                Log.e(TAG, "recyclerView 为 null");
                return;
            }
            binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
            binding.recyclerView.setRecycledViewPool(new RecyclerView.RecycledViewPool()); // 启用回收池
            updateCurrentPageRecords(); // 初始化当前页数据
            recordAdapter = new RecordAdapter(currentPageRecords);
            binding.recyclerView.setAdapter(recordAdapter);
            Log.d(TAG, "RecyclerView 设置成功");
        } catch (Exception e) {
            Log.e(TAG, "设置 RecyclerView 错误", e);
        }
    }

    private void setupPagination() {
        totalPages = (int) Math.ceil((double) allRecords.size() / pageSize);
        Log.d(TAG, "总记录数: " + allRecords.size() + ", 总页数: " + totalPages);

        updateButtonState();

        binding.btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                updateCurrentPageRecords();
                recordAdapter.updateData(currentPageRecords);
                updateButtonState();
                binding.recyclerView.scrollToPosition(0);
                Log.d(TAG, "切换到第 " + (currentPage + 1) + " 页");
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateCurrentPageRecords();
                recordAdapter.updateData(currentPageRecords);
                updateButtonState();
                binding.recyclerView.scrollToPosition(0);
                Log.d(TAG, "切换到第 " + (currentPage + 1) + " 页");
            }
        });
    }

    private void setupBackButton() {
        binding.btnBack.setOnClickListener(v -> {
            Log.d(TAG, "点击返回按钮");
            finish();
        });
    }

    private void updateCurrentPageRecords() {
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, allRecords.size());
        currentPageRecords = new ArrayList<>(allRecords.subList(start, end));
    }

    private void updateButtonState() {
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

        int score = record.getScore();
        if (score >= 20) {
            holder.cardView.setCardBackgroundColor(0xFFFFD700);
            holder.tvDate.setTextColor(0xFF333333);
            holder.tvScore.setTextColor(0xFF333333);
            holder.ivIcon.getLayoutParams().height = 32;
            holder.ivIcon.getLayoutParams().width = 32;
            holder.ivIcon.requestLayout();
        } else if (score >= 10) {
            holder.cardView.setCardBackgroundColor(0xFFFF00);
            holder.tvDate.setTextColor(0xFF333333);
            holder.tvScore.setTextColor(0xFF333333);
            holder.ivIcon.getLayoutParams().height = 24;
            holder.ivIcon.getLayoutParams().width = 24;
            holder.ivIcon.requestLayout();
        } else {
            holder.cardView.setCardBackgroundColor(0xFFFFFFFF);
            holder.tvDate.setTextColor(0xFF333333);
            holder.tvScore.setTextColor(0xFF666666);
            holder.ivIcon.getLayoutParams().height = 24;
            holder.ivIcon.getLayoutParams().width = 24;
            holder.ivIcon.requestLayout();
        }

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
        recyclerView.setRecycledViewPool(new RecyclerView.RecycledViewPool());
    }

    public void updateData(List<RecordEntry> newRecords) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(this.records, newRecords));
        this.records = newRecords;
        diffResult.dispatchUpdatesTo(this);
    }

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
            return oldList.get(oldItemPosition).getTimestamp() == newList.get(newItemPosition).getTimestamp();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvDate, tvScore;
        ImageView ivIcon;

        public RecordViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvScore = itemView.findViewById(R.id.tv_score);
            ivIcon = itemView.findViewById(R.id.iv_icon);
        }
    }
}