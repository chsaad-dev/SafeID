# SafeID

## Project Overview

SafeID is an open source privacy utility for Android that allows people to secure sensitive identity documents before sharing them. The app strips hidden camera and location data from document photos, applies protective watermarks, blurs private details, and compresses files to exact upload limits entirely on the user device without internet access.

* Application ID: `com.saaddev.safeid`
* Target Platform: Android 8.0 and above (API level 26 through 36)

## Why We Built This App

Every day, millions of people take photos of their national identity cards, passports, driving licenses, or utility bills to complete basic tasks. They send these images to mobile phone shops, banks, landlords, employers, travel agencies, and government portals.

In almost every case, these photos are sent as raw, unedited camera files. Once an unedited image leaves a phone, the owner loses all control over it. Anyone who receives the photo can store it, forward it, or upload it to other places. 

We built SafeID to give smartphone users a free, fast, and completely private tool to protect their documents before sending them anywhere. The application runs entirely on the phone itself. It does not require creating an account, entering an email address, or paying a subscription. Most importantly, it never uploads documents to remote servers or cloud services.

## The Problem It Solves and Why People Need It

Sharing raw document photos creates serious risks that most people only discover after becoming victims of identity fraud. SafeID solves four major problems:

* Unmarked documents can be reused without permission: When an unedited copy of an identity card is sent to a shop or uploaded online, a dishonest third party can save the image and use it to register unauthorized phone SIM cards, apply for quick loans, or open fraudulent accounts. SafeID places permanent, repeated watermark text across the entire document stating the exact purpose and date, such as "For Bank Account Opening Only". This makes the file useless for any other purpose.
* Standard phone camera photos leak private personal details: Every modern smartphone camera secretly embeds exchangeable image file format metadata, known as EXIF data, inside every photo. This hidden data includes the exact GPS coordinates of the home or office where the photo was taken, the date and time of capture, the phone manufacturer, the phone model, and the camera software version. SafeID inspects every image, reveals these hidden tags to the user, and strips all metadata completely during processing.
* Online upload portals have strict size and layout rules: Government portals, job boards, and visa websites frequently reject uploads because the files are too large. Many sites also require both the front and back of an identity card to appear together on a single page. SafeID provides an automatic front and back layout on a single A4 page and uses an intelligent compression system that fits files under strict limits like 50 KB, 100 KB, or 200 KB without making text unreadable.
* Most existing scanner apps compromise user privacy: Many popular document scanning apps require users to log in, show invasive full screen promotions, or silently upload documents to third party servers for optical recognition. SafeID performs every calculation, crop, filter, watermark, and PDF generation directly inside the local phone memory using native Android system tools.

## How SafeID Works Step by Step

The user experience in SafeID is organized into four simple, clear screens.

### Screen 1: Home Screen

The Home screen provides a clean, welcoming layout that clearly shows the offline status of the app. Users can choose between two main workflows:

* Single Document Mode: Designed for single page documents, passports, certificates, or driving licenses. Users can pick an existing photo from their gallery using the secure Android Photo Picker or take a fresh photo with the camera. Camera permissions are requested only when the user explicitly taps the camera button.
* Front and Back ID Card Mode: Guides the user through a two step capture process. The user picks or snaps the front side of their card, then picks or snaps the reverse side. The app then automatically arranges both sides centered neatly on a standard A4 document layout.
* Direct Share Integration: SafeID can also be opened directly from the phone gallery or messaging apps. When a user selects an image in another app and taps the system Share button, choosing SafeID immediately bypasses the Home screen and loads the image straight into the editing workflow.

### Screen 2: Crop and Privacy Audit Screen

When an image enters this screen, the app immediately inspects the file and presents a clear Privacy Audit Card at the top:

* Privacy Audit Report: The card tells the user exactly how many hidden metadata fields were discovered, listing details such as geographic location coordinates, phone hardware model, and capture timestamps.
* Interactive 4 Corner Crop: The document is displayed on an interactive canvas with four circular corner handles. Users can drag each corner independently to straighten tilted or skewed documents. While dragging, a circular magnifier loupe appears above the user finger, magnifying the area by 2.2 times so corners can be placed with pinpoint accuracy.
* Document Filters: A row of filter chips allows the user to enhance readability with one tap. Options include Original, Clean Scan for a high contrast black and white document appearance, and Color Boost for vivid color preservation.
* Dual Card Tabs: In Front and Back mode, users can easily toggle between editing the front card and the back card before moving forward.

### Screen 3: Watermark and Target Size Studio

This screen gives users complete control over how their document is protected and formatted:

* Purpose Presets: Users can tap preconfigured watermark options including SIM Verification, Bank / KYC, Job Application, Visa / Travel, Rental Agreement, or Custom Text to type their own specific notice.
* Automatic Date Inclusion: A simple switch lets users append the current date to the watermark text so recipients cannot use an old copy years later.
* Watermark Appearance: Users can adjust text opacity from 10 percent to 60 percent using a smooth slider and switch between Slate Grey, Crimson Red, and Navy Blue text colors. The watermark is drawn as repeated diagonal lines across the whole document at a minus 30 degree angle.
* Blur Redaction Brush: Users can switch to a finger brush mode to blur sensitive fields like personal identification numbers, tax numbers, or signatures. An undo button allows quick corrections.
* Export Format and Target File Size: Users can select JPG or PDF output format. Front and Back ID mode automatically defaults to a standardized A4 PDF. A row of target size chips lets users choose Original, under 200 KB, under 100 KB, or under 50 KB. Tapping the primary button compresses the document on a background thread and prepares the export.

### Screen 4: Export and Share Screen

The final screen confirms that the document is protected and ready for delivery:

* Verification Badge: Displays a green security badge showing the exact final file size, the format, and visual confirmation that all GPS and camera details were stripped and watermarks were applied.
* Share via Messaging or Email: Launches the standard Android share sheet using a secure temporary file provider, allowing the user to send the document directly through WhatsApp, Gmail, Telegram, or any other installed app.
* Save to Downloads: Saves the file directly to the public Downloads folder under a dedicated SafeID directory using the modern Android MediaStore system, without requiring any broad storage permissions.
* Privacy Auto Wipe: An automatic cleanup switch is enabled by default. As soon as the user completes their export and leaves the screen, all temporary files stored in the internal app cache are erased immediately.

## Key Features Built So Far

* Complete local execution where every image operation runs directly on the phone processor.
* Zero storage permissions required, preserving complete privacy while reading and saving files.
* Automatic stripping of all EXIF metadata including GPS latitude, longitude, altitude, camera make, model, software version, and timestamps.
* Privacy audit report that explicitly informs the user which hidden tags were found and removed.
* Free 4 corner perspective transformation to correct angles and flatten documents.
* Real time corner loupe that magnifies the image under the user finger during cropping.
* Fast document color filters including high contrast black and white document scan and color enhancement.
* Tiled diagonal repeating watermark text rendered across the entire image at a minus 30 degree angle.
* Quick watermark text presets for banking, identity verification, job applications, travel visas, and rental agreements.
* Optional automated date stamping appended to watermark text.
* Adjustable watermark opacity and three high contrast color options.
* On screen blur redaction brush for obscuring signatures, home addresses, or registration numbers.
* Front and back identity card stitching onto a single 300 DPI equivalent printable A4 page.
* Binary search JPEG compression engine that hits exact file size targets like 50 KB, 100 KB, and 200 KB.
* Standard Android share sheet integration using secure content URIs.
* Scoped storage export to the public Downloads folder using the MediaStore API.
* Automatic wiping of temporary cached files upon exit.
* System wide image sharing receiver supporting incoming images from any gallery or browser.
* Frequency capped interstitial ad manager enforcing a strict three minute cooldown between impressions and failing silently when offline.
* Collapsible banner ad component that takes up zero screen space when the device is disconnected from the internet.
* Full set of modern adaptive launcher icons and an Android Material You dynamic monochrome theme icon.
* Google Play Console store icon formatted at 512 by 512 pixels with zero transparency.

## Privacy and Offline Architecture

SafeID was built from the ground up with a strict privacy first architecture.

### True Offline Operation

The core image processing pipeline does not use any network libraries. The app functions with identical speed and capability when the phone is in airplane mode or has no network connection. Network permissions in the manifest are utilized exclusively by Google Mobile Ads SDK for optional test banner and interstitial placements, which fail gracefully without interrupting the user workflow when offline.

### Zero Storage Permissions

Traditional Android utilities frequently demand broad read and write access to all shared storage. SafeID requests zero storage permissions:

* Image Selection: The app uses the native Android Photo Picker (`ActivityResultContracts.PickVisualMedia`), which allows the user to select only the specific image they wish to edit without granting the app access to their wider photo library.
* Camera Capture: The app uses a temporary content URI provided by Android `FileProvider` (`ActivityResultContracts.TakePicture`), requiring only the standard camera runtime permission.
* Saving Files: Exported documents are written directly to the public Downloads directory using the Android `MediaStore` collection. This modern approach works seamlessly on Android 10 and newer versions without any legacy storage permission prompts.

### Automatic Memory and Cache Cleanup

Document security requires that unencrypted copies do not remain stored in hidden locations. SafeID handles this in two ways:

* During processing, original bitmaps are kept only in temporary device memory and are recycled as soon as the session completes.
* Generated output files are written to a private cache subfolder. When the user finishes sharing and returns to the Home screen, the application deletes all generated files from the cache directory, leaving no trace behind.

## Technical Stack and Project Structure

### Build Configuration

* Gradle Version: Android Gradle Plugin 9.5.1
* Kotlin Version: 2.2.10
* Compile SDK: 36 (Android 16 Developer Preview ready)
* Minimum SDK: 26 (Android 8.0 Oreo)
* Target SDK: 36
* Java Compatibility: Java 11 (Source and Target)
* UI Toolkit: Jetpack Compose with Material 3 (BOM version 2026.02.01)
* Navigation: `androidx.navigation:navigation-compose:2.8.4`
* Metadata Engine: `androidx.exifinterface:exifinterface:1.3.7`
* Ad Framework: `com.google.android.gms:play-services-ads:23.6.0`
* Release Optimizations: Code minification with R8 and resource shrinking enabled

### Project Directory Structure

```text
SafeID/
    app/
        build.gradle.kts
        src/
            main/
                AndroidManifest.xml
                java/com/saaddev/safeid/
                    MainActivity.kt
                    SafeIdApplication.kt
                    ads/
                        InterstitialAdManager.kt
                    data/model/
                        ExifAuditSummary.kt
                        ExportConfig.kt
                        SafeIdSessionState.kt
                    engine/
                        ExifPrivacyEngine.kt
                        ExportAndCompressionEngine.kt
                        PerspectiveAndFilterEngine.kt
                        WatermarkAndBlurEngine.kt
                    ui/
                        components/
                            AdBannerComposable.kt
                        navigation/
                            SafeIdNavGraph.kt
                            Screen.kt
                        screens/
                            CropAuditScreen.kt
                            ExportShareScreen.kt
                            HomeScreen.kt
                            WatermarkStudioScreen.kt
                        theme/
                            Color.kt
                            Theme.kt
                            Type.kt
                        viewmodel/
                            SafeIdViewModel.kt
                res/
                    drawable/
                        ic_launcher_monochrome.xml
                    drawable-xxxhdpi/
                    mipmap-anydpi-v26/
                        ic_launcher.xml
                        ic_launcher_round.xml
                    mipmap-xxxhdpi/
                    values/
                        colors.xml
                        ic_launcher_background.xml
                        strings.xml
                        themes.xml
                    xml/
                        backup_rules.xml
                        data_extraction_rules.xml
                        file_paths.xml
    gradle/
        libs.versions.toml
    build.gradle.kts
    settings.gradle.kts
    play_store_icon_512.png
    README.md
```

### Package Descriptions

* `com.saaddev.safeid`: Contains the application entry points including `SafeIdApplication` for background service initialization and `MainActivity` for lifecycle management and intent handling.
* `com.saaddev.safeid.ads`: Houses `InterstitialAdManager`, which manages ad preloading, cooldown enforcement, and offline protection.
* `com.saaddev.safeid.data.model`: Defines data classes representing the active document state, the privacy audit report, and user export configurations.
* `com.saaddev.safeid.engine`: The native mathematical and image processing layer containing `ExifPrivacyEngine`, `PerspectiveAndFilterEngine`, `WatermarkAndBlurEngine`, and `ExportAndCompressionEngine`.
* `com.saaddev.safeid.ui.components`: Holds modular Compose widgets including `AdBannerComposable`.
* `com.saaddev.safeid.ui.navigation`: Defines navigation routes and screen graphs using Android Navigation Compose.
* `com.saaddev.safeid.ui.screens`: Contains the primary Jetpack Compose screens for Home, Crop and Audit, Watermark Studio, and Export and Share.
* `com.saaddev.safeid.ui.theme`: Specifies the Material 3 visual styling, colors, typography, and dark navy aesthetic.
* `com.saaddev.safeid.ui.viewmodel`: Contains `SafeIdViewModel`, which coordinates session state, asynchronous processing tasks, and user selections across screens.

## How to Build and Run Locally

To test or modify SafeID on your development computer, follow these simple steps:

### Prerequisites

* Android Studio: Ladybug (version 2024.2) or newer is recommended.
* Java Development Kit: JDK 11 or JDK 17 installed and configured in Android Studio.
* Android SDK: Android SDK Platform 36 installed through the Android Studio SDK Manager.
* Hardware or Virtual Device: An Android device or emulator running Android 8.0 (API level 26) or higher.

### Steps to Build

1. Clone the project repository to your computer:
   ```shell
   git clone https://github.com/chsaad-dev/SafeID.git
   ```
2. Open Android Studio, choose Open, and select the `SafeID` folder.
3. Allow Android Studio to sync the Gradle build files automatically. If prompted, click Sync Project with Gradle Files.
4. Connect an Android device with USB debugging enabled, or start an Android Virtual Device from the Device Manager.
5. In the top toolbar, select `app` as the run configuration and click the green Run button, or press Shift and F10.

### Running from the Command Line

You can also compile and assemble the debug application directly using the Gradle wrapper:

* On Windows:
  ```shell
  .\gradlew.bat assembleDebug
  ```
* On macOS and Linux:
  ```shell
  ./gradlew assembleDebug
  ```

The generated debug package will be created in the `app/build/outputs/apk/debug/` directory.

To run the automated unit test suite, execute:

* On Windows:
  ```shell
  .\gradlew.bat testDebugUnitTest
  ```
* On macOS and Linux:
  ```shell
  ./gradlew testDebugUnitTest
  ```
