package com.aether.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aether.app.R;
import com.aether.app.models.ChatMessage;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_STREAMING = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private com.aether.app.models.StreamingState streaming;
    private OnMessageActionListener listener;

    public interface OnMessageActionListener {
        void onCopy(ChatMessage msg);
        void onRegenerate(ChatMessage msg);
        void onShare(ChatMessage msg);
        void onEditUser(String content);
    }

    public void setListener(OnMessageActionListener l) { this.listener = l; }

    public void setMessages(List<ChatMessage> list) {
        messages.clear();
        if (list != null) messages.addAll(list);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void setStreaming(com.aether.app.models.StreamingState s) {
        this.streaming = s;
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        if (position == messages.size() && streaming != null) return TYPE_STREAMING;
        ChatMessage m = messages.get(position);
        return m.isUser() ? TYPE_USER : TYPE_ASSISTANT;
    }

    @Override public int getItemCount() {
        return messages.size() + (streaming != null ? 1 : 0);
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            return new UserVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_user, parent, false));
        } else if (viewType == TYPE_STREAMING) {
            return new StreamingVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_assistant, parent, false));
        } else {
            return new AssistantVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_assistant, parent, false));
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof UserVH) {
            ((UserVH) holder).bind(messages.get(position), listener);
        } else if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).bind(messages.get(position), listener);
        } else if (holder instanceof StreamingVH) {
            ((StreamingVH) holder).bind(streaming);
        }
    }

    static class UserVH extends RecyclerView.ViewHolder {
        TextView tvContent;
        ImageView ivAttachment;
        MaterialButton btnCopy, btnEdit;
        UserVH(View v) {
            super(v);
            tvContent = v.findViewById(R.id.tvContent);
            ivAttachment = v.findViewById(R.id.ivAttachment);
            btnCopy = v.findViewById(R.id.btnCopy);
            btnEdit = v.findViewById(R.id.btnEdit);
        }
        void bind(ChatMessage msg, OnMessageActionListener listener) {
            tvContent.setText(msg.content);
            if (msg.attachmentDataUrl != null && !msg.attachmentDataUrl.isEmpty()) {
                ivAttachment.setVisibility(View.VISIBLE);
                try {
                    Glide.with(ivAttachment.getContext()).load(msg.attachmentDataUrl).into(ivAttachment);
                } catch (Exception ignored) {}
            } else {
                ivAttachment.setVisibility(View.GONE);
            }
            btnCopy.setOnClickListener(view -> {
                ClipboardManager cm = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("message", msg.content));
                Toast.makeText(view.getContext(), "Скопировано", Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onCopy(msg);
            });
            btnEdit.setOnClickListener(view -> {
                if (listener != null) listener.onEditUser(msg.content);
            });
        }
    }

    static class AssistantVH extends RecyclerView.ViewHolder {
        TextView tvProvider, tvModel, tvLatency, tvContent, tvReasoning, tvFailover;
        View reasoningContainer;
        ImageView ivGenerated;
        RecyclerView rvAnime, rvImages;
        MaterialButton btnCopy, btnRegen, btnShare, btnSources;

        AssistantVH(View v) {
            super(v);
            tvProvider = v.findViewById(R.id.tvProvider);
            tvModel = v.findViewById(R.id.tvModel);
            tvLatency = v.findViewById(R.id.tvLatency);
            tvContent = v.findViewById(R.id.tvContent);
            tvReasoning = v.findViewById(R.id.tvReasoning);
            tvFailover = v.findViewById(R.id.tvFailover);
            reasoningContainer = v.findViewById(R.id.reasoningContainer);
            ivGenerated = v.findViewById(R.id.ivGenerated);
            rvAnime = v.findViewById(R.id.rvAnime);
            rvImages = v.findViewById(R.id.rvImages);
            btnCopy = v.findViewById(R.id.btnCopy);
            btnRegen = v.findViewById(R.id.btnRegen);
            btnShare = v.findViewById(R.id.btnShare);
            btnSources = v.findViewById(R.id.btnSources);
        }

        void bind(ChatMessage msg, OnMessageActionListener listener) {
            tvProvider.setText(msg.providerName != null ? msg.providerName : "Люми");
            tvModel.setText(msg.modelId != null ? msg.modelId : "");
            tvLatency.setText(msg.latencyMs != null ? (msg.latencyMs < 1000 ? msg.latencyMs + " мс" : String.format("%.1f с", msg.latencyMs / 1000f)) : "");
            // Simple markdown rendering: for now set raw, but we could use Markwon
            tvContent.setText(msg.content != null ? msg.content : "");

            if (msg.reasoningTrace != null && !msg.reasoningTrace.isEmpty()) {
                reasoningContainer.setVisibility(View.VISIBLE);
                tvReasoning.setText(msg.reasoningTrace);
            } else {
                reasoningContainer.setVisibility(View.GONE);
            }

            if (msg.imageUrl != null && !msg.imageUrl.isEmpty()) {
                ivGenerated.setVisibility(View.VISIBLE);
                try {
                    Glide.with(ivGenerated.getContext()).load(msg.imageUrl).into(ivGenerated);
                } catch (Exception ignored) {}
            } else {
                ivGenerated.setVisibility(View.GONE);
            }

            if (msg.failoverLog != null && !msg.failoverLog.isEmpty()) {
                tvFailover.setVisibility(View.VISIBLE);
                StringBuilder sb = new StringBuilder();
                for (com.aether.app.models.FailoverHop hop : msg.failoverLog) {
                    sb.append(hop.providerName).append(" ").append(hop.status).append(" ").append(hop.latencyMs).append("мс\n");
                }
                tvFailover.setText(sb.toString());
            } else {
                tvFailover.setVisibility(View.GONE);
            }

            // Anime & images handling would need adapters - hide for now unless data present
            if (msg.animeData != null) {
                rvAnime.setVisibility(View.VISIBLE);
            } else {
                rvAnime.setVisibility(View.GONE);
            }
            if (msg.imagesData != null && !msg.imagesData.isEmpty()) {
                rvImages.setVisibility(View.VISIBLE);
                rvImages.setLayoutManager(new LinearLayoutManager(rvImages.getContext(), LinearLayoutManager.HORIZONTAL, false));
                // Simple gallery adapter
                rvImages.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
                        return new RecyclerView.ViewHolder(view){};
                    }
                    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
                        ImageView iv = holder.itemView.findViewById(R.id.ivImage);
                        TextView tv = holder.itemView.findViewById(R.id.tvTitle);
                        ChatMessage.ImageData data = msg.imagesData.get(pos);
                        tv.setText(data.title);
                        try { Glide.with(iv.getContext()).load(data.thumbnail != null ? data.thumbnail : data.image).into(iv); } catch (Exception ignored) {}
                    }
                    @Override public int getItemCount() { return msg.imagesData.size(); }
                });
            } else {
                rvImages.setVisibility(View.GONE);
            }

            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("response", msg.content));
                Toast.makeText(v.getContext(), "Скопировано", Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onCopy(msg);
            });
            btnRegen.setOnClickListener(v -> { if (listener != null) listener.onRegenerate(msg); });
            btnShare.setOnClickListener(v -> { if (listener != null) listener.onShare(msg); });
        }
    }

    static class StreamingVH extends RecyclerView.ViewHolder {
        TextView tvProvider, tvModel, tvContent, tvReasoning;
        View reasoningContainer;
        StreamingVH(View v) {
            super(v);
            tvProvider = v.findViewById(R.id.tvProvider);
            tvModel = v.findViewById(R.id.tvModel);
            tvContent = v.findViewById(R.id.tvContent);
            tvReasoning = v.findViewById(R.id.tvReasoning);
            reasoningContainer = v.findViewById(R.id.reasoningContainer);
            v.findViewById(R.id.btnCopy).setVisibility(View.GONE);
            v.findViewById(R.id.btnRegen).setVisibility(View.GONE);
            v.findViewById(R.id.btnShare).setVisibility(View.GONE);
        }
        void bind(com.aether.app.models.StreamingState s) {
            if (s == null) return;
            tvProvider.setText(s.providerName.isEmpty() ? "Подбираем модель…" : s.providerName);
            tvModel.setText(s.modelId);
            tvContent.setText(s.content.isEmpty() ? "▌" : s.content + "▌");
            if (s.reasoning != null && !s.reasoning.isEmpty()) {
                reasoningContainer.setVisibility(View.VISIBLE);
                tvReasoning.setText(s.reasoning);
            } else {
                reasoningContainer.setVisibility(View.GONE);
            }
        }
    }
}
