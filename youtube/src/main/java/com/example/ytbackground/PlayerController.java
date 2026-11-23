package com.example.ytbackground;

/** Controls the video playing inside the WebView. Implemented by {@link MainActivity}. */
public interface PlayerController {
    void play();

    void pause();

    void seekBy(int seconds);

    void seekTo(long positionMs);
}
