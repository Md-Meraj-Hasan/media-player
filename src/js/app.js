import { Capacitor, registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { ScreenOrientation } from '@capacitor/screen-orientation';

const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
const VideoPermissionBridge = registerPlugin('MediaPermission');
const VideoScannerBridge = registerPlugin('MediaLibraryScanner');

let tracks = [], current = -1, favs = new Set(), historyPages = ['home'];
const audio = $('#audio');

// VIDEO IFRAME BRIDGE
const videoFrameBridge = document.getElementById('videoPlayerFrame');
window.addEventListener('message', async (event) => {
  if (!videoFrameBridge || event.source !== videoFrameBridge.contentWindow) return;
  const message = event.data;
  if (message?.type === 'NAVIGATE_PAGE') { page(message.target || 'home'); return; }
  if (!message || message.bridge !== 'meraj-video-native' || !message.requestId) return;
  
  const reply = { bridge: 'meraj-video-native-result', requestId: message.requestId };
  try {
    switch (message.action) {
      case 'permission': reply.result = await VideoPermissionBridge.requestIfNeeded(); break;
      case 'scan': reply.result = await VideoScannerBridge.scan({ type: message.payload?.type || 'video' }); break;
      case 'launchVideo': reply.result = await VideoScannerBridge.launchVideo(message.payload || {}); break;
      case 'getThumbnail': reply.result = await VideoScannerBridge.getThumbnail(message.payload || {}); break;
      case 'shareMedia': reply.result = await VideoScannerBridge.shareMedia(message.payload || {}); break;
      case 'orientationLock': await ScreenOrientation.lock({ orientation: message.payload?.orientation || 'landscape' }); reply.result = { locked: true }; break;
      case 'orientationUnlock': await ScreenOrientation.unlock(); reply.result = { locked: false }; break;
      default: throw new Error('Unsupported action: ' + message.action);
    }
    reply.ok = true;
  } catch (error) {
    reply.ok = false;
    reply.error = String(error?.message || error || 'Native operation failed');
  }
  event.source.postMessage(reply, '*');
});

// NAVIGATION
function page(id) {
  $$('.page').forEach(p => p.classList.toggle('active', p.id === id));
  $$('.nav').forEach(b => b.classList.toggle('active', b.dataset.page === id));
  document.body.classList.toggle('video-mode', id === 'videoPlayer');
  window.scrollTo(0, 0);
}
$$('[data-page]').forEach(b => b.addEventListener('click', () => page(b.dataset.page)));
