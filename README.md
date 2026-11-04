# Dola Down — Full Virtual Sandbox prototype

Dola Down is being rebuilt as a host application with an embedded VirtualApp guest runtime. The target is to install the user's already-installed Dola app into a separate virtual app space and launch that guest copy from Dola Down.

## Virtualization engine

- Engine: [VirtualApp_16](https://github.com/f0restw0w/VirtualApp_16), pinned as a Git submodule at commit `b3c634ad7941765df3da84a207aca94b7861afae`.
- Engine module: `:virtualapp-engine:lib`.
- Host package: `com.kawaqdr.doladown`.
- Guest package: `com.larus.wolf`.
- Guest install: copies the installed Dola APK set into VirtualApp's private package store; split APKs are imported from their common package directory.
- Guest launch: uses VirtualCore's virtual package manager and guest activity routing.
- The original Dola installation is not uninstalled or modified.

## First-run workflow

1. Install and open Dola Down.
2. Tap **Install Dola into Sandbox**.
3. Wait for the virtual package installer to finish.
4. Tap **Launch Dola in Sandbox**.
5. Sign in to the guest Dola instance and test video generation.

## Important compatibility notes

This is an experimental integration, not a verified production clone. VirtualApp_16 describes itself as unstable and not production-ready. Its published tested configuration is Android 14 on a Xiaomi Redmi / Snapdragon 695; it documents Android-version-specific hidden-API, native hooking, Google Play Services, storage, and signature limitations. The user's Infinix Android 12 device still requires on-device testing.

The Android Virtualization Framework (AVF) is not a drop-in alternative for this use: its VM APIs require privileged/system permissions and are not generally available to ordinary third-party apps.

Dola may depend on Play Integrity, Google sign-in, push notifications, device attestation, or anti-virtualization checks. If so, the guest app may fail to sign in or launch correctly. Dola Down will not bypass those protections.

## Current scope

This phase implements the host runtime startup, import of the installed Dola APK set, and guest launch. It does **not** yet implement reliable capture of Dola's private API traffic from the guest process or automatic 720P downloads. Those require separate guest-runtime integration and compatibility testing.

## Build

JDK 17, Gradle 7.5, Android Gradle Plugin 7.4.2, Android SDK 34, NDK 21.4.7075529.

The workflow checks out the pinned VirtualApp submodule and builds the Dola Down debug APK.
