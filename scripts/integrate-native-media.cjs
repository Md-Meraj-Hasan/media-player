const fs = require('fs');
const path = require('path');

const root = process.cwd();
const androidRoot = path.join(root, 'android');
const appRoot = path.join(androidRoot, 'app');
const javaRoot = path.join(appRoot, 'src', 'main', 'java');
const manifest = path.join(appRoot, 'src', 'main', 'AndroidManifest.xml');
const nativeRoot = path.join(root, 'android-native');

function walk(dir, predicate, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, predicate, out);
    else if (predicate(full, entry.name)) out.push(full);
  }
  return out;
}

if (!fs.existsSync(androidRoot)) throw new Error('Android project not found. Run npx cap add android first.');
if (!fs.existsSync(manifest)) throw new Error(`AndroidManifest.xml not found at ${manifest}`);

let activities = walk(javaRoot, (file, name) => /^MainActivity\.(kt|java)$/.test(name));
let mainActivity = activities[0];
let packageName;

if (mainActivity) {
  const content = fs.readFileSync(mainActivity, 'utf8');
  const match = content.match(/^\s*package\s+([A-Za-z0-9_.]+)\s*;?/m);
  if (!match) throw new Error(`Could not determine package declaration in ${mainActivity}`);
  packageName = match[1];
} else {
  const gradleFiles = [path.join(appRoot, 'build.gradle'), path.join(appRoot, 'build.gradle.kts')];
  let gradleText = gradleFiles.filter(fs.existsSync).map(f => fs.readFileSync(f, 'utf8')).join('\n');
  const ns = gradleText.match(/namespace\s*(?:=\s*)?["']([^"']+)["']/);
  const appId = JSON.parse(fs.readFileSync(path.join(root, 'capacitor.config.json'), 'utf8')).appId;
  packageName = (ns && ns[1]) || appId || 'com.meraj.mediaplayer';
  const dir = path.join(javaRoot, ...packageName.split('.'));
  fs.mkdirSync(dir, { recursive: true });
  mainActivity = path.join(dir, 'MainActivity.kt');
  fs.writeFileSync(mainActivity, `package ${packageName}\n\nimport android.os.Bundle\nimport com.getcapacitor.BridgeActivity\n\nclass MainActivity : BridgeActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)\n        registerPlugin(MerajMediaPlayerPlugin::class.java)\n        super.onCreate(savedInstanceState)\n    }\n}\n`);
}

const activityDir = path.dirname(mainActivity);
// Yahan MerajAudioEffects.kt bhi add kiya gaya hai
for (const name of ['MediaPermissionPlugin.kt', 'MediaLibraryScannerPlugin.kt', 'MerajMediaPlayerPlugin.kt', 'MerajPlaybackService.kt', 'NativeVideoPlayerActivity.kt', 'MerajAudioEffects.kt']) {
  const source = path.join(nativeRoot, name);
  if (!fs.existsSync(source)) throw new Error(`Required native plugin source missing: ${source}`);
  let code = fs.readFileSync(source, 'utf8');
  code = code.replace(/^\s*package\s+[A-Za-z0-9_.]+\s*$/m, `package ${packageName}`);
  fs.writeFileSync(path.join(activityDir, name), code);
}

let main = fs.readFileSync(mainActivity, 'utf8');
if (!main.includes('MediaPermissionPlugin::class.java') && !main.includes('MediaPermissionPlugin.class')) {
  if (mainActivity.endsWith('.kt')) {
    const onCreate = /override\s+fun\s+onCreate\s*\(\s*savedInstanceState\s*:\s*Bundle\?\s*\)\s*\{/.exec(main);
    if (onCreate) {
      const insertAt = onCreate.index + onCreate[0].length;
      main = main.slice(0, insertAt) + '\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)\n        registerPlugin(MerajMediaPlayerPlugin::class.java)' + main.slice(insertAt);
    } else {
      main = main.replace(/class\s+MainActivity\s*:\s*BridgeActivity\(\)\s*\{?\s*\}?\s*$/, `class MainActivity : BridgeActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)\n        registerPlugin(MerajMediaPlayerPlugin::class.java)\n        super.onCreate(savedInstanceState)\n    }\n}`);
    }
  }
}
if (!main.includes('MediaLibraryScannerPlugin::class.java') && !main.includes('MediaLibraryScannerPlugin.class')) {
  throw new Error(`Could not register native media scanner in ${mainActivity}`);
}
if (!main.includes('MerajMediaPlayerPlugin::class.java') && !main.includes('MerajMediaPlayerPlugin.class')) {
  main = main.replace(/registerPlugin\(MediaLibraryScannerPlugin(::class\.java|\.class)\);?/, match => match + (match.includes('::class') ? '\n        registerPlugin(MerajMediaPlayerPlugin::class.java)' : '\n        registerPlugin(MerajMediaPlayerPlugin.class);'));
}
fs.writeFileSync(mainActivity, main);

let xml = fs.readFileSync(manifest, 'utf8');
const permissions = [
  '<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />',
  '<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />',
  '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />',
  '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
  '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />'
];
const missing = permissions.filter(p => !xml.includes(p));
if (missing.length) xml = xml.replace(/<manifest\b[^>]*>/, m => `${m}\n    ${missing.join('\n    ')}`);
fs.writeFileSync(manifest, xml);

function ensureKotlinGradlePlugin() {
  const appGradle = path.join(appRoot, 'build.gradle');
  if (!fs.existsSync(appGradle)) throw new Error('Expected Groovy Gradle file android/app/build.gradle was not generated.');
  let appText = fs.readFileSync(appGradle, 'utf8');
  if (!appText.includes("org.jetbrains.kotlin.android")) {
    if (/plugins\s*\{/.test(appText)) {
      appText = appText.replace(/plugins\s*\{/, match => `${match}\n    id 'org.jetbrains.kotlin.android' version '2.1.0'`);
    } else {
      appText = `plugins { id 'org.jetbrains.kotlin.android' version '2.1.0' }\n${appText}`;
    }
    fs.writeFileSync(appGradle, appText);
  }
}
ensureKotlinGradlePlugin();

const appGradle = path.join(appRoot, 'build.gradle');
let gradleText = fs.readFileSync(appGradle, 'utf8');
if (!gradleText.includes('androidx.media3:media3-exoplayer')) {
  if (!/dependencies\s*\{/.test(gradleText)) throw new Error('Could not find dependencies block in android/app/build.gradle');
  gradleText = gradleText.replace(/dependencies\s*\{/, match => `${match}\n    implementation 'androidx.media3:media3-exoplayer:1.5.1'\n    implementation 'androidx.media3:media3-session:1.5.1'`);
  fs.writeFileSync(appGradle, gradleText);
} else if (!gradleText.includes('androidx.media3:media3-session')) {
  gradleText = gradleText.replace(/dependencies\s*\{/, match => `${match}\n    implementation 'androidx.media3:media3-session:1.5.1'`);
  fs.writeFileSync(appGradle, gradleText);
}
if (!gradleText.includes('androidx.media3:media3-ui')) {
  gradleText = gradleText.replace(/dependencies\s*\{/, match => `${match}\n    implementation 'androidx.media3:media3-ui:1.5.1'`);
  fs.writeFileSync(appGradle, gradleText);
}

xml = fs.readFileSync(manifest, 'utf8');
if (!xml.includes('NativeVideoPlayerActivity')) {
  xml = xml.replace(/<application\b[^>]*>/, match => `${match}\n        <activity android:name=".NativeVideoPlayerActivity" android:exported="false" android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden" />`);
}
if (!xml.includes('MerajPlaybackService')) {
  xml = xml.replace(/<application\b[^>]*>/, match => `${match}\n        <service android:name=".MerajPlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true">\n            <intent-filter>\n                <action android:name="androidx.media3.session.MediaSessionService" />\n            </intent-filter>\n        </service>`);
}
fs.writeFileSync(manifest, xml);

console.log(`Native Media3 audio service/plugin and direct-URI video activity copied and registered.`);
