(() => {
  'use strict';
  if (window.__DOLA_DOWN_ANDROID__) return;
  window.__DOLA_DOWN_ANDROID__ = true;

  const TAG = '[Dola Down]';
  const originalFetch = window.fetch.bind(window);
  const originalXhrOpen = XMLHttpRequest.prototype.open;
  const originalXhrSend = XMLHttpRequest.prototype.send;
  const seenFallbacks = new Set();
  const resolvedMedia = [];
  const videoButtons = new WeakMap();

  function clean(value) {
    return String(value || '').replace(/\\u0026/g, '&').replace(/\\\//g, '/').replace(/\\\"/g, '"');
  }

  function readNear(text, index, key) {
    const part = text.slice(Math.max(0, index - 2600), Math.min(text.length, index + 2600));
    const escaped = key.replace(/[.*+?^$()|[\]\\]/g, '\\$&');
    const pattern = new RegExp('"' + escaped + '"\\\\s*:\\\\s*"((?:\\\\\\\\.|[^"\\\\\\\\])*)"', 'i');
    const match = part.match(pattern);
    return match ? clean(match[1]) : '';
  }

  function scanFallbacks(text) {
    if (!text || !text.includes('fallback_api') || !window.DolaAndroid) return;
    const patterns = [
      /"fallback_api"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"/g,
      /fallback_api\\\\":\\\\"(.*?)\\\\"/g
    ];
    for (const pattern of patterns) {
      let match;
      while ((match = pattern.exec(text)) !== null) {
        const fallbackUrl = clean(match[1]);
        if (!fallbackUrl || seenFallbacks.has(fallbackUrl)) continue;
        seenFallbacks.add(fallbackUrl);
        const metadata = {
          fallbackUrl,
          prompt: readNear(text, match.index, 'prompt') || readNear(text, match.index, 'query') || readNear(text, match.index, 'content'),
          poster: readNear(text, match.index, 'poster_url') || readNear(text, match.index, 'poster'),
          vid: readNear(text, match.index, 'vid') || readNear(text, match.index, 'video_id'),
          keySeed: readNear(text, match.index, 'key_seed'),
          messageId: readNear(text, match.index, 'message_id')
        };
        try {
          window.DolaAndroid.onFallbackApi(JSON.stringify(metadata));
          console.info(TAG, 'Captured fallback_api.');
        } catch (error) {
          console.warn(TAG, 'Bridge call failed:', error);
        }
      }
    }
  }

  function observeResponse(response, requestUrl) {
    if (!/chat\\/completion|im\\/chain\\/single|dola\\.com|doubao\\.com/i.test(requestUrl)) return;
    try {
      const clone = response.clone();
      if (clone.body && clone.body.getReader) {
        const reader = clone.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        (async () => {
          while (true) {
            const result = await reader.read();
            if (result.done) break;
            buffer += decoder.decode(result.value, { stream: true });
            scanFallbacks(buffer);
            if (buffer.length > 2000000) buffer = buffer.slice(-500000);
          }
          buffer += decoder.decode();
          scanFallbacks(buffer);
        })().catch(error => console.debug(TAG, 'Stream observer stopped:', error));
      } else {
        clone.text().then(scanFallbacks).catch(() => {});
      }
    } catch (error) {
      console.debug(TAG, 'Could not clone response:', error);
    }
  }

  window.fetch = async function (...args) {
    const response = await originalFetch(...args);
    const requestUrl = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '';
    observeResponse(response, requestUrl);
    return response;
  };

  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__dolaDownUrl = String(url || '');
    return originalXhrOpen.call(this, method, url, ...rest);
  };

  XMLHttpRequest.prototype.send = function (...args) {
    this.addEventListener('load', function () {
      try {
        if (this.__dolaDownUrl && typeof this.responseText === 'string') scanFallbacks(this.responseText);
      } catch (_) {}
    });
    return originalXhrSend.apply(this, args);
  };

  function tosKey(value) {
    if (!value) return '';
    try {
      const path = new URL(value, location.href).pathname;
      const match = path.match(/tos-[a-zA-Z0-9_-]+\\/([a-zA-Z0-9_-]+)/i);
      const segment = match ? match[1] : path.split('/').filter(Boolean).pop() || '';
      return segment.split('~')[0].split('.')[0];
    } catch (_) { return ''; }
  }

  function findMedia(video, index, videos) {
    const keys = [tosKey(video.poster), tosKey(video.currentSrc || video.src)].filter(Boolean);
    for (const key of keys) {
      const found = resolvedMedia.find(media => [media.poster, media.url].some(value => tosKey(value) === key));
      if (found) return found;
    }
    if (videos.length === 1 && resolvedMedia.length === 1) return resolvedMedia[0];
    if (index >= 0 && index < resolvedMedia.length) return resolvedMedia[index];
    return null;
  }

  function makeButton(video) {
    const wrapper = document.createElement('div');
    wrapper.style.cssText = 'display:flex;gap:8px;margin:8px 0 14px;position:relative;z-index:9999;';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = '↓ Download 720P';
    button.style.cssText = 'border:0;border-radius:10px;padding:11px 17px;min-height:42px;background:linear-gradient(135deg,#2563eb,#7c3aed);color:white;font:600 13px system-ui,sans-serif;box-shadow:0 3px 12px rgba(37,99,235,.24);cursor:pointer;';
    button.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      const videos = Array.from(document.querySelectorAll('video'));
      const media = findMedia(video, videos.indexOf(video), videos);
      if (!media) {
        button.textContent = '720P not resolved yet';
        setTimeout(() => button.textContent = '↓ Download 720P', 2200);
        return;
      }
      button.disabled = true;
      button.textContent = 'Starting download…';
      window.DolaAndroid.download(media.url, media.filename);
      setTimeout(() => { button.disabled = false; button.textContent = '↓ Download 720P'; }, 1800);
    });
    wrapper.appendChild(button);
    video.insertAdjacentElement('afterend', wrapper);
    videoButtons.set(video, wrapper);
  }

  function scanVideos() {
    Array.from(document.querySelectorAll('video')).forEach(video => {
      const existing = videoButtons.get(video);
      if (existing && existing.isConnected) return;
      if (existing) existing.remove();
      makeButton(video);
    });
  }

  window.DolaDownloader = {
    scanVideos,
    addResolvedMedia(media) {
      if (!media || !media.url) return;
      if (!resolvedMedia.some(item => item.url === media.url)) resolvedMedia.push(media);
      scanVideos();
      console.info(TAG, '720P media resolved:', media.width + 'x' + media.height);
    }
  };

  new MutationObserver(scanVideos).observe(document.documentElement, { childList: true, subtree: true });
  scanVideos();
  console.info(TAG, 'Fetch/XHR interceptor installed in Dola WebView.');
})();
