const fs = require('fs');
const path = require('path');
const root = process.cwd();
const pkg = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'meraj', 'mediaplayer');
fs.mkdirSync(pkg, { recursive: true });
for (const name of ['MediaPermissionPlugin.kt', 'MediaLibraryScannerPlugin.kt']) {
  fs.copyFileSync(path.join(root, 'android-native', name), path.join(pkg, name));
}
const mainActivity = path.join(pkg, 'MainActivity.kt');
if (!fs.existsSync(mainActivity)) throw new Error(`MainActivity.kt not found at ${mainActivity}`);
let main = fs.readFileSync(mainActivity, 'utf8');
if (!main.includes('MediaPermissionPlugin::class.java')) {
  main = main.replace(/class MainActivity\s*:\s*BridgeActivity\(\)\s*(?:\{\s*\})?/, `class MainActivity : BridgeActivity() {\n    override fun onCreate(savedInstanceState: android.os.Bundle?) {\n        registerPlugin(MediaPermissionPlugin::class.java)\n        registerPlugin(MediaLibraryScannerPlugin::class.java)\n        super.onCreate(savedInstanceState)\n    }\n}`);
}
if (!main.includes('MediaLibraryScannerPlugin::class.java')) throw new Error('Could not register native media plugins in MainActivity.kt');
fs.writeFileSync(mainActivity, main);
const manifest = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
let xml = fs.readFileSync(manifest, 'utf8');
const permissions = [
  '<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />',
  '<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />',
  '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />'
];
const missing = permissions.filter(p => !xml.includes(p));
if (missing.length) xml = xml.replace(/<manifest\b[^>]*>/, m => `${m}\n    ${missing.join('\n    ')}`);
fs.writeFileSync(manifest, xml);
console.log('Native media plugins copied, registered, and Android media permissions declared.');
