# 👋🧩 Miraie Patches

Template repository for Morphe Patches.

## ❓ About

Panasonic MirAIe patches for Morphe Manager.

<!-- TODO: Update this about section with a brief introduction/summary about this repo and what it offers. -->

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/sarvottam99/Miraie-Patches

<a href="https://morphe.software/add-source?github=sarvottam99/Miraie-Patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.5](https://github.com/sarvottam99/Miraie-Patches/releases/tag/v1.0.0-dev.5)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 MirAIe&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**Package name:** `com.panasonic.in.miraie`

**🎯 Supported versions:**

| 01.4.32 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change installer source](#change-installer-source) | Spoofs the installer source so the app appears to be installed from an app store. | • Spoofed package installer name |
| [Dark mode](#dark-mode) | Adds system-following dark mode and fixes dashboard, E-Care, account, AC settings, notifications and energy-chart contrast. |  |
| [Disable Play Store updates](#disable-play-store-updates) | Disables Play Store updates by setting the version code to the maximum allowed. This patch may cause unexpected issues with some apps and does not work if the app is installed by root mounting |  |
| [Spoof signature](#spoof-signature) | Spoofs the package signature of the original APK. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

Miraie Patches are licensed under the [GNU General Public License v3.0](LICENSE)

## MirAIe support

This repository contains patches for Panasonic MirAIe Android app package
`com.panasonic.in.miraie`, targeting version `01.4.32` (XAPK).
The bundle is intended for use with Morphe Manager; it is an independent,
community-maintained project and is not affiliated with the Morphe project
or Panasonic.

Included patches: Dark mode (follows Android system dark mode), Change
installer source, Disable Play Store updates, and Spoof signature.
