package com.aether.app.ui.video;

import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.aether.app.R;

public class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        playerView = findViewById(R.id.playerView);
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        String url = getIntent().getStringExtra("url");
        if (url == null) url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        MediaItem item = MediaItem.fromUri(Uri.parse(url));
        player.setMediaItem(item);
        player.prepare();
        player.play();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
