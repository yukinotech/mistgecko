# 完整离线包与 GitHub Release

## 固定方案

正式分发仅发布 `aiRelease`、ARM64、内置 LaMa 的签名 APK。不发布需要用户导入权重的版本。下载地址为 GitHub Releases；无需图片服务器或模型下载服务。

应用自身没有网络权限。CI 和开发电脑在构建时访问 Maven、Gradle 和 Hugging Face，模型来源/修订/大小/哈希固定在 `model.lock.json`。CI 缓存只为加速，构建始终重新校验模型。Apache-2.0、ORT MIT 与第三方声明包含在安装包中。

## 日常发版（推荐）

1. 更新 `version.properties` 的 `versionName`（三段数字）和 `versionCode`（严格递增）。
2. 将修改合并到 `main`。
3. `Offline Android Release` workflow 自动创建对应 `v版本号` 的 Release 并上传完整 APK、SHA-256 和第三方来源说明。

首次发版使用 `0.1.0 / 1`。普通 main 代码变更不会重复发版；只有版本文件变更触发。若源码改动但忘记升版本，可在升版本后重新发版。

其他入口：

```sh
# 版本文件应先更新并合并到 main
 git tag v0.1.1
 git push origin v0.1.1

# 手动重跑当前默认分支版本（该版本标签如已存在，必须指向同一提交）
 gh workflow run release.yml --ref main
```

也支持在 GitHub 网页发布一个标签与版本文件一致的 Release，Actions 会补齐附件。推荐使用 main 版本文件入口，这样只在构建验证通过后公开新 Release。网页提前发布的 Release 如构建失败会暂时没有附件，需要修复后重跑。

工作流只发布 `main` 历史中的提交。已有版本标签不能指向其他提交；应升新版本，禁止强制移动标签。自动使用 `GITHUB_TOKEN` 创建的 Release/标签不会递归触发相同发布流程。

## CI 签名配置

仓库 Secrets：

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 固定 Release keystore 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

首次配置已通过 `gh secret set` 完成。密钥及密码不在仓库、构建附件或日志里。Runner 临时恢复 keystore，构建结束删除。workflow 使用 `contents: write` 发布，不需要个人访问令牌。

维护者本机私有签名配置在 `~/.config/mistgecko/signing.json`，密钥在同目录 `release.jks`，需将整个目录另行加密备份。**Secrets 无法读回签名密钥，不要把 GitHub 当成唯一备份。** 此密钥用于今后覆盖升级；不要每次发版重新生成。

本地 JSON 结构（不要提交密码或密钥）：

```json
{
  "MISTGECKO_KEYSTORE": "/安全目录/release.jks",
  "MISTGECKO_STORE_PASSWORD": "你的密码",
  "MISTGECKO_KEY_ALIAS": "mistgecko",
  "MISTGECKO_KEY_PASSWORD": "你的密码"
}
```

也可以直接设置同名环境变量后运行 `python3 scripts/build_release.py`。缺少签名配置时正式构建失败，不回退到调试签名。APK 是普通 Android Release 安装包，无需 ADB。

## 模型更新

更新 `model.lock.json` 的修订、URL、大小、SHA-256，然后执行 `scripts/prepare_model.py`。上游新权重必须先真机验证再发布。APK 内有 `model.json`，运行时按哈希命名私有模型副本：首次/模型升级时从 assets 复制到临时文件，校验后原子替换，成功后才清理旧权重。复制失败可以下次重试；不会留下半个可加载模型。清除数据后可从 APK 重建。

## 验收

- 构建脚本校验 APK 内模型哈希、ARM64 native 库、许可证与 APK 签名。
- CI 运行 Release Lint；不会把静态检查标为真机画质测试。
- 发布前应在干净安装状态验证断网推理、保存、旋转与失败恢复。
- 调试版和正式版签名不同，不能直接覆盖；用户需先导出需要保留的图片后手动卸载调试版。不得为了安装自动删除用户数据。
- 后续正式版在相同包名/签名且 versionCode 递增时可以覆盖升级。
