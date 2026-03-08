# TwinMind

## Overview
TwinMind is a robust Android voice recording and intelligent notes application built with Kotlin, Jetpack Compose, and MVVM Clean Architecture. It records audio locally in chunks, processes transcriptions in the background, and utilizes the Google Gemini API to generate structured meeting summaries. 

It handles multiple real-world edge cases ensuring seamless recording including tracking phone calls, audio focus loss, and silent mic scenarios seamlessly via an Android Foreground Service.

## Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- A Google Gemini API Key

## Setup & Run Instructions

1. Clone or download the repository to your local machine.
2. Open the project in Android Studio.
3. In the root directory of the project, locate or create a `local.properties` file.
4. Add your Google Gemini API Key to this file following this format:
   ```properties
   GEMINI_API_KEY=your_actual_api_key_here
   ```
5. Sync the project with Gradle files.
6. Select an emulator or connected physical Android device (API 24+) from the target dropdown.
7. Click "Run" in Android Studio or execute `./gradlew assembleDebug` in the terminal to build and install the Application.

## Acknowledgement
Thank you to the TwinMind team for this engaging and comprehensive assignment. It has been a valuable learning experience in handling complex Android services, real-time audio processing, and AI integrations.


