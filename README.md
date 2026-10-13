<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="resources/anitv-logo.svg">
    <img src="resources/anitv-logo-light.svg" alt="AniTV logo" width="420">
  </picture>
</p>

<h1 align="center">AniTV-dot</h1>
<p align="center"><a href="README.md">English</a> | <a href="README-TR.md">Türkçe</a></p>

Watch animes with your Android TV with **AniTV-dot**.

*This tool scrapes the site <a href="https://hianime.at/">hianime</a>.*

## Features

The project is still in development, but the following features are already implemented:

- Searching, favoriting, mark as watched anime titles.
- Continue watching animes from the last watched episode.
- Caption settings such as closing/opening captions, changing style of captions, and translating captions to other languages via Google Translate and DeepL (API keys required) (DeepL API tested and works, Google Translate API NOT TESTED).

## Current Status of Development

- Minimum SDK version: 34 (Android 14)
- Target SDK version: 37 (Android 17)

## Installation and local releases

AniTV requires Android TV / Google TV with Android 14 or newer. The current preparation uses version `1.0.0` and Android versionCode `1`, with automatic signed HiAnime rule updates from the public [rules branch](https://github.com/fiatsiz/AniTV-dot/tree/rules).

Build an owner-signed APK using the external credentials in the [release guide](docs/RELEASE.md), then copy that APK to your TV and install it with your chosen APK installer. Check its checksum and signing certificate first. A differently signed debug installation normally cannot update in place; uninstalling can erase Favorites and progress. Never clear data or uninstall automatically.

The [verification report](docs/RELEASE-VERIFICATION.md) records source commit `7d0f3db56e5e7b4686dcbcd11dc43a83624e8d9b`, test results, and the available debug APK's checksum. **Owner signing credentials are still required; this preparation has not produced an owner-signed release APK.** Only rules were published. No APK update checker, downloader, installer, or GitHub APK release is included. Distributed APKs must include the matching source and [GPL-3.0 license](LICENSE).

## Inspiration

This project was inspired by the [ani-cli](https://github.com/pystardust/ani-cli) project, which is a command-line tool for watching animes. I wanted to create a similar experience for Android TV users (especially for myself).

## AI Tools in Development

<details>
  <summary>AI Usage</summary>

  This project is a hobby project of mine, so I use AI tools to help me with the development (in the first place I created this project only for my use to watch animes). But I do the tests of the application with my Android TV, and I do the final review of the code before pushing it to the repository. So, if you find any bug, please report it in the issues section.
</details>

## Disclaimer

AniTV-dot is an independent hobby project. HiAnime has no affiliation with anime creators, distributors, Google, or DeepL, nor is it supported by these organizations.

The app accesses content and services provided by third parties. This project does not host any media nor does it grant any rights to videos, subtitles, images, or trademarks. These rights belong to their respective owners. Your use of this app & access to content of third-party services are entirely at your own risk. Personal installation and use do not grant permission to copy or redistribute this content.

Third-party sites and services may change, block requests, or become unavailable. Search, playback, subtitles, and translation features may not work. If you enable subtitle translation, the app will send the subtitle text to the Google Cloud Translation or DeepL service you select using the API key you provide.

The software is provided without warranty under the terms of the [GPL-3.0 license](LICENSE).
