# PhotoReviewer

PhotoReviewer 是一款简洁的 Android 应用，旨在帮助用户快速回顾和整理手机相册。通过简单的滑动手势，您可以轻松地标记并批量删除不需要的照片，从而释放存储空间。

## ✨ 功能特性

- **手势驱动**: 向上滑动即可将照片标记为待删除，操作直观高效。
- **批量处理**: 浏览完一组照片后，可以一次性确认删除所有已标记的照片。
- **安全确认**: 在执行删除操作前，会弹出对话框进行二次确认，防止误删。
- **权限处理**: 在应用启动时会妥善处理相册读取权限的申请。
- **现代化 UI**: 使用 Jetpack Compose 构建，支持系统深色、浅色主题，并适配了 Android 12+ 的动态色彩。

## 📱 工作流程

1.  **授权**: 应用启动后，会请求访问相册的权限。
2.  **浏览**: 授权后，应用会随机加载一批照片，全屏展示。
3.  **标记**: 您可以左右滑动切换照片。对于不满意的照片，向上滑动将其“扔掉”，标记为待删除。
4.  **确认**: 当您浏览完当前批次的所有照片，或者所有照片都已被标记时，应用会显示一个对话框，列出待删除照片的数量。
5.  **删除或取消**:
    - 点击“删除”，应用将永久删除这些照片。
    - 点击“取消”，应用将清空待删除列表并加载新的一批照片。

## 🛠️ 技术栈

- **语言**: [Kotlin](https://kotlinlang.org/)
- **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **架构模式**: MVVM (Model-View-ViewModel)
- **异步处理**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- **图片加载**: [Coil](https://coil-kt.github.io/coil/)
- **核心组件**: AndroidX, Lifecycle, ViewModel

## 🚀 如何构建

1.  **克隆仓库**:
    ```bash
    git clone <repository-url>
    ```
2.  **打开项目**:
    使用最新版本的 [Android Studio](https://developer.android.com/studio) 打开项目。
3.  **构建与运行**:
    等待 Gradle 同步完成后，直接点击 "Run" 按钮即可在模拟器或真实设备上运行。

## 📝 权限

为了读取和删除照片，本应用需要以下权限：

- `android.permission.READ_MEDIA_IMAGES` (Android 13+)
- `android.permission.READ_EXTERNAL_STORAGE` (Android 13 以下)
