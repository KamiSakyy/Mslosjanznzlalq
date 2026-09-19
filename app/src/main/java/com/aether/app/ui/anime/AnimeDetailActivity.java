package com.aether.app.ui.anime;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.aether.app.R;
import com.bumptech.glide.Glide;

public class AnimeDetailActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.item_anime); // placeholder, real layout would be detailed
        // For simplicity, finish and show toast
        finish();
    }
}
