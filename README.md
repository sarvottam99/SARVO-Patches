# SARVO Patches

A growing, multi-app collection of Android patches for **Morphe Manager**.

SARVO Patches is designed as one installable patch bundle for multiple Android apps. Each patch declares the app package and supported versions it targets, so new app families can be added without creating a separate bundle for every app.

**Current app support:** Panasonic MirAIe — `com.panasonic.in.miraie`, version `01.4.32`.

<a href="https://morphe.software/add-source?github=sarvottam99/SARVO-Patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add SARVO Patches to Morphe Manager" height="48">
</a>

## Install

Add **SARVO Patches** to Morphe Manager using the button above, or add this GitHub source manually:

`sarvottam99/SARVO-Patches`

Choose the stable release for normal use. To try development builds, enable pre-releases for this source in Morphe Manager.

## Patch catalogue

The catalogue below is generated from the actual patch bundle during each release. It includes supported app package names, tested versions, patch descriptions and available options.

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.14](https://github.com/sarvottam99/SARVO-Patches/releases/tag/v1.0.0-dev.14)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
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
| [Dark mode](#dark-mode) | Forces MirAIe into night mode, enables Android Force Dark, darkens its legal WebView, and keeps energy-chart labels readable. |  |
| [Disable Play Store updates](#disable-play-store-updates) | Disables Play Store updates by setting the version code to the maximum allowed. This patch may cause unexpected issues with some apps and does not work if the app is installed by root mounting |  |
| [Spoof signature](#spoof-signature) | Spoofs the package signature of the original APK. |  |

</details>
<!-- PATCHES_END -->

## One bundle, multiple apps

All app-specific patches are shipped in the same `.mpp` bundle. Compatibility metadata determines which patches are offered for an installed app.

The collection currently starts with Panasonic MirAIe. Additional apps can be added as separate patch groups while keeping the same SARVO Patches source and release channel.

## Releases

- **Stable:** published from `main` and intended for everyday use.
- **Development:** published from `dev` as pre-releases for testing upcoming changes.

The release workflow builds the bundle, generates the machine-readable patch catalogue and bundle manifest, updates this README's catalogue, and attaches the `.mpp` file to the GitHub release. Generated files such as `patches-list.json`, `patches-bundle.json` and `CHANGELOG.md` should not be edited by hand.

## Build locally

Requirements: Java 21 and the dependencies declared by the Gradle project.

```bash
./gradlew buildAndroid
```

The bundle is written to:

```text
patches/build/libs/patches-*.mpp
```

Import that `.mpp` file into Morphe Manager to test a local build. See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) and [patcher documentation](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/1_patcher_intro.md) for development guidance.

## Add another app

1. Create a package for the app's patches under `patches/src/main/kotlin/`.
2. Define its `Compatibility` metadata, including package name, APK type and supported versions.
3. Attach that compatibility to each app-specific patch with `compatibleWith(...)`.
4. Build and test the combined bundle with `./gradlew buildAndroid`.

The patch-list generator discovers patches from the built bundle, so supported app groups and patch options are reflected in the catalogue automatically.

## Project notes

SARVO Patches is an independent community project. It is not affiliated with, endorsed by, or sponsored by Panasonic or the Morphe project. **Morphe Manager** and the Morphe patching framework are separate projects.

## License

SARVO Patches is licensed under the [GNU General Public License v3.0](LICENSE).
