# MistGecko

一款完全在 Android 手机上运行的 AI 图片消除 App。**正式安装包内置 LaMa 模型：只需安装一个 APK，无需另外下载模型、无需 ADB、无需账号，也不需要服务器。** 照片与推理均留在本机，应用没有网络权限。

[下载完整离线 APK](https://github.com/yukinotech/mistgecko/releases/latest) · [发布流程](docs/RELEASING.md) · [架构说明](docs/ARCHITECTURE.md)

## 安装与使用

1. 从 Releases 下载 `MistGecko-版本号-arm64-v8a.apk`，在手机上允许安装该来源的应用。
2. 打开 **MistGecko AI**，选择图片，涂抹需要消除的物体。
3. 点击「开始消除」，按住对比，满意后保存 PNG 到相册。

支持 Android 10+ 的 ARM64 手机。第一次消除会在本机自动准备内置模型，之后断网、重启都可正常使用。清除应用数据后，下次会从安装包重新准备模型，无需联网。模型原文件约 208 MB，首次准备后会保留一份私有副本，安装和更新时请预留额外存储空间。

早期通过 ADB 安装的调试版使用不同签名，不能直接覆盖为正式版；先保存需要的编辑结果，再手动卸载旧调试版。后续同一 Release 签名的正式版可以正常覆盖升级。

## 功能与边界

- 真实 LaMa ONNX 本地 CPU 推理，512×512 模型输入。
- 等比显示、画笔大小、笔画撤销/重做、清空遮罩、连续修复、恢复原图。
- 以遮罩包围盒扩展上下文进行局部推理，仅回填遮罩区域，保留原尺寸及遮罩外像素。
- 图像导入应用方向信息，支持最多 1200 万像素；导出 PNG。

当前没有缩放平移、多步修复结果历史、进程恢复或推理取消。单设备合成测试完整链路约 15 秒；复杂真实照片效果和不同手机性能仍需继续验证。完整覆盖物体的涂抹通常比漏掉边缘更可靠。

## 本地构建完整离线 APK

需要 Python 3.11+、JDK 17、Android SDK 35 / Build Tools 35.0.0，并设置 `ANDROID_HOME`。Gradle Wrapper 已提交。**构建时**需要下载构建依赖及固定版本模型；**App 使用时**不需要网络。

```sh
# 下载并校验固定版本模型；只在构建电脑执行
python3 scripts/prepare_model.py

# 开发调试：同样内置真实 AI 模型
./gradlew assembleAiDebug

# 正式签名包；签名 JSON 保存在仓库外，配置见发布文档
python3 scripts/build_release.py --signing-config /安全目录/signing.json
```

正式产物在 `dist/`：完整 APK、SHA-256 校验文件与第三方说明。脚本会检查签名、内置模型 SHA-256、许可证资源及 ARM64 架构。模型权重不提交 Git，由 `model.lock.json` 锁定来源、版本、大小和哈希。

AI 构建会强制校验模型，缺失或损坏时直接失败，不会生成一个缺模型的 AI 包。仓库另保留 `demo` flavor 供交互调试，使用非 AI 扩散填充，**不作为对外发布版本**。

## 自动发布

已配置 [.github/workflows/release.yml](.github/workflows/release.yml)。推荐操作：修改 `version.properties` 中的版本号及递增版本代码，合并/推送到 `main`。

```properties
versionName=0.1.1
versionCode=2
```

GitHub Actions 会自动校验模型、构建签名完整 APK、运行 Lint、校验产物并创建 `v0.1.1` Release，附上安装包和校验文件。

也支持推送与版本文件一致的 `v*` 标签、手动运行 workflow，或发布对应标签的 GitHub Release。无需每次手动上传 APK；普通代码提交不会自动生成新版本。详见 [RELEASING.md](docs/RELEASING.md)。

## 测试与技术结构

```sh
./gradlew assembleAiDebug assembleAiDebugAndroidTest lintAiDebug
adb install -r app/build/outputs/apk/ai/debug/app-ai-debug.apk
adb install -r app/build/outputs/apk/androidTest/ai/debug/app-ai-debug-androidTest.apk
adb shell am instrument -w -r com.mistgecko.test/com.mistgecko.PipelineInstrumentation
```

设备测试覆盖空遮罩拒绝、边角裁剪、横竖图与细长图、原图不变、遮罩外逐像素保留，以及真实 LaMa 推理。真实模型测试使用合成图，不代表完整照片画质基准。

```text
MainActivity → EditorView                  图片编辑交互
             → ImageStore                 系统选图与相册保存
             → InpaintPipeline            局部裁剪、缩放与遮罩回填
                 → InpaintEngine          后端接口
                     → LaMa / ONNX CPU    正式 AI 版本
```

模型来自 [Carve/LaMa-ONNX](https://huggingface.co/Carve/LaMa-ONNX)，推理使用 [ONNX Runtime](https://github.com/microsoft/onnxruntime)。模型和依赖许可证随 APK 放在 `assets/licenses/`，来源说明见 [THIRD_PARTY.md](docs/THIRD_PARTY.md)。
