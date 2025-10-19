(function(){
  try {
    if (typeof window !== 'undefined') {
      if (window.__bubbleBridgeInstalled) { try { console.info('[BubbleUnits] Bridge already installed'); } catch(e) {} ; return; }
      window.__bubbleBridgeInstalled = true;
    }
  } catch(e) {}
})();
// Define fallback for __bubbleSave_send if injection failed or only a stub exists
if (typeof window.__bubbleSave_send !== 'function' || (window.__bubbleSave_send && window.__bubbleSave_send.__bubbleStub)) {
  console.info('[BubbleUnits] Creating fallback for __bubbleSave_send');
  window.__bubbleSave_send = function(data) {
    console.info('[BubbleUnits] __bubbleSave_send fallback called with data length: ' + (data ? data.length : 0));
    // Use hash-based fallback mechanism
    try {
      if (!data) return;
      var parts = String(data||'').split('\t');
      var command = parts[0];
      // For BEGIN command
      if (command === 'BEGIN') {
        var id = parts[1] || '';
        var kind = parts[2] || 'file';
        var name = parts[3] || 'snapshot';
        window.__bubbleExportSession = { id: id, kind: kind, name: name, chunks: [] };
        return;
      }
      // For CHUNK command
      if (command === 'CHUNK') {
        if (!window.__bubbleExportSession) return;
        var chunk = parts[2] || '';
        window.__bubbleExportSession.chunks.push(chunk);
        return;
      }
      // For END command
      if (command === 'END') {
        if (!window.__bubbleExportSession) return;
        var session = window.__bubbleExportSession;
        var b64 = session.chunks.join('');
        location.hash = '#__buSave=' + encodeURIComponent(session.kind + '|' + session.name) + '&d=' + encodeURIComponent(b64) + '&t=' + Date.now();
        window.__bubbleExportSession = null;
        return;
      }
      // For single-shot export
      var kind = parts[0] || 'file';
      var name = parts[1] || 'snapshot';
      var b64 = parts.slice(2).join('\t');
      location.hash = '#__buSave=' + encodeURIComponent(kind + '|' + name) + '&d=' + encodeURIComponent(b64) + '&t=' + Date.now();
    } catch (e) {
      try { console.error('[BubbleUnits] Fallback __bubbleSave_send error:', e); } catch(ex) {}
    }
  };
}

// Define a unified notify that forwards to the bridge function, looking it up dynamically each time
try {
  (function(){
    window.__bubbleNotify = function(msg){
      try {
        // Look up the bridge dynamically to ensure we get the real JSQuery bridge, not the early stub
        var bridge = (typeof window !== 'undefined' && typeof window.__bubbleNotify_bridge === 'function' && !window.__bubbleNotify_bridge.__bubbleStub) ? window.__bubbleNotify_bridge : null;
        if (bridge) { bridge(String(msg == null ? '' : msg)); }
        else { console.info('[BubbleUnits] notify: bridge not ready - ' + (msg || '')); }
      } catch (e) { try { console.error(e); } catch(ex){} }
    };
  })();
} catch (e) {}
// Mirror console.* into the IDE log via console bridge, preserving original behavior
try {
  (function(){
    if (!window.__bubbleConsoleHooked) {
      window.__bubbleConsoleHooked = true;
      var orig = { log: console.log, info: console.info, warn: console.warn, error: console.error, debug: console.debug };
      function asString(v){ try{ if(v==null) return String(v); if(typeof v==='string') return v; if(typeof v==='object') return JSON.stringify(v); return String(v);}catch(e){ try{return String(v);}catch(e2){return '[unprintable]';} } }
      function fwd(level, args){ try{ var b=(typeof window !== 'undefined' && typeof window.__bubbleConsole_bridge === 'function' && !window.__bubbleConsole_bridge.__bubbleStub)?window.__bubbleConsole_bridge:null; if(!b) return; var msg = Array.prototype.map.call(args, asString).join(' '); b(level+'\t'+msg); } catch(e){} }
      ['log','info','warn','error','debug'].forEach(function(k){ try{ console[k] = function(){ try{ orig[k] && orig[k].apply(console, arguments); }catch(e){}; fwd(k, arguments); }; } catch(e){} });
    }
  })();
} catch (e) {}
try { console.info('[BubbleUnits] Bridge injected (save, notify, console)'); } catch(e) {}
try {
  if (typeof window !== 'undefined') {
    window.__bubbleBridgeReadyCount = (window.__bubbleBridgeReadyCount||0) + 1;
    if (!window.__bubbleBridgeReadyNotified) {
      window.__bubbleBridgeReadyNotified = true;
      if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('BubbleUnits bridge ready'); }
    }
  }
} catch(e) {}
(function(){
  // Provide a sender that dynamically looks up the bridge to avoid hitting early stubs
  function __bubbleSendRaw(s){
    try {
      // Look up the bridge dynamically to ensure we get the real JSQuery bridge, not the early stub
      var bridge = (typeof window !== 'undefined' && typeof window.__bubbleSave_send === 'function' && !window.__bubbleSave_send.__bubbleStub) ? window.__bubbleSave_send : null;
      if (bridge) { bridge(s); return true; }
    } catch(e) { console.error(e); }
    return false;
  }
  // Wrapper that chunks payloads to avoid JCEF query size limits
  window.__bubbleSave = (function(){
    var MAX = 24000; // conservative chunk size
    function sendRaw(s){ try{ if(!__bubbleSendRaw(s)){ console.info('[BubbleUnits] save bridge not ready'); try{ if (typeof window !== 'undefined' && typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Export bridge is not ready — try Reload (toolbar)'); } } catch(e2){} } }catch(e){ console.error(e);} }
    function sendChunked(kind,name,b64){
      try{
        var id = Math.random().toString(36).slice(2);
        sendRaw('BEGIN\t'+id+'\t'+kind+'\t'+name);
        for(var i=0;i<b64.length;i+=MAX){
          var part = b64.slice(i, i+MAX);
          sendRaw('CHUNK\t'+id+'\t'+part);
        }
        sendRaw('END\t'+id);
      }catch(e){ console.error('chunked-save failed', e); }
    }
    return function(payload){
      try{
        if(typeof payload !== 'string') return;
        if(payload.length <= MAX){ sendRaw(payload); return; }
        var first = payload.indexOf('\t'); if(first<0) return;
        var second = payload.indexOf('\t', first+1); if(second<0) return;
        var kind = payload.slice(0, first);
        var name = payload.slice(first+1, second);
        var b64 = payload.slice(second+1);
        if(b64.length <= MAX){ sendRaw(payload); return; }
        sendChunked(kind, name, b64);
      }catch(e){ console.error(e); }
    }
  })();
  
  // Create the __bubble object expected by bubbles.js
  // Note: This is replaced by a more comprehensive implementation below
  function ts(){ var d=new Date(); return d.toISOString().replace(/[:.]/g,'-'); }
  function serializeSvg(){
    var el = document.querySelector('#bubbles svg') || document.querySelector('svg');
    if(!el) return null;
    var copy = el.cloneNode(true);
    try {
      var r = getComputedStyle(document.documentElement);
      var css = '.bubbles circle.success{fill:'+r.getPropertyValue('--color-success').trim()+';}' +
                '.bubbles circle.failed{fill:'+r.getPropertyValue('--color-failed').trim()+';}' +
                '.bubbles circle.error{fill:'+r.getPropertyValue('--color-error').trim()+';}' +
                '.bubbles circle.skipped{fill:'+r.getPropertyValue('--color-warning').trim()+';}';
      var styleEl=document.createElement('style'); styleEl.textContent=css; copy.insertBefore(styleEl, copy.firstChild);
    } catch(e){}
    var s = new XMLSerializer().serializeToString(copy);
    if(!/^<svg[\s\S]*xmlns=/.test(s)) s = s.replace('<svg','<svg xmlns="http://www.w3.org/2000/svg"');
    return s;
  }
  function toPng(svgText){
    return new Promise(function(resolve,reject){
      try{
        var img = new Image();
        img.onload = function(){
          var c=document.createElement('canvas'); c.width=img.width; c.height=img.height;
          var ctx=c.getContext('2d'); ctx.drawImage(img,0,0);
          resolve(c.toDataURL('image/png').split(',')[1]);
        };
        img.onerror=reject;
        img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svgText);
      } catch(e){ reject(e); }
    });
  }
  function buNotify(msg){
    try { if (typeof window.__bubbleNotify==='function') { window.__bubbleNotify(msg); return; } } catch(e){}
    try { location.hash = '#__buNotify=' + encodeURIComponent(String(msg||'')) + '&t=' + Date.now(); } catch(e){}
  }
  function buSend(kind,name,b64){
    try { if (typeof window.__bubbleSave==='function') { window.__bubbleSave(kind+'\t'+name+'\t'+b64); return; } } catch(e) {}
    try { location.hash = '#__buSave=' + encodeURIComponent(kind+'|'+name) + '&d=' + encodeURIComponent(b64) + '&t=' + Date.now(); } catch(e){}
  }
  // Provide a stable window.__bubble API used by bubbles.js
  try {
    window.__bubble = {};
    // Core save function that handles both images and other content
    window.__bubble.save = function(kind, name, b64){
      try { buSend(String(kind||'file'), String(name||'snapshot'), String(b64||'')); return Promise.resolve('OK'); } catch(e){ return Promise.reject(e); }
    };
    // Export helpers used by bubbles.js
    window.__bubble.exportSvg = function(name, b64){ return window.__bubble.save('svg', name, b64); };
    window.__bubble.exportJson = function(name, b64){ return window.__bubble.save('json', name, b64); };
    window.__bubble.exportPng = function(name, b64){ return window.__bubble.save('png', name, b64); };
    // Add a notify function to ensure notifications work through the bubbles.js API
    window.__bubble.notify = function(msg) {
      try { 
        if (typeof window.__bubbleNotify === 'function') {
          window.__bubbleNotify(msg);
          return Promise.resolve('OK');
        } else {
          buNotify(msg);
          return Promise.resolve('OK (fallback)');
        }
      } catch(e){ 
        console.error('[BubbleUnits] notify failed:', e);
        return Promise.reject(e); 
      }
    };
    // Log that the API is ready
    console.info('[BubbleUnits] window.__bubble API initialized successfully');
  } catch(e) { try { console.error('[BubbleUnits] Failed to initialize window.__bubble API:', e); } catch(ex) {} }
  // Ensure a visible disabled style for export buttons
  (function ensureDisabledStyles(){
    try {
      if (!document.getElementById('bu-disabled-style')) {
        var st = document.createElement('style');
        st.id = 'bu-disabled-style';
        st.textContent = '\n.bu-disabled, #svg_download_link[disabled], #png_download_link[disabled], #json_report_download_link[disabled] {\n  opacity: 0.55 !important;\n  filter: grayscale(100%) !important;\n  cursor: default !important;\n  pointer-events: none !important;\n}\n#svg_download_link.bu-disabled::after, #png_download_link.bu-disabled::after, #json_report_download_link.bu-disabled::after {\n  content: " \u2713";\n  font-weight: 600;\n}\n';
        (document.head || document.documentElement).appendChild(st);
      }
    } catch(_) {}
  })();
  function bindIfPresent(el, listener) {
    try {
      if (!el) return;
      var m = el.getAttribute('data-bu-bound');
      if (m === '1') return;
      el.setAttribute('data-bu-bound','1');
      el.addEventListener('click', listener, true);
    } catch(e) { try { console.error(e); } catch(ex) {} }
  }
  function tryBindButtons(){
    try {
      var svgBtn = document.getElementById('svg_download_link');
      var pngBtn = document.getElementById('png_download_link');
      var jsonBtn = document.getElementById('json_report_download_link');
      bindIfPresent(svgBtn, function(ev){
        ev.preventDefault();
        try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Exporting SVG…'); } } catch(e) {}
        var s = serializeSvg();
        if (!s) {
          try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('No SVG found to export yet. Generate the chart first.'); } } catch(e) {}
          return;
        }
        var b = btoa(unescape(encodeURIComponent(s)));
        try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Saving: phpunit-bubble-report.svg'); } } catch(e) {}
        buSend('svg','phpunit-bubble-report.svg', b);
        try { svgBtn.setAttribute('disabled','true'); svgBtn.classList.add('bu-disabled'); svgBtn.title = 'Exported'; if (svgBtn && svgBtn.style) svgBtn.style.pointerEvents = 'none'; } catch(e) {}
      });
      bindIfPresent(pngBtn, async function(ev){
        ev.preventDefault();
        var s = serializeSvg();
        if (!s) {
          try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('No SVG found to export yet. Generate the chart first.'); } } catch(e) {}
          return;
        }
        try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Exporting PNG…'); } } catch(e) {}
        var b = await toPng(s);
        try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Saving: phpunit-bubble-report.png'); } } catch(e) {}
        buSend('png','phpunit-bubble-report.png', b);
        try { pngBtn.setAttribute('disabled','true'); pngBtn.classList.add('bu-disabled'); pngBtn.title = 'Exported'; if (pngBtn && pngBtn.style) pngBtn.style.pointerEvents = 'none'; } catch(e) {}
      });
      bindIfPresent(jsonBtn, function(ev){
        ev.preventDefault();
        var data = {circles: Array.from(document.querySelectorAll('#bubbles svg circle')).map(function(c){ return { cx:c.getAttribute('cx'), cy:c.getAttribute('cy'), r:c.getAttribute('r'), cls:c.getAttribute('class') }; })};
        var txt = JSON.stringify(data, null, 2);
        var b = btoa(unescape(encodeURIComponent(txt)));
        try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Saving: phpunit-bubble-report.json'); } } catch(e) {}
        buSend('json','phpunit-bubble-report.json', b);
        try { jsonBtn.setAttribute('disabled','true'); jsonBtn.classList.add('bu-disabled'); jsonBtn.title = 'Exported'; } catch(e) {}
      });
    } catch(e) { try { console.error(e); } catch(ex) {} }
  }
  function onReady(){
    tryBindButtons();
    try {
      if (window.__buButtonsObserver) return;
      var obs = new MutationObserver(function(){ tryBindButtons(); });
      window.__buButtonsObserver = obs;
      var root = document.body || document.documentElement;
      if (root) obs.observe(root, {childList:true, subtree:true});
    } catch(e) { try { console.error(e); } catch(ex) {} }
  }
  if (document.readyState === 'complete' || document.readyState === 'interactive') onReady(); else document.addEventListener('DOMContentLoaded', onReady);
})();
