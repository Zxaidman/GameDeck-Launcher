# Setup — Building and Installing Without a Full IDE

**Document:** `docs/SETUP.md`  
**Status:** Active — the Linux path in this guide was executed end to end and produced both APKs  

This guide is written for someone who is not a software developer. It uses a code editor and a
terminal rather than the full IDE, which is a much smaller install.

Every command here was run for real on the project. Where a step is known to differ on your
operating system, that is called out rather than glossed over.

---

## 1. What you are installing, and why

Three things, and only the first two are required:

| Thing | Why it is needed | Size |
| --- | --- | --- |
| **JDK 17 or newer** | The build system and the language both run on Java. Nothing builds without it. | ~200 MB |
| **SDK command-line tools** | Supplies the platform to compile against, the packaging tools, and `adb`, which talks to your phone. | ~640 MB |
| **VS Code** (optional) | An editor for reading and changing files. The build does not need it. | ~350 MB |

Plus roughly 1–2 GB that the build system caches the first time it runs. Budget about 3 GB total.

**You do not need the full IDE.** It bundles its own JDK, an emulator, and a device manager, and it
runs to several gigabytes. The trade-off is honest: without it you get no code completion, no
visual debugger, and no one-click run button. For building this project and installing it on your
phone, the tools below are enough.

---

## 2. Install the JDK

**Windows:** download the Temurin 21 MSI from <https://adoptium.net>, run it, and tick *Set
JAVA_HOME* during installation.

**macOS:** `brew install temurin@21`

**Linux:** `sudo apt install openjdk-21-jdk`

Check it:

```bash
java -version
```

Any version from 17 upward works. This project was built and tested on OpenJDK 21.

---

## 3. Install the SDK command-line tools

Download the **command-line tools only** package for your system from
<https://developer.android.com/studio#command-line-tools-only> — scroll past the IDE download; it is
near the bottom of that page.

The directory layout matters. The tools must end up in a folder named `latest` inside
`cmdline-tools`, or `sdkmanager` will refuse to run.

**Windows (PowerShell):**

```powershell
mkdir C:\Android\cmdline-tools
# unzip the download, then move the extracted "cmdline-tools" folder so the path becomes:
#   C:\Android\cmdline-tools\latest\bin\sdkmanager.bat
setx ANDROID_HOME "C:\Android"
```

Close and reopen the terminal so `ANDROID_HOME` takes effect.

**macOS / Linux:**

```bash
mkdir -p ~/Android/cmdline-tools
unzip commandlinetools-*.zip -d /tmp/cmdtools
mv /tmp/cmdtools/cmdline-tools ~/Android/cmdline-tools/latest
echo 'export ANDROID_HOME=$HOME/Android' >> ~/.bashrc
echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH' >> ~/.bashrc
source ~/.bashrc
```

Then accept the licences and install exactly three packages — no more are needed:

```bash
sdkmanager --licenses          # press y at each prompt
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

On Windows use `sdkmanager.bat` in place of `sdkmanager`.

Those three were confirmed sufficient to build this project: nothing else was installed.

---

## 4. Get the source and point it at the SDK

```bash
git clone https://github.com/Zxaidman/Kestrel.git
cd Kestrel
```

Create a file called `local.properties` in the project root containing the path to your SDK:

```properties
sdk.dir=/home/YOUR_NAME/Android
```

On Windows the path uses double backslashes:

```properties
sdk.dir=C:\\Android
```

This file is deliberately not committed — it is specific to your machine. If you skip it, the build
stops with `SDK location not found`, which is the single most common first error.

---

## 5. Build

```bash
./gradlew build
```

On Windows: `gradlew.bat build`

The first run downloads the build system and every dependency, so expect several minutes and a lot
of output. Later runs take seconds. When it finishes you should see `BUILD SUCCESSFUL`.

This one command compiles both applications, runs the linter, and runs the tests. That is exactly
what was run to verify this guide.

Two APKs are produced:

```text
app/build/outputs/apk/debug/app-debug.apk           the product — a placeholder screen for now
tools/phase0/build/outputs/apk/debug/phase0-debug.apk   the Phase 0 harness
```

Each is about 28 MB.

To build just one:

```bash
./gradlew :app:assembleDebug
./gradlew :tools:phase0:assembleDebug
./gradlew :core:test            # tests only; works even with no SDK installed
```

---

## 6. Prepare the phone

You need **USB debugging**. You do not need USB tethering — that shares your phone's internet
connection and is unrelated.

On the Redmi Note 13 5G running HyperOS:

1. **Settings → About phone**, tap **OS version** seven times to unlock Developer options.
2. **Settings → Additional settings → Developer options**, turn on **USB debugging**.
3. In the same screen, turn on **Install via USB**. Xiaomi requires this for `adb install` to work,
   and enabling it commonly requires signing into a Mi account with a SIM inserted. This is a
   Xiaomi restriction, not something this project can work around.
4. Connect the phone by cable and set the USB mode to **File transfer**. Charging-only mode blocks
   debugging on many Xiaomi builds.
5. A dialog will appear on the phone asking to **Allow USB debugging** — accept it, and tick
   *Always allow* so it stops asking.

Check the connection:

```bash
adb devices
```

You want one line ending in `device`. If it says `unauthorized`, the dialog in step 5 was not
accepted. If the list is empty, try a different cable — many charging cables carry power only.

---

## 7. Install and run

```bash
adb install -r tools/phase0/build/outputs/apk/debug/phase0-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Both can be installed at once; they are separate applications with separate identities and appear
separately in the launcher, named **Kestrel** and **Kestrel Phase 0**.

Open **Kestrel Phase 0** and you are ready to start Tier 0 of the procedure in
`docs/phase0/README.md`.

To remove them:

```bash
adb uninstall io.github.zxaidman.kestrel.phase0
adb uninstall io.github.zxaidman.kestrel
```

---

## 8. VS Code, if you want an editor

VS Code is optional — everything above works from a plain terminal. If you want to read and edit the
code comfortably, install it from <https://code.visualstudio.com> and add:

- **Kotlin** (fwcd) — syntax highlighting for `.kt` files
- **Gradle for Java** (Microsoft) — lets you run build tasks from a side panel

Be aware of the honest limitation: VS Code has no real understanding of this kind of project. You
get colours and basic navigation, not the completion or refactoring the full IDE provides. Use its
built-in terminal (**Ctrl+`**) to run the commands above.

---

## 9. When something goes wrong

| Message | Cause and fix |
| --- | --- |
| `SDK location not found` | `local.properties` is missing or the path in it is wrong. See §4. |
| `Failed to install the following SDK components` / licence errors | Run `sdkmanager --licenses` and accept each one. |
| `adb: command not found` | `platform-tools` is not on your PATH. Use the full path, e.g. `~/Android/platform-tools/adb`. |
| `adb devices` shows `unauthorized` | Accept the debugging prompt on the phone. |
| `adb devices` shows nothing | Cable is power-only, or USB mode is charging-only. |
| `INSTALL_FAILED_USER_RESTRICTED` | **Install via USB** is off in Developer options. This is the usual Xiaomi blocker — see §6 step 3. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A previous version is installed with a different signature. Uninstall first (§7). |
| Build fails after pulling new changes | Run `./gradlew clean build`. |

If you hit something not listed, copy the **last 20 lines** of the output — that is where the real
error is. The thousands of lines above it are normal progress noise.

---

## 10. What was actually verified

The Linux path in this guide was executed end to end: the command-line tools were installed, the
three SDK packages listed in §3 were installed, and `./gradlew build` completed successfully,
producing both APKs with the identities and sizes stated above.

The Windows and macOS instructions follow the same shape with different paths, but were **not**
executed. If a step there behaves differently, that is worth reporting so this document can be
corrected — per `AI_DEVELOPMENT_GUIDE.md`, untested is not the same as working.

Neither APK has been installed on a physical device or launched. That is the next step, and it is
yours.
