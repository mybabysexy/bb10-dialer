# BB10 Dialer

[Tiếng Việt](README.md) | [English](README.en.md) | **简体中文**

一款具有 BlackBerry 10 手机外观的 Android 电话应用。使用 Kotlin 和 Jetpack Compose 编写，可设为默认电话应用，专为 Q25、P26 等方屏全键盘手机打造。

这是个人项目，与 BlackBerry 无关。所有图形和图标均由代码重新绘制。

## 截图

截图中的数据均为虚构。

<table>
  <tr>
    <td><img src="docs/screenshots/calls.png" alt="通话列表" width="280"></td>
    <td><img src="docs/screenshots/contacts.png" alt="联系人" width="280"></td>
    <td><img src="docs/screenshots/dial-pad.png" alt="拨号盘" width="280"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/settings.png" alt="设置" width="280"></td>
    <td><img src="docs/screenshots/incoming.png" alt="来电" width="280"></td>
    <td><img src="docs/screenshots/in-call.png" alt="通话中" width="280"></td>
  </tr>
</table>

## 功能

**通话**
- 替代默认电话应用：拨打和接听电话，通话界面全屏显示，锁屏时也一样。
- 拖动接听或拒接，静音铃声。
- 保持、切换和合并通话，查看多方通话成员。
- 通话时写备注，并显示在通话记录中对应的通话下方。
- 通话录音，可在通话记录中对应的通话下方拖动回听。**需要 Root**，详见[通话录音](#通话录音)。

**联系人和通话记录**
- 按日期显示通话记录、快捷联系人，拨号盘会根据输入提示匹配的号码。
- 查看、添加、编辑和删除联系人。
- 在列表顶部下拉可查看每张 SIM 卡的号码和运营商。

**实体键盘**
- 选择设备为 Q25 或 P26 后，字母键可输入键上印的数字。
- 在 Q25 上，通话键可接听来电并呼叫所选号码。

**设置**
- 按号码或号段设置黑名单。
- 隐藏接听滑块、结束通话按钮和已保存联系人的号码。
- 浅色或深色外观，也可跟随系统，并有 6 种强调色。
- 支持英语、越南语和简体中文。

## 通话录音

录音**需要手机已通过 Magisk 或 KernelSU 获取 Root**。没有 Root 时，此功能保持关闭，通话界面也不显示录音按钮。

Android 只允许系统应用录制通话线路，因此应用需要通过一个模块被视为系统应用：

1. 将 BB10 Dialer 设为默认电话应用。
2. 进入**设置 → 通话录音**，轻触**请求 Root 权限**，并在 Root 管理器中允许。
3. 开启**允许通话录音**。如需在通话接通时自动录音，再开启下方的开关。
4. 轻触**录制通话线路**安装模块，然后选择**重启**。
5. 拨打一通测试电话并录音，回听确认双方声音都有。并非所有手机都会输出声音。

**移除：**在同一页面轻触**移除线路录音**，然后重启手机。如果手机卡在启动过程中，请进入 Magisk 安全模式以关闭模块。

**更换签名密钥：**模块保存了一份应用副本，因此更换密钥后必须先移除模块，重新安装应用，再重新安装模块。

各地关于通话录音的法律不同。请自行了解并遵守。

## 尚未完成

**去电和语音信箱**
- 针对无信号、飞行模式和 SIM 卡锁定的专门提示。
- 长按 1 键呼叫语音信箱，以及处理其他应用的 `voicemail:` 请求。
- 从菜单切换电话线路（该菜单项目前已停用）。

**通话中**
- HD、Wi-Fi 标签。
- 视频通话和 RTT：目前仅以音频接听。
- 多步 USSD。
- 在 Zalo、Messenger 通话期间收到蜂窝来电时的专门处理。

**拦截**
- 自动识别骚扰电话：目前只拦截您输入的名单。

## 构建

需要 JDK 21 和 Android SDK 36。应用可在 Android 14（API 34）及以上版本运行。

BB10 界面套件（`duc1607:bb10-kotlin-ui-kit`）来自 Maven local，因此需要先在其仓库中发布：

```bash
./gradlew :bb10-kotlin-ui-kit:publishToMavenLocal
```

然后构建应用：

```bash
./gradlew :app:assembleFast
```

`fast` 是经过优化的版本，使用调试密钥签名，因此可以覆盖安装调试版并保留数据。调试版要慢得多，请不要以它来判断流畅度。

## 支持

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P21MYUO3)

## 许可证

[PolyForm Noncommercial 1.0.0](LICENSE)。以下仅为摘要，以 `LICENSE` 文件的完整文本为准：

- 不得用于商业用途。
- 可出于非商业目的使用、修改和再分发。
- 分发时必须附带许可证，并保留署名作者的 `Required Notice` 行。
