package com.aether.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.aether.app.R;
import com.aether.app.models.ChatSession;
import com.aether.app.utils.TimeUtils;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class SessionsAdapter extends RecyclerView.Adapter<SessionsAdapter.VH> {
    private final List<ChatSession> items = new ArrayList<>();
    private OnSessionActionListener listener;

    public interface OnSessionActionListener {
        void onSelect(ChatSession session);
        void onDelete(ChatSession session);
        void onPin(ChatSession session);
    }

    public void setListener(OnSessionActionListener l) { this.listener = l; }

    public void setItems(List<ChatSession> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatSession s = items.get(position);
        holder.tvTitle.setText(s.title != null ? s.title : "Новый чат");
        String meta = s.messageCount + " сообщ • " + TimeUtils.relativeTime(s.updatedAt);
        if (s.isPinned) meta = "📌 " + meta;
        holder.tvMeta.setText(meta);
        holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onSelect(s); });
        holder.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(s); });
        holder.btnPin.setOnClickListener(v -> { if (listener != null) listener.onPin(s); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMeta;
        MaterialButton btnDelete, btnPin;
        VH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvMeta = v.findViewById(R.id.tvMeta);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnPin = v.findViewById(R.id.btnPin);
        }
    }
}
