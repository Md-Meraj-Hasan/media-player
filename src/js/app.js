window.addEventListener('message', async (event) => {
  if (!videoFrameBridge || event.source !== videoFrameBridge.contentWindow) return;
  const message = event.data;
  if (message?.type === 'meraj-video-switch-audio') { page('home'); return; }
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
      case 'exitApp': await App.exitApp(); reply.result = { exited: true }; break;
      default: throw new Error('Unsupported video native action: ' + message.action);
    }
    reply.ok = true;
  } catch (error) {
    reply.ok = false;
    reply.error = String(error?.message || error || 'Native operation failed');
  }
  event.source.postMessage(reply, '*');
});
