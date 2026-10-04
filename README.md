# Migu No Update Hook

一个用于阻止咪咕快游升级检查与升级弹窗的 LSPosed 模块。

## 适配范围

- 模块 API：LSPosed / libxposed API 102（对应 LSPosed 2.2.0）
- 目标包名（同一应用、不同下载渠道）：
  - `cn.emagsoftware.gamehall`
  - `com.migu.miguplay`
- 当前模块版本：`2.1-api102`（versionCode 101）

包名差异仅来自下载渠道，没有新旧版本之分，模块对两者使用同一份 hook 逻辑。

## 解决的问题

咪咕快游在不同升级阶段会通过以下三个接口拉取升级元数据：

| 接口 | URL 特征 |
|---|---|
| 咪咕版本查询 | `betagame.migufun.com` 下 `/game/version/queryGuideUpgradeVersion` |
| 咪咕 SDK 自升级 | `appipay.migu.cn:8443` 下 `/migusdk/verification/checkUnionSdkUpdate` |
| freeserver 插件清单 | `freeserver.migufun.com` 下 `/resource/beta/package/pluggable.json` |

模块针对上述三组接口统一返回“无更新”数据，从而关闭升级提示、避免被强制升级。

具体两种典型触发：
- 旧版（3.28.1.2 等）保留签到送 2 小时的活动入口，但升级弹窗无法关闭；模块让它收不到升级响应后，弹窗不再触发
- 新版带云电脑功能，但升级后首页“最近游玩”入口被 AI 对话替换；模块保留当前版本的使用方式

## 功能说明

- 通过 LSPosed 静态作用域（`staticScope=true`）加载到咪咕快游相关进程中
- 同时覆盖 `URL.openConnection` / `HttpURLConnection` / `HttpsURLConnection` 及 OkHttp `RealCall.execute` / `enqueue`，不区分 HTTP 库
- 对已知升级接口返回伪造的“无更新” JSON 响应，其他接口完全透传
- 不写日志，不写文件，不主动调用 `XposedBridge.log`

## 使用方式

1. 安装 `outputs/MiguNoUpdateHook-API102.apk`
2. 在 LSPosed 中启用模块
3. 确认作用域勾选 `cn.emagsoftware.gamehall` 与 `com.migu.miguplay`（视你安装的下载渠道选一个或都勾选）
4. 强行停止咪咕快游
5. 重新打开咪咕快游测试

通常不需要重启手机，但必须重启目标 App 进程。

## 构建方式

本项目使用 Windows 本地 Android SDK 工具链构建。在项目根目录的 Git Bash / PowerShell 中运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\work\MiguNoUpdateHookApi102\build_local.ps1
```

构建产物输出到：

```text
outputs/MiguNoUpdateHook-API102.apk
```

脚本默认使用：

- Android SDK：`F:\flutterSDK\android-sdk-windows`
- Build Tools：`35.0.0`
- libxposed API：项目内 `work\libxposed\api-aar\classes.jar`（来自 LSPosed 官方 release 中的 `api-102.0.0.aar`，不随仓库分发，需自行放置）

如你的环境路径不同，需要自行修改 `work/MiguNoUpdateHookApi102/build_local.ps1`。

**获取 libxposed 依赖**：从 [LSPosed](https://github.com/LSPosed/LSPosed/releases) 官方 release 下载 `api-102.0.0.aar`，解压到 `work/libxposed/api-aar/` 即可。

## 项目结构

```text
README.md
LICENSE
.gitignore
outputs/
  MiguNoUpdateHook-API102.apk
work/
  MiguNoUpdateHookApi102/
    AndroidManifest.xml
    build_local.ps1
    src/com/codex/migunoupdatehook102/MiguNoUpdateModule.java
    res/values/strings.xml
    meta/META-INF/xposed/{java_init.list,module.prop,scope.list}
# 不入库：work/libxposed/ 是 LSPosed 官方 AAR 依赖，需自行获取
```

## 注意事项

- 本模块仅为个人学习和兼容性研究用途
- 仅针对已知的咪咕快游下载渠道包做过实机验证
- 不保证对未来版本、其他渠道包、定制包有效
- 咪咕快游如有安全更新通过上述三个接口以外的方式下发，模块可能失效
- 请不要在不了解风险的情况下批量分发或用于商业用途
- 遇到异常时，建议先停用模块并强行停止咪咕快游，再重新验证

## 许可

MIT License