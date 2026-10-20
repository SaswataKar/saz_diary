// Turns the freshly generated Capacitor Android project into Saz's Diary:
// icons, colours, keyboard behaviour, version number and release signing.
// Run from the app/ folder after `npx cap add android`.
import fs from "node:fs";
import path from "node:path";

const android = path.resolve("android");
const res = path.join(android, "app/src/main/res");
const must = (p) => { if (!fs.existsSync(p)) throw new Error("Missing " + p + " (run `npx cap add android` first)"); return p; };
must(res);

// 1. Launcher icon: a magical open book. Layered ("adaptive") icon for Android 8+, with a
//    single-colour layer for themed icons on Android 13+, and classic PNGs for older phones.
for (const d of ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]) {
  const dir = path.join(res, "mipmap-" + d);
  fs.mkdirSync(dir, { recursive: true });
  fs.copyFileSync(`res/icon-${d}.png`, path.join(dir, "ic_launcher.png"));
  fs.copyFileSync(`res/icon-round-${d}.png`, path.join(dir, "ic_launcher_round.png"));
  fs.copyFileSync(`res/ic-fg-${d}.png`, path.join(dir, "ic_launcher_foreground.png"));
  fs.copyFileSync(`res/ic-bg-${d}.png`, path.join(dir, "ic_launcher_background.png"));
  fs.copyFileSync(`res/ic-mono-${d}.png`, path.join(dir, "ic_launcher_monochrome.png"));
}
const anydpi = path.join(res, "mipmap-anydpi-v26");
fs.mkdirSync(anydpi, { recursive: true });
const adaptive = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
`;
fs.writeFileSync(path.join(anydpi, "ic_launcher.xml"), adaptive);
fs.writeFileSync(path.join(anydpi, "ic_launcher_round.xml"), adaptive);
for (const dir of fs.readdirSync(res)) {                 // remove the template's own background colour/drawable
  for (const f of ["ic_launcher_background.xml"]) fs.rmSync(path.join(res, dir, f), { force: true });
}

// 1b. App name under the icon
const stringsPath = path.join(res, "values/strings.xml");
if (fs.existsSync(stringsPath)) {
  let st = fs.readFileSync(stringsPath, "utf8");
  st = st.replace(/<string name="app_name">[^<]*<\/string>/, '<string name="app_name">Diary</string>')
         .replace(/<string name="title_activity_main">[^<]*<\/string>/, '<string name="title_activity_main">Diary</string>');
  fs.writeFileSync(stringsPath, st);
}

// 2. Colours, splash screen and window background in the diary's leather green
fs.mkdirSync(path.join(res, "values"), { recursive: true });
fs.writeFileSync(path.join(res, "values/diary_colors.xml"),
`<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="diary_bg">#1F3A33</color>
</resources>
`);
for (const dir of fs.readdirSync(res)) {
  if (!dir.startsWith("drawable")) continue;
  for (const f of ["splash.png", "splash.xml"]) fs.rmSync(path.join(res, dir, f), { force: true });
}
fs.mkdirSync(path.join(res, "drawable"), { recursive: true });
fs.copyFileSync("res/splash-icon.png", path.join(res, "drawable/diary_splash_icon.png"));
fs.writeFileSync(path.join(res, "drawable/splash.xml"),
`<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/diary_bg" />
    <item android:gravity="center"><bitmap android:src="@drawable/diary_splash_icon" android:gravity="center" /></item>
</layer-list>
`);
const stylesPath = must(path.join(res, "values/styles.xml"));
let styles = fs.readFileSync(stylesPath, "utf8");
const themeItems = `
        <item name="android:windowBackground">@color/diary_bg</item>
        <item name="android:statusBarColor">@color/diary_bg</item>
        <item name="android:navigationBarColor">@color/diary_bg</item>
        <item name="android:windowLightStatusBar">false</item>`;
styles = styles.replace(/(<style name="AppTheme\.NoActionBar"[^>]*>)/, "$1" + themeItems);
if (!styles.includes("diary_bg")) throw new Error("Couldn't find AppTheme.NoActionBar in styles.xml");
fs.writeFileSync(stylesPath, styles);

// 3. Manifest: resize for the keyboard so the page stays visible while typing
const manifestPath = must(path.join(android, "app/src/main/AndroidManifest.xml"));
let manifest = fs.readFileSync(manifestPath, "utf8");
if (!manifest.includes("windowSoftInputMode")) {
  manifest = manifest.replace(/<activity(\s)/, '<activity android:windowSoftInputMode="adjustResize"$1');
}
if (!manifest.includes("RECORD_AUDIO")) {
  manifest = manifest.replace("</manifest>", '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n</manifest>');
}
fs.writeFileSync(manifestPath, manifest);

// 4. Google sign-in for Drive backup (native code lives in app/native)
const javaDir = path.join(android, "app/src/main/java/com/saz/diary");
for (const f of ["DriveAuthPlugin.java", "GuardPlugin.java", "MainActivity.java"]) fs.copyFileSync(path.join("native", f), path.join(javaDir, f));

// 5. Version number from the build run, and release signing from environment variables
const gradlePath = must(path.join(android, "app/build.gradle"));
let gradle = fs.readFileSync(gradlePath, "utf8");
const run = parseInt(process.env.BUILD_NUMBER || "1", 10);
gradle = gradle.replace(/versionCode \d+/, "versionCode " + run).replace(/versionName "[^"]*"/, `versionName "1.${run}"`);
if (process.env.KS_PATH) {
  gradle += `
android {
    signingConfigs {
        diary {
            storeFile file(System.getenv("KS_PATH"))
            storePassword System.getenv("KS_PASS")
            keyAlias "saz"
            keyPassword System.getenv("KS_PASS")
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.diary
            minifyEnabled false
        }
    }
}
`;
}
gradle += `
dependencies {
    implementation "com.google.android.gms:play-services-auth:21.3.0"
    implementation "androidx.biometric:biometric:1.1.0"
}
`;
fs.writeFileSync(gradlePath, gradle);
console.log("Prepared Saz's Diary: version 1." + run + (process.env.KS_PATH ? ", signed" : ", unsigned"));
