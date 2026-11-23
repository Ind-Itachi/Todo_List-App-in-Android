package com.example.ytbackground;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

/**
 * A WebView that never tells Chromium it has become invisible.
 *
 * When an app is minimised or the screen is locked, Android reports the window
 * as GONE/INVISIBLE. The WebView forwards that to the page, and media gets
 * suspended. By always reporting VISIBLE, the page (and its video) keeps
 * running in the background.
 */
public class BackgroundWebView extends WebView {

    public BackgroundWebView(Context context) {
        super(context);
    }

    public BackgroundWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BackgroundWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(View.VISIBLE);
    }
}
