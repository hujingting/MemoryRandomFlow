# Photo Reviewer (回流)

Photo Reviewer is a simple Android application designed to help you efficiently review and manage your photos. It presents a random selection of your photos in a focused, one-by-one view, allowing you to make quick decisions to keep, favorite, or delete them. This streamlines the process of cleaning up your device's storage and rediscovering old memories.

The app's Chinese name, "回流" (Huíliú), translates to "Reflow" or "Flow Back," reflecting the idea of letting past photo memories flow back to you for review.

## Features

- **Randomized Photo Discovery**: Loads a random sample of photos from your gallery each session.
- **Intuitive Swipe Gestures**:
    - **Swipe Left** to mark a photo for deletion.
    - **Swipe Right** to mark a photo as a favorite.
- **Undo Action**: Instantly undo the last deletion swipe if you make a mistake.
- **Batch Deletion**: Photos marked for deletion are removed from view and can be permanently deleted in a single batch action.
    - Uses the Android Recycle Bin (Trash) on supported devices (Android 11+) for safer deletion.
- **Photo Details**: Tap on a photo to view it in a zoomable detail screen, which displays metadata like:
    - Image dimensions (width x height)
    - Date and time taken
    - Geolocation (city, country), if available in the EXIF data.
- **Simple UI**: A clean and focused interface for distraction-free photo management.

## Technology Stack

- **Language**: Kotlin
- **Platform**: Android
- **UI**: Android SDK with ViewBinding and `RecyclerView`.
- **Architecture**: Model-View-ViewModel (MVVM)
- **Asynchronous Operations**: Kotlin Coroutines and Flow for background tasks and UI updates.
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/) for efficient loading and display of images.
- **Permissions**: Handles modern Android runtime permissions for media access.

## How to Build and Run

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/PhotoReviewer.git
    ```
2.  **Open in Android Studio**:
    - Open Android Studio.
    - Select "Open an existing project".
    - Navigate to the cloned repository folder and select it.
3.  **Sync Gradle**:
    - Let Android Studio automatically sync the project's dependencies via Gradle.
4.  **Run the app**:
    - Select an emulator or connect a physical Android device.
    - Click the "Run" button (▶️) in Android Studio.

The app will build, install, and launch on your selected device. It will request permission to access your photos upon first launch.
