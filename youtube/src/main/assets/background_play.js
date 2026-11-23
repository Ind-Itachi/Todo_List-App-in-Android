/*
 * Injected into every YouTube page.
 *
 * 1. Makes the page believe it is always visible / focused, so YouTube never
 *    pauses the video when the app is minimised or the screen is locked.
 * 2. Watches the <video> element and reports play / pause / title to the app
 *    (YTBridge), which drives the media notification and lock-screen controls.
 * 3. Exposes window.__ytbg so the app can play / pause / seek from the
 *    notification, and auto-resumes playback if something pauses the video
 *    while the app is in the background.
 */
(function () {
  if (window.__ytbg) return;

  var st = (window.__ytbg = {
    background: false,  // app is minimised / screen locked
    wantPlay: false,    // the user wants the video to be playing
    resumes: []         // timestamps of automatic resumes (loop guard)
  });

  // ---- 1. Always visible -------------------------------------------------
  function define(obj, prop, value) {
    try {
      Object.defineProperty(obj, prop, {
        configurable: true,
        get: function () { return value; }
      });
    } catch (e) {}
  }
  define(Document.prototype, 'hidden', false);
  define(Document.prototype, 'webkitHidden', false);
  define(Document.prototype, 'visibilityState', 'visible');
  define(Document.prototype, 'webkitVisibilityState', 'visible');
  try { Document.prototype.hasFocus = function () { return true; }; } catch (e) {}

  ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze', 'blur'].forEach(function (type) {
    var block = function (e) {
      // Only swallow page-level events; element blur (e.g. search box) still works.
      if (e.target === window || e.target === document) e.stopImmediatePropagation();
    };
    window.addEventListener(type, block, true);
    document.addEventListener(type, block, true);
  });

  // ---- 2. Report state to the app ---------------------------------------
  function isWatchPage() {
    return /^\/(watch|shorts|live|embed)/.test(location.pathname);
  }

  function videoId() {
    try {
      var u = new URL(location.href);
      var v = u.searchParams.get('v');
      if (v) return v;
      var m = u.pathname.match(/^\/(?:shorts|live|embed)\/([\w-]{6,})/);
      return m ? m[1] : '';
    } catch (e) { return ''; }
  }

  function title() {
    return (document.title || '').replace(/\s*-\s*YouTube$/, '').trim();
  }

  // Muted auto-previews in the home feed are not "real" playback.
  function isPreview(v) {
    return v.muted && !isWatchPage();
  }

  function mainVideo() {
    return document.querySelector('#movie_player video') ||
           document.querySelector('video.html5-main-video') ||
           document.querySelector('video');
  }

  function report(v) {
    if (!v || isPreview(v)) return;
    try {
      YTBridge.onState(
        !v.paused && !v.ended,
        v.currentTime || 0,
        isFinite(v.duration) ? v.duration : 0,
        title(),
        videoId()
      );
    } catch (e) {}
  }

  function tryResume(v) {
    var now = Date.now();
    st.resumes = st.resumes.filter(function (t) { return now - t < 10000; });
    if (st.resumes.length >= 3) { st.wantPlay = false; report(v); return; } // give up, avoid loops
    st.resumes.push(now);
    setTimeout(function () {
      if (v.paused && st.wantPlay) {
        var p = v.play();
        if (p && p.catch) p.catch(function () {});
      }
    }, 300);
  }

  function hook(v) {
    if (v.__ytbgHooked) return;
    v.__ytbgHooked = true;

    v.addEventListener('play', function () {
      if (!isPreview(v)) st.wantPlay = true;
      report(v);
    });
    v.addEventListener('playing', function () { report(v); });
    v.addEventListener('pause', function () {
      if (st.background && st.wantPlay && !v.ended && !isPreview(v)) {
        // Paused by the system / page because we went to the background.
        tryResume(v);
        return;
      }
      if (!isPreview(v)) st.wantPlay = false;
      report(v);
    });
    ['ended', 'seeked', 'loadedmetadata', 'durationchange', 'ratechange'].forEach(function (t) {
      v.addEventListener(t, function () { report(v); });
    });
  }

  var lastKey = '';
  setInterval(function () {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) hook(vids[i]);
    var v = mainVideo();
    var key = title() + '|' + videoId();
    if (v && key !== lastKey) { lastKey = key; report(v); }
  }, 1000);

  // ---- 3. Controls used by the app --------------------------------------
  st.setBackground = function (b) { st.background = !!b; };
  st.play = function () {
    var v = mainVideo();
    if (!v) return;
    st.wantPlay = true;
    var p = v.play();
    if (p && p.catch) p.catch(function () {});
  };
  st.pause = function () {
    var v = mainVideo();
    st.wantPlay = false;
    if (v) v.pause();
  };
  st.seekBy = function (sec) {
    var v = mainVideo();
    if (!v) return;
    var t = (v.currentTime || 0) + sec;
    if (isFinite(v.duration)) t = Math.min(t, v.duration - 0.5);
    v.currentTime = Math.max(0, t);
  };
  st.seekTo = function (sec) {
    var v = mainVideo();
    if (v) v.currentTime = Math.max(0, sec);
  };
})();
