# SARVO Patches

A multi-app collection of Android bytecode patches for **Morphe Manager**. SARVO Patches brings together app-specific customizations in one Morphe patch source, with each patch declaring the app package and versions it targets.

<a href="https://morphe.software/add-source?github=sarvottam99/SARVO-Patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add SARVO Patches to Morphe Manager" height="48">
</a>

## Install

1. Install **Morphe Manager**.
2. Add this source using the button above, or add the source manually:

   `sarvottam99/SARVO-Patches`
3. Open the source in Morphe Manager, choose the app you want to patch, select the available patches, and build the patched APK.
4. Install the output using the install flow recommended by Morphe Manager.

Use a stable release for normal use. Development builds, when available, can be enabled from the source's pre-release settings.

## Supported apps and patches

### Panasonic MirAIe

| Field | Details |
|---|---|
| App | Panasonic MirAIe |
| Package | `com.panasonic.in.miraie` |
| Patch family | MirAIe UI, appearance, installer and update customizations |

The MirAIe patch set includes:

| Patch | Purpose |
|---|---|
| **Change installer source** | Spoofs the installer identity reported to the app. The selected installer identity can be configured in the patch options. |
| **Dark mode** | Forces the app into night mode, enables Android Force Dark, darkens its legal WebView, and preserves readable energy-chart labels. |
| **Disable Play Store updates** | Raises the reported version code to discourage Play Store updates. This can cause unexpected behavior and does not work with root-mounted installations. |
| **Hide Lifestyle tab** | Hides the Lifestyle item from the bottom navigation without reordering the other tabs. |
| **Remove dashboard promotional banner** | Removes the dashboard promotional carousel before it initializes. |
| **Spoof signature** | Spoofs the package signature expected by the original app. |

The exact supported MirAIe versions are declared in the patch compatibility metadata and shown by Morphe Manager. Check that list before patching an APK.

### Samsung Good Lock

| Field | Details |
|---|---|
| App | Samsung Good Lock |
| Package | `com.samsung.android.goodlock` |
| Target version declared by the patch | `3.0.16.6` |
| Patch | **Unlock all Samsung Good Lock modules** |

**What it does:** Good Lock builds its plugin catalogue using device and compatibility information. This patch adds a curated set of 25 plugin records to Good Lock's store-backed plugin list when they are not already present. Good Lock's normal catalogue processing then displays these entries instead of filtering them out solely because Samsung has not listed them for that device.

**What it does not do:** The patch does not install the individual plugins, modify their internals, or make an unsupported plugin universally compatible. Some plugins depend on Samsung framework components, One UI versions, device-specific features, or other services. A plugin can appear in Good Lock and still fail to install, launch, or function correctly on a non-Samsung device or unsupported software build. Install a compatible plugin APK separately when needed, and check its Android version and ABI requirements before installing it.

**Compatibility note:** This patch targets the Good Lock app package and the exact app version shown above. A different Good Lock release may have a changed internal layout and is not automatically supported. Do not assume that a successful patch means all 25 plugins will work on every device.

<details>
<summary>Show all 25 Good Lock plugins and their APKMirror pages</summary>

| # | Good Lock plugin | Package name | APKMirror |
|---:|---|---|---|
| 1 | QuickStar | `com.samsung.android.qstuner` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/quickstar/) |
| 2 | Home Up | `com.samsung.android.app.homestar` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/home-up/) |
| 3 | MultiStar | `com.samsung.android.multistar` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsung-multistar/) |
| 4 | Sound Assistant | `com.samsung.android.soundassistant` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/soundassistant/) |
| 5 | LockStar | `com.samsung.systemui.lockstar` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/lockstar/) |
| 6 | NotiStar | `com.samsung.systemui.notilus` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/notistar/) |
| 7 | NavStar | `com.samsung.systemui.navillera` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsung-navstar/) |
| 8 | ClockFace | `com.samsung.android.app.clockface` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsung-clockface/) |
| 9 | Theme Park | `com.samsung.android.themedesigner` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsung-theme-park/) |
| 10 | Wonderland | `com.samsung.android.wonderland.wallpaper` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/wonderland/) |
| 11 | Pentastic | `com.samsung.android.pentastic` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/pentastic/) |
| 12 | RegiStar | `com.samsung.android.app.galaxyregistry` | [APKMirror](https://www.apkmirror.com/apk/good-lock-labs/registar/) |
| 13 | One Hand Operation + | `com.samsung.android.sidegesturepad` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/one-hand-operation/) |
| 14 | Nice Shot | `com.samsung.android.app.captureplugin` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/nice-shot/) |
| 15 | Camera Assistant | `com.samsung.android.app.cameraassistant` | [APKMirror](https://www.apkmirror.com/apk/good-lock-labs/camera-assistant/) |
| 16 | Routines + | `com.samsung.android.app.routineplus` | [APKMirror](https://www.apkmirror.com/apk/good-lock-labs/samsung-routine/) |
| 17 | Keys Cafe | `com.samsung.android.keyscafe` | [APKMirror](https://www.apkmirror.com/apk/good-lock-labs/keys-cafe/) |
| 18 | Nice Catch | `com.samsung.android.app.goodcatch` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/nice-catch/) |
| 19 | Display Assistant | `com.samsung.android.displayassistant` | [APKMirror](https://www.apkmirror.com/apk/galaxy-labs/display-assistant-beta/) |
| 20 | Dropship | `com.samsung.dropship_client` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsung-dropship/) |
| 21 | Edge lighting+ | `com.samsung.android.edgelightingplus` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/edgelighting/) |
| 22 | Edge Touch | `com.samsung.android.app.edgetouch` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/edge-touch/) |
| 23 | Galaxy To Share | `com.samsung.android.gts` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/galaxy-to-share/) |
| 24 | Gallery Assistant | `com.samsung.android.gallery.assistant.app` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/gallery-assistant/) |
| 25 | Game Booster + | `com.samsung.android.game.honeyplayplus` | [APKMirror](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/game-booster-2/) |

APKMirror pages list available releases and variants. Select an APK that matches your device's Android version and CPU architecture; availability and compatibility can change over time.

</details>

## Patch catalogue

The catalogue below is generated from the compiled patch bundle and `patches-list.json` during a semantic-release run. It lists the app packages, supported versions, patch descriptions, and configurable options declared by the patches.

<!-- PATCHES_START -->
> **[v1.2.0](https://github.com/sarvottam99/SARVO-Patches/releases/tag/v1.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details open>
<summary>📦 MirAIe&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**Package name:** `com.panasonic.in.miraie`

**🎯 Supported versions:**

| 🧪&nbsp;01.4.32 | 01.4.30 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change installer source](#change-installer-source) | Spoofs the installer source so the app appears to be installed from an app store. | • Spoofed package installer name |
| [Dark mode](#dark-mode) | Forces MirAIe into night mode, enables Android Force Dark, darkens its legal WebView, and keeps energy-chart labels readable. |  |
| [Disable Play Store updates](#disable-play-store-updates) | Disables Play Store updates by setting the version code to the maximum allowed. This patch may cause unexpected issues with some apps and does not work if the app is installed by root mounting |  |
| [Hide Lifestyle tab](#hide-lifestyle-tab) | Hides only the Lifestyle tab from MirAIe's bottom navigation. Other tabs, icons and their order remain unchanged. |  |
| [Remove dashboard promotional banner](#remove-dashboard-promotional-banner) | Removes the dashboard promotional carousel before it initializes. |  |
| [Spoof signature](#spoof-signature) | Spoofs the package signature of the original APK. |  |

</details>

<details open>
<summary>📦 Samsung Good Lock&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.samsung.android.goodlock`

**🎯 Supported versions:**

| 3.0.16.6 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock all Samsung Good Lock modules](#unlock-all-samsung-good-lock-modules) | Show all Good Lock modules irrespective of Samsung official device compatibility. They may or may not work depending on your device Android and/or UI version or other factors. You need to install unsupported modules separately from apk files from the internet. |  |

</details>

<!-- PATCHES_END -->

## Build and release

- Source code is maintained in this repository.
- GitHub Actions builds and verifies the Morphe patch bundle; releases publish the `.mpp` bundle and update the generated patch catalogue.
- The patch project is intended to be built through the repository workflow. Local Android/Gradle build instructions are not required to use the published bundle.

## Disclaimer

SARVO Patches is an independent community project. It is not affiliated with, endorsed by, or sponsored by Samsung, Panasonic, or the Morphe project. Samsung Good Lock and its plugins are Samsung software. APKMirror is a third-party APK catalogue; links are provided as references, not as an endorsement. Use patches and separately downloaded APKs at your own discretion. Always verify APK source, signature, Android version, and architecture before installing.

## License

SARVO Patches is licensed under the [GNU General Public License v3.0](LICENSE).
