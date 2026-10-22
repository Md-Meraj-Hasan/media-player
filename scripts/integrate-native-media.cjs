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
const settingsGradle = path.join(androidRoot, 'settings.gradle');
const capacitorSettingsGradle = path.join(androidRoot, 'capacitor.settings.gradle');
const settingsText = [
  fs.existsSync(settingsGradle) ? fs.readFileSync(settingsGradle, 'utf8') : '',
  fs.existsSync(capacitorSettingsGradle) ? fs.readFileSync(capacitorSettingsGradle, 'utf8') : ''
].join('\n');
if (fs.existsSync(settingsGradle) && !settingsText.includes('capacitor-android')) {
  throw new Error('The Android folder is not a correctly generated Capacitor project: :capacitor-android is missing from settings.gradle and capacitor.settings.gradle. Delete android/ and run npx cap add android.');
}
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
for (const name of ['MediaPermissionPlugin.kt', 'MediaLibraryScannerPlugin.kt', 'MerajMediaPlayerPlugin.kt', 'MerajPlaybackService.kt', 'NativeVideoPlayerActivity.kt']) {
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
  } else {
    const onCreate = /(?:@Override\s*)?protected\s+void\s+onCreate\s*\(\s*Bundle\s+savedInstanceState\s*\)\s*\{/.exec(main);
    if (onCreate) {
      const insertAt = onCreate.index + onCreate[0].length;
      main = main.slice(0, insertAt) + '\n        registerPlugin(MediaPermissionPlugin.class);\n        registerPlugin(MediaLibraryScannerPlugin.class);\n        registerPlugin(MerajMediaPlayerPlugin.class);' + main.slice(insertAt);
    } else {
      if (!/import\s+android\.os\.Bundle\s*;/.test(main)) {
        const packageMatch = /package\s+[A-Za-z0-9_.]+\s*;/.exec(main);
        if (packageMatch) {
          const at = packageMatch.index + packageMatch[0].length;
          main = main.slice(0, at) + '\n\nimport android.os.Bundle;' + main.slice(at);
        } else {
          throw new Error(`Could not find package declaration in ${mainActivity}`);
        }
      }
      const classOpen = /class\s+MainActivity\s+extends\s+BridgeActivity\s*\{/.exec(main);
      if (!classOpen) throw new Error(`Could not find MainActivity extends BridgeActivity class in ${mainActivity}`);
      const insertAt = classOpen.index + classOpen[0].length;
      main = main.slice(0, insertAt) + `\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        registerPlugin(MediaPermissionPlugin.class);\n        registerPlugin(MediaLibraryScannerPlugin.class);\n        registerPlugin(MerajMediaPlayerPlugin.class);\n        super.onCreate(savedInstanceState);\n    }\n` + main.slice(insertAt);
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
  '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />'
];
const missing = permissions.filter(p => !xml.includes(p));
if (missing.length) xml = xml.replace(/<manifest\b[^>]*>/, m => `${m}\n    ${missing.join('\n    ')}`);
fs.writeFileSync(manifest, xml);

function ensureKotlinGradlePlugin() {
  const appGradle = path.join(appRoot, 'build.gradle');
  if (!fs.existsSync(appGradle)) {
    throw new Error('Expected Groovy Gradle file android/app/build.gradle was not generated.');
  }

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

function ensureKotlinJvmTarget21() {
  const appGradle = path.join(appRoot, 'build.gradle');
  const appGradleKts = path.join(appRoot, 'build.gradle.kts');
  const file = fs.existsSync(appGradle) ? appGradle : appGradleKts;
  if (!fs.existsSync(file)) throw new Error('Cannot set Kotlin JVM target: app Gradle file is missing.');
  let text = fs.readFileSync(file, 'utf8');
  const marker = '// MERAJ_KOTLIN_JVM_TARGET_21';
  if (text.includes(marker)) return;
  if (file.endsWith('.kts')) {
    text += `\n\n${marker}\ntasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n    kotlinOptions.jvmTarget = \"17\"\n}\n`;
  } else {
    text += `\n\n${marker}\ntasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {\n    kotlinOptions {\n        jvmTarget = '21'\n    }\n}\n`;
  }
  fs.writeFileSync(file, text);
}
ensureKotlinJvmTarget21();

const appGradle = path.join(appRoot, 'build.gradle');
const appGradleKts = path.join(appRoot, 'build.gradle.kts');
const gradleFile = fs.existsSync(appGradle) ? appGradle : appGradleKts;
if (!fs.existsSync(gradleFile)) throw new Error('Could not find android/app/build.gradle or build.gradle.kts');
let gradleText = fs.readFileSync(gradleFile, 'utf8');
const isKotlinDsl = gradleFile.endsWith('.kts');
const depBlock = isKotlinDsl
  ? `dependencies {\n    implementation("androidx.media3:media3-exoplayer:1.5.1")\n    implementation("androidx.media3:media3-session:1.5.1")\n    implementation("androidx.media3:media3-ui:1.5.1")\n}`
  : `dependencies {\n    implementation 'androidx.media3:media3-exoplayer:1.5.1'\n    implementation 'androidx.media3:media3-session:1.5.1'\n    implementation 'androidx.media3:media3-ui:1.5.1'\n}`;
const depLines = [
  ['androidx.media3:media3-exoplayer', isKotlinDsl ? '    implementation("androidx.media3:media3-exoplayer:1.5.1")' : "    implementation 'androidx.media3:media3-exoplayer:1.5.1'"],
  ['androidx.media3:media3-session', isKotlinDsl ? '    implementation("androidx.media3:media3-session:1.5.1")' : "    implementation 'androidx.media3:media3-session:1.5.1'"],
  ['androidx.media3:media3-ui', isKotlinDsl ? '    implementation("androidx.media3:media3-ui:1.5.1")' : "    implementation 'androidx.media3:media3-ui:1.5.1'"]
];
const hasDependenciesBlock = /\bdependencies\s*\{/.test(gradleText);
if (!hasDependenciesBlock) {
  gradleText = gradleText.trimEnd() + '\n\n' + depBlock + '\n';
} else {
  for (const [coordinate, line] of depLines) {
    if (!gradleText.includes(coordinate)) {
      gradleText = gradleText.replace(/\bdependencies\s*\{/, match => `${match}\n${line}`);
    }
  }
}
fs.writeFileSync(gradleFile, gradleText);

xml = fs.readFileSync(manifest, 'utf8');
const servicePermissions = [
  '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
  '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />'
];
for (const permission of servicePermissions) {
  if (!xml.includes(permission)) xml = xml.replace(/<manifest\b[^>]*>/, match => `${match}\n    ${permission}`);
}
if (!xml.includes('NativeVideoPlayerActivity')) {
  xml = xml.replace(/<application\b[^>]*>/, match => `${match}\n        <activity android:name=".NativeVideoPlayerActivity" android:exported="false" android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden" />`);
}
if (!xml.includes('MerajPlaybackService')) {
  xml = xml.replace(/<application\b[^>]*>/, match => `${match}\n        <service android:name=".MerajPlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true">\n            <intent-filter>\n                <action android:name="androidx.media3.session.MediaSessionService" />\n            </intent-filter>\n        </service>`);
}
fs.writeFileSync(manifest, xml);

// --- NEW ASSETS SYNC LOGIC ---
const assetsDir = path.join(appRoot, 'src', 'main', 'assets');
if (!fs.existsSync(assetsDir)) {
  fs.mkdirSync(assetsDir, { recursive: true });
}

const srcHtml = path.join(root, 'video-player.html');
const destHtml = path.join(assetsDir, 'video-player.html');
if (fs.existsSync(srcHtml)) {
  fs.copyFileSync(srcHtml, destHtml);
  console.log('[integrate-media] video-player.html copied to Android assets successfully.');
} else {
  console.error('[integrate-media] Error: video-player.html not found in root directory!');
  process.exit(1);
}

const srcVideoSrc = path.join(root, 'video-src');
const destVideoSrc = path.join(assetsDir, 'video-src');

function copyRecursive(src, dest) {
  if (!fs.existsSync(src)) return;
  const stats = fs.statSync(src);
  if (stats.isDirectory()) {
    if (!fs.existsSync(dest)) {
      fs.mkdirSync(dest, { recursive: true });
    }
    for (const childItemName of fs.readdirSync(src)) {
      copyRecursive(path.join(src, childItemName), path.join(dest, childItemName));
    }
  } else {
    fs.copyFileSync(src, dest);
  }
}

if (fs.existsSync(srcVideoSrc)) {
  copyRecursive(srcVideoSrc, destVideoSrc);
  console.log('[integrate-media] video-src/ folder copied to Android assets successfully.');
} else {
  console.error('[integrate-media] Warning: video-src/ folder not found in root directory.');
}

console.log(`Native Media3 audio service/plugin and direct-URI video activity copied and registered in ${mainActivity}; media permissions and ExoPlayer/session/UI dependencies configured.`);
