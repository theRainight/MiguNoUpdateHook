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

## 构建方法

需要准备：

- JDK（能执行 `javac` 命令）
- Android SDK Platform 35
- Build Tools 35.0.0（含 `aapt2`、`d8`、`zipalign`、`apksigner`）
- libxposed API 102 的 `classes.jar`（从 [LSPosed](https://github.com/LSPosed/LSPosed/releases) 官方 release 获取 `api-102.0.0.aar` 后解压取得）

构建流程（Windows `cmd` / PowerShell 通用）：

```text
1. javac -encoding UTF-8 -source 8 -target 8 ^
       -cp "<android.jar>;<libxposed-api-102.jar>" ^
       -d <classes_dir> ^
       work\MiguNoUpdateHookApi102\src\com\codex\migunoupdatehook102\MiguNoUpdateModule.java

2. cd <classes_dir> && jar cf ..\module-classes.jar . && cd ..

3. d8 --lib <android.jar> --min-api 26 ^
       --output <out_dir> module-classes.jar

4. aapt2 compile --dir work\MiguNoUpdateHookApi102\res ^
       -o <out_dir>\compiled.zip

5. aapt2 link -o unsigned.apk ^
       --manifest work\MiguNoUpdateHookApi102\AndroidManifest.xml ^
       -I <android.jar> ^
       <out_dir>\compiled.zip ^
       --min-sdk-version 26 --target-sdk-version 35 ^
       --version-code 101 --version-name 2.1-api102 ^
       --auto-add-overlay

6. jar uf unsigned.apk -C work\MiguNoUpdateHookApi102\meta META-INF

7. jar uf unsigned.apk -C <out_dir> classes.dex

8. zipalign -p -f 4 unsigned.apk aligned.apk

9. apksigner sign --ks <keystore> --ks-pass pass:<pwd> --key-pass pass:<pwd> ^
        --out outputs\MiguNoUpdateHook-API102.apk aligned.apk

10. apksigner verify --verbose outputs\MiguNoUpdateHook-API102.apk
```

任意 `<...>` 占位符请替换为你本地实际路径。`work\MiguNoUpdateHookApi102\meta\META-INF\xposed\{module.prop,scope.list,java_init.list}` 决定模块在 LSPosed 中的注册信息，构建时必须包含。

构建产物统一输出到 `outputs/MiguNoUpdateHook-API102.apk`。

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
    src/com/codex/migunoupdatehook102/MiguNoUpdateModule.java
    res/values/strings.xml
    meta/META-INF/xposed/{java_init.list,module.prop,scope.list}
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