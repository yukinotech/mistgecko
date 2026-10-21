# 第三方与模型来源

本项目自行实现编辑器与图像管线，没有复制 IOPaint 或参考 Android App 的源码。正式 AI APK 内置以下模型与运行库，许可证文本随 APK 放在 `assets/licenses/`。

| 组件 | 用途 | 版本/来源 | 许可 |
| --- | --- | --- | --- |
| Carve LaMa FP32 ONNX | 内置图片修复模型 | revision `c3c0c9e468934d62e79c329e35d82dd09ff8c444`，https://huggingface.co/Carve/LaMa-ONNX | 模型卡声明 Apache-2.0 |
| ONNX Runtime Android | 本地推理 | 1.23.2，https://github.com/microsoft/onnxruntime | MIT；完整 ThirdPartyNotices 随包提供 |
| Kotlin / 标准库 | 应用语言运行时 | 2.2.20，https://github.com/JetBrains/kotlin | Apache-2.0 |
| Gradle Wrapper | 构建工具，不随 APK 分发 | 8.14，https://github.com/gradle/gradle | Apache-2.0 |

模型源自 LaMa：https://github.com/advimman/lama 。ONNX 导出/Fourier 实现：https://github.com/Carve-Photos/lama 。模型字节未修改；App 提供自己的预处理、遮罩膨胀和回填逻辑。模型哈希和来源固定在 `model.lock.json`。

许可证资源：Apache-2.0 全文、ONNX Runtime MIT 全文与完整第三方声明、模型归属说明。未来引入 MI-GAN 或新的运行库时需同步更新许可证与权重来源。
