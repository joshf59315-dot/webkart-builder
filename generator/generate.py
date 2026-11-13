#!/usr/bin/env python3
"""Generates a signed-ready Android WebView project from Web Kart settings."""
import base64, json, os, re, sys

cfg = json.load(open(sys.argv[1]))
out = sys.argv[2]
here = os.path.dirname(os.path.abspath(__file__))
pkg = cfg["packageName"]
if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", pkg):
    sys.exit("bad package name")
hexcolor = re.compile(r"#[0-9a-fA-F]{6}")
for key in ("primaryColor", "splashBg"):
    if not hexcolor.fullmatch(cfg[key]):
        sys.exit("bad color")
loader = cfg["loaderStyle"] if cfg["loaderStyle"] in ("spinner", "bar", "dots") else "spinner"

def write(path, content, mode="w"):
    full = os.path.join(out, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, mode) as f:
        f.write(content)

def image(data_url):
    m = re.match(r"data:image/(png|jpeg|webp);base64,(.*)$", data_url, re.S)
    if not m:
        sys.exit("bad image")
    return ("jpg" if m.group(1) == "jpeg" else m.group(1)), base64.b64decode(m.group(2))

def xml_text(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if s[:1] in ("@", "?"):
        s = "\\" + s
    return s

write("settings.gradle", """pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = 'webapp'
include ':app'
""")
write("build.gradle", "plugins { id 'com.android.application' version '8.5.2' apply false }\n")
write("gradle.properties", "org.gradle.jvmargs=-Xmx3g\nandroid.useAndroidX=false\n")
write("app/build.gradle", """plugins { id 'com.android.application' }
android {
    namespace '%(pkg)s'
    compileSdk 34
    defaultConfig {
        applicationId '%(pkg)s'
        minSdk 23
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }
    signingConfigs {
        release {
            storeFile file('../release.jks')
            storePassword System.getenv('KS_PASS')
            keyAlias 'app'
            keyPassword System.getenv('KS_PASS')
        }
    }
    buildTypes {
        release {
            minifyEnabled false
            signingConfig signingConfigs.release
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
""" % {"pkg": pkg})

allowed = re.compile(r"android\.permission\.[A-Z_]+")
perms = [p for p in cfg.get("manifestPermissions", []) if allowed.fullmatch(p)]
for base in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"):
    if base not in perms:
        perms.insert(0, base)
features = ""
if "android.permission.RECORD_AUDIO" in perms and "android.permission.MODIFY_AUDIO_SETTINGS" not in perms:
    perms.append("android.permission.MODIFY_AUDIO_SETTINGS")  # WebView mic (WebRTC) needs this
if "android.permission.RECORD_AUDIO" in perms:
    features += '    <uses-feature android:name="android.hardware.microphone" android:required="false" />\n'
perm_xml = "\n".join('    <uses-permission android:name="%s" />' % p for p in perms)
if "android.permission.CAMERA" in perms:
    features += '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n'
write("app/src/main/AndroidManifest.xml", """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
%s
%s    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="true"
        android:hardwareAccelerated="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""" % (perm_xml, features))

write("app/src/main/res/values/strings.xml", '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="app_name">%s</string>\n</resources>\n' % xml_text(cfg["appName"]))
write("app/src/main/res/values/colors.xml", '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="primary">%s</color>\n    <color name="splash_bg">%s</color>\n</resources>\n' % (cfg["primaryColor"], cfg["splashBg"]))
write("app/src/main/res/values/styles.xml", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:colorPrimary">@color/primary</item>
        <item name="android:colorAccent">@color/primary</item>
        <item name="android:statusBarColor">@color/primary</item>
        <item name="android:windowBackground">@color/splash_bg</item>
    </style>
</resources>
""")

ext, data = image(cfg["iconDataUrl"])
write("app/src/main/res/mipmap-xxxhdpi/ic_launcher." + ext, data, "wb")
has_splash = bool(cfg.get("splashDataUrl"))
if has_splash:
    ext, data = image(cfg["splashDataUrl"])
    write("app/src/main/res/drawable-nodpi/splash." + ext, data, "wb")

java = open(os.path.join(here, "MainActivity.java")).read()
java = (java.replace("__PACKAGE__", pkg)
        .replace("__URL__", json.dumps(cfg["url"]))
        .replace("__APP_NAME__", json.dumps(cfg["appName"]))
        .replace("__PRIMARY__", cfg["primaryColor"])
        .replace("__SPLASH_BG__", cfg["splashBg"])
        .replace("__LOADER__", loader)
        .replace("__SHOW_PROGRESS__", "true" if cfg.get("showProgress") else "false")
        .replace("__HAS_SPLASH__", "true" if has_splash else "false"))
write("app/src/main/java/" + pkg.replace(".", "/") + "/MainActivity.java", java)

formats = cfg.get("outputFormats") or ["apk"]
tasks = []
if "apk" in formats: tasks.append("assembleRelease")
if "aab" in formats: tasks.append("bundleRelease")
write("gradle_tasks.txt", " ".join(tasks))
name = re.sub(r"[^A-Za-z0-9]+", "-", cfg["appName"]).strip("-") or "app"
write("artifact_name.txt", name[:40])
print("Generated", pkg, tasks)
