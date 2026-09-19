package com.aether.app.utils;

import android.content.Context;
import android.widget.TextView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class MarkdownRenderer {
    private final Markwon markwon;

    public MarkdownRenderer(Context ctx) {
        markwon = Markwon.builder(ctx)
                .usePlugin(TablePlugin.create(ctx))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build();
    }

    public void render(TextView tv, String markdown) {
        if (markdown == null) markdown = "";
        markwon.setMarkdown(tv, markdown);
    }
}
