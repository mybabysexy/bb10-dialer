# BB10 Dialer

[Tiếng Việt](README.md) | **English** | [简体中文](README.zh-CN.md)

A phone app for Android with the look of a BlackBerry 10 phone. Written in Kotlin and Jetpack Compose, it can be your default phone app, and it is made for square-screen phones with a hardware keyboard such as the Q25 and P26.

This is a personal project, not affiliated with BlackBerry. All artwork and icons are redrawn in code.

## Screenshots

The data in these pictures is made up.

<table>
  <tr>
    <td><img src="docs/screenshots/calls.png" alt="Call list" width="280"></td>
    <td><img src="docs/screenshots/contacts.png" alt="Contacts" width="280"></td>
    <td><img src="docs/screenshots/dial-pad.png" alt="Dial pad" width="280"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/settings.png" alt="Settings" width="280"></td>
    <td><img src="docs/screenshots/incoming.png" alt="Incoming call" width="280"></td>
    <td><img src="docs/screenshots/in-call.png" alt="In a call" width="280"></td>
  </tr>
</table>

## Features

**Calls**
- Replaces the default phone app: place and answer calls, with a full-screen call screen, also on the lock screen.
- Drag to answer or decline, silence the ringer.
- Hold, swap and merge calls, with a list of the people in a conference.
- Notes during a call, shown under that call in the history.
- Call recording, with seeking, under that call in the history. **Requires root**, see [Call recording](#call-recording).

**Contacts and history**
- Call history by day, quick contacts, and a dial pad that suggests matches as you type.
- View, add, edit and delete contacts.
- Pull down at the top of a list to see each SIM's number and carrier.

**Hardware keyboard**
- Choose the Q25 or P26 as your device, and the letter keys type the digits printed on them.
- On the Q25, the Call key answers and calls the selected number.

**Settings**
- A blacklist of numbers or prefixes.
- Hide the answer slider, the End Call button, and the numbers of saved contacts.
- A light or dark theme, or one that follows the system, and 6 accent colors.
- English, Vietnamese and Simplified Chinese.

## Call recording

Recording **requires a phone rooted** with Magisk or KernelSU. Without root it stays off and the call screen has no Record button.

Android only lets system apps record the phone line, so the app needs a module to count as one:

1. Set BB10 Dialer as your default phone app.
2. Go to **Settings → Call Recording**, tap **Request Root Access** and allow it in your root manager.
3. Turn on **Allow Recording Calls**. To record automatically when a call connects, also turn on the switch below it.
4. Tap **Record the Phone Line** to install the module, then choose **Restart**.
5. Make a test call, record it and listen back to check that both sides are there. Not every phone passes the sound on.

**To remove:** on the same page, tap **Remove Line Recording** and restart the phone. If the phone gets stuck while starting, boot into Magisk's safe mode to turn the module off.

**Changing the signing key:** the module holds a copy of the app, so after changing the key you must remove the module, reinstall the app and install the module again.

Laws on recording calls vary by where you live. Checking and following them is your responsibility.

## Not done yet

**Outgoing calls and voicemail**
- Specific messages for no signal, airplane mode and a locked SIM.
- Holding the 1 key to call voicemail, and handling `voicemail:` requests from other apps.
- Changing the phone line from the menu (the item is disabled).

**During a call**
- HD and Wi-Fi labels.
- Video and RTT calls: currently answered with audio only.
- Multi-step USSD.
- Special handling for a cellular call arriving during a Zalo or Messenger call.

**Blocking**
- Detecting spam automatically: currently only the list you enter is blocked.

## Build

Requires JDK 21 and Android SDK 36. The app runs on Android 14 (API 34) and later.

The BB10 UI kit (`duc1607:bb10-kotlin-ui-kit`) comes from Maven local, so publish it first from its own repository:

```bash
./gradlew :bb10-kotlin-ui-kit:publishToMavenLocal
```

Then build the app:

```bash
./gradlew :app:assembleFast
```

`fast` is the optimized build, signed with the debug key so that it installs over a debug build and keeps its data. The debug build is much slower, so don't judge smoothness by it.

## Support

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P21MYUO3)

## License

[PolyForm Noncommercial 1.0.0](LICENSE). In short, and only the full text in `LICENSE` is binding:

- No commercial use.
- You may use, modify and redistribute it for noncommercial purposes.
- When distributing, include the license and keep the `Required Notice` line that credits the author.
