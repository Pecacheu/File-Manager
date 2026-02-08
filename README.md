# About this fork

This fork adds **network file sharing via SMB** *(more protocols eg. SFTP/FTPS may be added in the future)*, among many other new features and improvements! Don't expect this to get merged into the base branch any time soon. The official maintainers take a stance against the app offering any networking functionality whatsoever.

### Other features/improvements include
- Favorites have their own tab instead of a tiny menu bar option
- Favorites can be removed easily from the tab instead of via `Menu -> Settings -> Manage Favorites`
- Improved sharing support to more apps and MIME type detection (eg. HEIC images don't share correctly on main branch)
- Reverse sharing supported too! Share **TO** File Explore as a destination, allowing you to import images and files in from other apps/instant messengers instantly and choose where to save them.
- Improved thumbnail performance, also fully supports **thumbnails on remote shares!** (A very rare feature in file manager apps I've tried)
- **Vastly** improved search performance, searches show results asynchronously and can be cancelled at any time w/ back button
- Bulk jobs (copy/move/delete/etc) run in background and show a progress notification that allows cancelling them at any time
- Runs faster, many methods are more optimized
- Cleaner edge-to-edge UI w/ full display notch/cutout support
- Better touch keyboard handling (doesn't move tab bar up and block UI)
- Physical keyboard shortcuts (tested with Samsung DeX/Desktop Mode!)
- Stays in selection mode when rotating screen
- And more!

### TODO
- Fix file rename on remote
- Fix "Open with" in ReadTextActivity (MainActivity "Open with" works fine)
- Fix clicking "cancel" in overwrite dialog breaking the transfer (use the "skip" option for now)
- Add recycle bin if that gets pushed to main
- Add support for more protocols
- Various bugfixes

# Fossify File Manager (Chu Edition :3)

<img alt="Logo" src="graphics/icon.webp" width="120"/>

<!-- <a href='https://play.google.com/store/apps/details?id=org.fossify.filemanager'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=80/></a> <a href="https://f-droid.org/packages/org.fossify.filemanager/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" height=80/></a> <a href="https://apt.izzysoft.de/fdroid/index/apk/org.fossify.filemanager"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height=80/></a> -->

Tired of file managers that slow you down and invade your privacy? Unlock a lightning-fast, secure, and completely customizable experience with Fossify File Manager. ⚡

**🚀 DOMINATE YOUR DIGITAL WORLD WITH BLAZING-FAST NAVIGATION:**
 - Swiftly manage your files with easy compression and transfer capabilities, keeping your digital life organized.
 - Quickly access your most-used folders with customizable per-device home/root folders and global favorites.
 - Find what you need in seconds with intuitive navigation, search, and sorting options.
 - Select, share, move, and copy files across multiple directories and even separate network shares at once.

**🔐 FORTIFY YOUR DATA WITH UNPARALLELED PRIVACY AND SECURITY:**
 - Secure sensitive files with password, pattern, or fingerprint locks for hidden items or the entire app.
 - No internet access required – your files stay private and secure on your device.

**💾 MASTER YOUR STORAGE LIKE A PRO:**
 - Clear space with easy file and folder compression to maximize your device's potential.
 - Identify and clean up space-hogging files with the built-in storage analysis tool.
 - Seamlessly navigate root files, SD cards, and USB devices for total organization.
 - Add unlimited custom network shares using SMB. *(More share types coming soon!)*

**📁 OPTIMIZE YOUR WORKFLOW WITH HANDY TOOLS:**
 - Create desktop shortcuts for instant access to your most-used files and folders.
 - Edit, print, or read documents easily with the light file editor, enhanced by zoom gestures.

**🌈 MAKE IT YOUR OWN WITH ENDLESS CUSTOMIZATION:**
 - Enjoy an ad-free, open-source experience that puts you in control, not corporate giants.
 - Personalize colors, themes, and icons to reflect your unique style and preferences.

Ditch the bloated, privacy-invading file managers and experience true freedom with Fossify File Manager. Download now and take back control of your digital life!

➡️ Explore more Fossify apps: https://www.fossify.org<br>
➡️ Open-Source Code: https://www.github.com/FossifyOrg<br>
➡️ Join the community on Reddit: https://www.reddit.com/r/Fossify<br>
➡️ Connect on Telegram: https://t.me/Fossify

<div align="center">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" width="30%">
</div>