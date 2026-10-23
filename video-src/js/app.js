const $ = id => document.getElementById(id);
const video = $('video'), app = $('app');
let items = [], selected = -1, hideTimer = null, shown = true, locked = false;
let fit = 0, brightness = 1, gesture = null;

const ui = [$('toolbar'), $('center'), $('seekArea'), $('bottom')];

function fmt(s) {
  if (!Number.isFinite(s) || s < 0) s = 0;
  let m = Math.floor(s / 60), sec = Math.floor(s % 60);
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

function controls(show) {
  if (locked && show) return;
  shown = show;
  ui.forEach(el => el && el.classList.toggle('hidden-ui', !show));
  if ($('hint')) $('hint').style.opacity = show ? '1' : '0';
  if (show) armHide();
  else clearTimeout(hideTimer);
}

function armHide() {
  clearTimeout(hideTimer);
  if (!locked && !video.paused) hideTimer = setTimeout(() => controls(false), 4500);
}

function playVideo(index) {
  if (index < 0 || index >= items.length) return;
  selected = index;
  const item = items[index];
  video.src = item.url;
  $('title').textContent = item.name;
  video.load();
  video.play().catch(() => {});
  $('playBtn').textContent = 'Ⅱ';
  renderThumbs();
  controls(true);
}

function renderThumbs() {
  const container = $('thumbs');
  container.innerHTML = '';
  items.forEach((it, i) => {
    const el = document.createElement('div');
    el.className = `thumb ${i === selected ? 'selected' : ''}`;
    el.textContent = `▶ ${it.name}`;
    el.onclick = () => playVideo(i);
    container.appendChild(el);
  });
}

// Top X Collapse button (Go back to Main Audio App)
$('collapseBtn').onclick = () => {
  video.pause();
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: 'NAVIGATE_PAGE', target: 'home' }, '*');
  }
};

// Scan / Select Video files
const scanBtn = $('scanVideosBtn');
const picker = $('videoPicker');
if (scanBtn && picker) {
  scanBtn.onclick = () => picker.click();
  picker.onchange = (e) => {
    const files = Array.from(e.target.files);
    if (!files.length) return;
    items = files.map(f => ({ name: f.name, url: URL.createObjectURL(f) }));
    playVideo(0);
  };
}

// Play / Pause
$('playBtn').onclick = () => (video.paused ? video.play() : video.pause());
video.addEventListener('play', () => { $('playBtn').textContent = 'Ⅱ'; armHide(); });
video.addEventListener('pause', () => { $('playBtn').textContent = '▶'; clearTimeout(hideTimer); controls(true); });

// 10s Seek
$('backBtn').onclick = () => { video.currentTime = Math.max(0, video.currentTime - 10); controls(true); };
$('forwardBtn').onclick = () => { video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 10); controls(true); };

// Seekbar Sync
video.addEventListener('timeupdate', () => {
  $('current').textContent = fmt(video.currentTime);
  $('seek').value = video.duration ? (video.currentTime / video.duration) * 1000 : 0;
});
video.addEventListener('loadedmetadata', () => {
  $('duration').textContent = fmt(video.duration);
});
$('seek').oninput = e => {
  if (video.duration) video.currentTime = (video.duration * e.target.value) / 1000;
  controls(true);
};

// Speed Selector
$('speedBtn').onclick = () => { $('speedMenu').hidden = !$('speedMenu').hidden; controls(true); };
$('speedMenu').querySelectorAll('button').forEach(b => {
  b.onclick = () => {
    video.playbackRate = Number(b.dataset.speed);
    $('speedBtn').textContent = b.dataset.speed + '× ▾';
    $('speedMenu').hidden = true;
    controls(true);
  };
});

// Fit & Volume
$('volBtn').onclick = () => {
  video.muted = !video.muted;
  $('volBtn').textContent = video.muted ? '🔇' : '🔊';
  controls(true);
};
$('fitBtn').onclick = () => {
  fit = (fit + 1) % 3;
  video.style.objectFit = ['contain', 'cover', 'fill'][fit];
  $('fitBtn').textContent = ['⛶', '▣', '↔'][fit];
  controls(true);
};
$('repeatBtn').onclick = () => {
  video.currentTime = 0;
  video.play();
  controls(true);
};

// Lock controls
$('lockBtn').onclick = () => {
  locked = true;
  app.classList.add('locked');
  $('unlockTap').hidden = false;
  clearTimeout(hideTimer);
  controls(false);
};
function unlock() {
  locked = false;
  app.classList.remove('locked');
  $('unlockTap').hidden = true;
  controls(true);
}
$('unlockTap').onclick = unlock;

// Tap Stage to Show/Hide Controls
$('stage').addEventListener('click', e => {
  if (e.target.closest('button, input')) return;
  if (locked) { unlock(); return; }
  controls(!shown);
});

// Touch Brightness & Volume Swipes
$('stage').addEventListener('touchstart', e => {
  if (e.touches.length !== 1) return;
  const t = e.touches[0];
  gesture = { x: t.clientX, y: t.clientY, lastY: t.clientY, side: t.clientX < innerWidth / 2 ? 'brightness' : 'volume' };
}, { passive: true });

$('stage').addEventListener('touchmove', e => {
  if (!gesture) return;
  let t = e.touches[0], dy = gesture.lastY - t.clientY;
  gesture.lastY = t.clientY;
  let line = $('levelLine');
  line.classList.add('show');
  line.style.left = gesture.side === 'brightness' ? '24px' : 'auto';
  line.style.right = gesture.side === 'volume' ? '24px' : 'auto';

  if (gesture.side === 'brightness') {
    brightness = Math.max(0.2, Math.min(1, brightness + dy / innerHeight));
    $('brightness').style.opacity = String((1 - brightness) * 0.85);
  } else {
    video.volume = Math.max(0, Math.min(1, video.volume + dy / innerHeight));
    $('volBtn').textContent = video.volume === 0 ? '🔇' : '🔊';
  }
}, { passive: true });

$('stage').addEventListener('touchend', () => {
  gesture = null;
  setTimeout(() => $('levelLine').classList.remove('show'), 400);
});
  
