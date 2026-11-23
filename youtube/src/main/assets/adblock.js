/*
 * Ad blocker, injected into every YouTube page before YouTube's own scripts.
 *
 * 1. Removes ad data (adPlacements, playerAds, adSlots, promoted feed items)
 *    from YouTube's JSON before the player sees it, so video ads are never
 *    scheduled.
 * 2. If an ad still plays, mutes it, jumps to its end and presses "Skip".
 * 3. Hides ad banners, promoted items and "get Premium" pop-ups with CSS.
 */
(function () {
  if (window.__ytAdblock) return;
  window.__ytAdblock = true;

  // ---- 1. Strip ads from data -------------------------------------------
  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adBreakParams'];
  var AD_RENDERERS = [
    'adSlotRenderer', 'adSlotAndLayoutRenderer', 'inFeedAdLayoutRenderer',
    'promotedSparklesWebRenderer', 'promotedSparklesTextSearchRenderer',
    'promotedVideoRenderer', 'compactPromotedVideoRenderer', 'compactPromotedItemRenderer',
    'displayAdRenderer', 'searchPyvRenderer', 'actionCompanionAdRenderer', 'bannerPromoRenderer',
    'statementBannerRenderer', 'brandVideoSingletonRenderer', 'brandVideoShelfRenderer'
  ];
  var AD_HINT = /"(adPlacements|playerAds|adSlots|adSlotRenderer|adSlotAndLayoutRenderer|inFeedAdLayoutRenderer|promoted\w*Renderer|compactPromoted\w*Renderer|displayAdRenderer|searchPyvRenderer|actionCompanionAdRenderer|bannerPromoRenderer|statementBannerRenderer|brandVideo\w*Renderer)"/;

  function hasAdRenderer(o) {
    if (!o || typeof o !== 'object') return false;
    for (var i = 0; i < AD_RENDERERS.length; i++) if (AD_RENDERERS[i] in o) return true;
    return false;
  }

  function isAdItem(it) {
    if (!it || typeof it !== 'object') return false;
    if (hasAdRenderer(it)) return true;
    // Feed items wrap their content, e.g. { richItemRenderer: { content: { adSlotRenderer } } }
    var c = (it.richItemRenderer && it.richItemRenderer.content) ||
            (it.richSectionRenderer && it.richSectionRenderer.content);
    if (hasAdRenderer(c)) return true;
    var sec = it.itemSectionRenderer && it.itemSectionRenderer.contents;
    if (Array.isArray(sec) && sec.length > 0 && sec.every(hasAdRenderer)) return true;
    return false;
  }

  function prune(o, depth) {
    if (!o || typeof o !== 'object' || depth > 60) return o;
    if (Array.isArray(o)) {
      for (var i = o.length - 1; i >= 0; i--) {
        if (isAdItem(o[i])) o.splice(i, 1);
        else prune(o[i], depth + 1);
      }
      return o;
    }
    for (var k = 0; k < AD_KEYS.length; k++) {
      if (Object.prototype.hasOwnProperty.call(o, AD_KEYS[k])) delete o[AD_KEYS[k]];
    }
    for (var key in o) {
      if (Object.prototype.hasOwnProperty.call(o, key)) prune(o[key], depth + 1);
    }
    return o;
  }
  window.__ytAdblockPrune = function (o) { return prune(o, 0); };

  // JSON.parse — covers fetch().text() + parse, XHR and inline JSON.parse('...')
  var origParse = JSON.parse;
  JSON.parse = function (text, reviver) {
    var result = origParse.apply(this, arguments);
    try {
      if (result && typeof result === 'object' && typeof text === 'string' && AD_HINT.test(text)) {
        prune(result, 0);
      }
    } catch (e) {}
    return result;
  };

  // fetch(...).json() — route through the patched JSON.parse
  try {
    Response.prototype.json = function () {
      return this.text().then(function (t) { return JSON.parse(t); });
    };
  } catch (e) {}

  // Data embedded in the HTML page: var ytInitialPlayerResponse = {...}
  ['ytInitialPlayerResponse', 'ytInitialData', 'ytInitialReelWatchSequenceResponse'].forEach(function (name) {
    var value = window[name];
    try {
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return value; },
        set: function (v) {
          try { prune(v, 0); } catch (e) {}
          value = v;
        }
      });
    } catch (e) {}
  });

  // ---- 2. Skip any ad that still plays ----------------------------------
  var SKIP_SELECTORS = [
    '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button',
    '.ytp-ad-skip-button-slot button', '.ytp-ad-skip-button-container button',
    'ytm-skip-ad-button button', '[class*="skip-ad"] button', '[class*="ad-skip"] button',
    'button[class*="skip-ad"]', 'button[class*="ad-skip"]'
  ].join(',');
  var CLOSE_SELECTORS = '.ytp-ad-overlay-close-button, .ytp-ad-overlay-close-container';

  var adState = null; // { muted, rate } of the video before the ad

  function clickAll(selector) {
    var els = document.querySelectorAll(selector);
    for (var i = 0; i < els.length; i++) {
      try { els[i].click(); } catch (e) {}
    }
  }

  function handleAds() {
    clickAll(CLOSE_SELECTORS);
    var player = document.querySelector('.html5-video-player') || document.getElementById('movie_player');
    if (!player) return;
    var v = player.querySelector('video');
    var adShowing = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');

    if (adShowing && v) {
      if (!adState) adState = { muted: v.muted, rate: v.playbackRate || 1 };
      v.muted = true;
      try { v.playbackRate = 16; } catch (e) {}
      if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.2) {
        try { v.currentTime = v.duration - 0.1; } catch (e) {}
      }
      clickAll(SKIP_SELECTORS);
    } else if (adState && v) {
      v.muted = adState.muted;
      try { v.playbackRate = adState.rate; } catch (e) {}
      adState = null;
    }

    // "Ad blockers are not allowed" pop-up: remove it and keep playing.
    var enforcement = document.querySelector('ytm-enforcement-message-view-model, ytd-enforcement-message-view-model');
    if (enforcement) {
      var dialog = enforcement.closest('tp-yt-paper-dialog, ytm-dialog, bottom-sheet-container, dialog') || enforcement;
      dialog.remove();
      var bd = document.querySelector('tp-yt-iron-overlay-backdrop');
      if (bd) bd.remove();
      if (v && v.paused && window.__ytbg && window.__ytbg.wantPlay) v.play().catch(function () {});
    }
  }
  setInterval(handleAds, 250);

  // ---- 3. Hide ad elements ----------------------------------------------
  var CSS = [
    'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-sparkles-text-search-renderer',
    'ytm-promoted-video-renderer', 'ytm-compact-promoted-video-renderer',
    'ytm-companion-ad-renderer', 'ytm-companion-slot', 'ytm-ad-slot-renderer', 'ad-slot-renderer',
    'ytm-in-feed-ad-layout-renderer', 'ytm-display-ad-renderer', 'ytm-search-pyv-renderer',
    'ytm-banner-promo-renderer', 'ytm-statement-banner-renderer', 'ytm-mealbar-promo-renderer',
    'ytm-brand-video-singleton-renderer', 'ytm-paid-content-overlay-renderer',
    'ytd-ad-slot-renderer', 'ytd-promoted-sparkles-web-renderer', 'ytd-display-ad-renderer',
    'ytd-banner-promo-renderer', 'ytd-statement-banner-renderer', '#masthead-ad', '#player-ads',
    '.ytp-ad-overlay-container', '.ytp-ad-overlay-slot', '.ytp-ad-image-overlay',
    '.ytp-featured-product', '.ytp-suggested-action',
    'ytm-enforcement-message-view-model', 'ytd-enforcement-message-view-model'
  ].join(',\n') + ' { display: none !important; }\n' +
    // :has() rules are separate so older WebViews without :has() still apply the rest.
    [
      'ytm-rich-item-renderer:has(ytm-ad-slot-renderer)',
      'ytm-rich-item-renderer:has(ad-slot-renderer)',
      'ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer)'
    ].map(function (sel) { return sel + ' { display: none !important; }'; }).join('\n');

  function addStyle() {
    var parent = document.head || document.documentElement;
    if (!parent) { setTimeout(addStyle, 10); return; }
    if (document.getElementById('__ytAdblockStyle')) return;
    var s = document.createElement('style');
    s.id = '__ytAdblockStyle';
    s.textContent = CSS;
    parent.appendChild(s);
  }
  addStyle();
  document.addEventListener('DOMContentLoaded', addStyle);
})();
