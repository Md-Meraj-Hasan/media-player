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

// Capacitor templates may generate MainActivity.kt or MainActivity.java and the
// source package may not match capacitor.config.json's appId. Find the actual activity.
let activities = walk(javaRoot, (file, name) => /^MainActivity\.(kt|java)$/.test(name));
let mainActivity = activities[0];
let packageName;

if (mainActivity) {
  const content = fs.readFileSync(mainActivity, 'utf8');
  const match = content.match(/^\s*package\s+([A-Za-z0-9_.]+)\s*;?/m);
  if (!match) throw new Error(`Could not determine package declaration in ${mainActivity}`);
  packageName = match[1];
} else {
  // Some generated projects have no activity source yet. Derive package from Gradle,
  // falling back to Capacitor appId, then create a Kotlin BridgeActivity.
  const gradleFiles = [path.join(appRoot, 'build.gradle'), path.join(appRoot, 'build.gradle.kts')];
  let gradleText = gradleFiles.filter(fs.existsSync).map(f => fs.readFileSync(f, 'utf8')).join('\n');
  const ns = gradleText.match(/namespace\s*(?:=\s*)?["']([^"']+)["']/);
  const appId = JSON.parse(fs.readFileSync(path.join(root, 'capacitor.config.json'), 'utf8')).appId;
  packageName = (ns && ns[1]) || appId || 'com.meraj.mediaplayer';
  const dir = path.join(javaRoot, ...packageName.split('.'));
  fs.mkdirSync(dir, { recursive: true });
  mainActivity = path.join(dir, 'MainActivity.kt');
  fs.writeFileSync(mainActivity, `package ${packageName}\n\nimport android.os.Bundle\nimport com.getcapacitor.BridgeActivity\n\nclass MainActivity : BridgeActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)\n        super.onCreate(savedInstanceState)\n    }\n}\n`);
}

const activityDir = path.dirname(mainActivity);
for (const name of ['MediaPermissionPlugin.kt', 'MediaLibraryScannerPlugin.kt']) {
  const source = path.join(nativeRoot, name);
  if (!fs.existsSync(source)) throw new Error(`Required native plugin source missing: ${source}`);
  let code = fs.readFileSync(source, 'utf8');
  code = code.replace(/^\s*package\s+[A-Za-z0-9_.]+\s*$/m, `package ${packageName}`);
  fs.writeFileSync(path.join(activityDir, name), code);
}

// Register plugins in the actual generated MainActivity, whether Kotlin or Java.
let main = fs.readFileSync(mainActivity, 'utf8');
if (!main.includes('MediaPermissionPlugin::class.java') && !main.includes('MediaPermissionPlugin.class')) {
  if (mainActivity.endsWith('.kt')) {
    const onCreate = /override\s+fun\s+onCreate\s*\(\s*savedInstanceState\s*:\s*Bundle\?\s*\)\s*\{/.exec(main);
    if (onCreate) {
      const insertAt = onCreate.index + onCreate[0].length;
      main = main.slice(0, insertAt) + '\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)' + main.slice(insertAt);
    } else {
      main = main.replace(/class\s+MainActivity\s*:\s*BridgeActivity\(\)\s*\{?\s*\}?\s*$/, `class MainActivity : BridgeActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)\n        super.onCreate(savedInstanceState)\n    }\n}`);
    }
  } else {
    const onCreate = /protected\s+void\s+onCreate\s*\(\s*Bundle\s+savedInstanceState\s*\)\s*\{/.exec(main);
    if (!onCreate) throw new Error(`Could not find onCreate(Bundle) in ${mainActivity}`);
    const insertAt = onCreate.index + onCreate[0].length;
    main = main.slice(0, insertAt) + '\n        registerPlugin(MediaPermissionPlugin.class);\n        registerPlugin(MediaLibraryScannerPlugin.class);' + main.slice(insertAt);
  }
}
if (!main.includes('MediaLibraryScannerPlugin::class.java') && !main.includes('MediaLibraryScannerPlugin.class')) {
  throw new Error(`Could not register native media plugins in ${mainActivity}`);
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
console.log(`Native media plugins copied and registered in ${mainActivity}; media permissions declared.`);
