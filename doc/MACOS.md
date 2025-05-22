## macOS notes

Like [Linux](LINUX.md) and [Windows](WINDOWS.md), macOS has some specific concerns and idiosynchrasies that Jaunch must accommodate.

### The main thread's CoreFoundation event loop

Many GUI paradigms have some kind of [event loop](https://en.wikipedia.org/wiki/Event_loop) to organize all of the operations happening in the interface. Java has its AWT Event Dispatch Thread (EDT), Qt has `QEventLoop`, the X Window System has the Xlib event loop (see also the [XInitThreads discussion in LINUX.md](LINUX.md#xinitthreads))... but none of them have challenged this developer nearly so much as macOS's requirement that a [Core Foundation event loop](https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/Multithreading/RunLoopManagement/RunLoopManagement.html#//apple_ref/doc/uid/10000057i-CH16) be running on the process's main thread. Apple tries to make this requirement as invisible as possible... if you are writing an Objective-C program with XCode. But Jaunch is a cross-platform launcher written in pure C, so it must spin up this event loop itself, then launch the actual main program on a separate pthread.

The main reason for this need is that starting Java directly on the process's main thread results in deadlocks when subsequently initializing Java's graphical AWT subsystem. If your Java program does not do anything with AWT, you might be fine, but if you want to display any GUI elements, your app will freeze.

The same problem occurs when attempting to use Java in-process from Python via a library like [JPype](https://www.jpype.org/): as soon as you invoke any Java AWT code from your Python script, it hangs. To overcome this limitation, JPype provides a `setupGuiEnvironment` function that uses `AppHelper.runConsoleEventLoop()` of `PyObjCTools` to start the Core Foundation event loop on the main thread... but then your Python program is subsequently blocked forever; you must pass the code you want executed as a callback function to the `setupGuiEnvironment` function, for it to be executed while the main thread event loop is running. Not only is PyObjC a hassle to install into a Python environment, this limitation also creates [serious obstacles](https://github.com/imagej/pyimagej/issues?q=label%3Amacos-gui) to unleashing the full power of Python+Java combined, e.g. operating on Java GUI elements interactively in a Python REPL as can be done on Linux or Windows.

Fortunately, because Jaunch on macOS explicitly starts the main thread's event loop, then runs the main program on a separate pthread, Java AWT works, and can be freely used from Python scripts. That said, we are still working out some use cases around combination with other tools, such as combining Java GUI usage with Qt-based projects (e.g. [napari](https://napari.org/)).

### Code signing

#### How macOS protects users from malware

* The [Gatekeeper](https://en.wikipedia.org/wiki/Gatekeeper_%28macOS%29) security layer checks every program you launch to discern whether it is safe to run.
* If the program is not crypticographically signed by its developer, the program launch is rejected<sup>1</sup>.
* If the program *is* code-signed but the developer has not submitted it to Apple for so-called *notarization*, the program launch is rejected<sup>1</sup>.
* If the program *has* been notarized, but the program or developer is no longer in good standing with Apple, the program launch is rejected.
* After launching, if the program attempts to perform certain actions without having requested corresponding so-called *entitlements* that signal its need to do so, the program will crash.<sup>2</sup>

For this system to work:
* **For users:** macOS must contact Apple servers whenever you launch a code-signed application in order to verify its standing as a safe application. [Every program you run gets reported to Apple](https://sneak.berlin/20201112/your-computer-isnt-yours/).
* **For developers:** you must [code-sign and notarize your application](https://developer.apple.com/documentation/xcode/packaging-mac-software-for-distribution) for it to launch successfully on macOS<sup>1</sup>. To code-sign your application, you must secure a signing certificate from Apple, which requires joining the [Apple Developer Program](https://developer.apple.com/programs/), which costs $99 USD per year.

<details><summary><sup>1</sup> Garbage programs</summary>

If you distribute your application unsigned or unnotarized, macOS will literally tell your users that the program is garbage, saying the app "is damaged and can't be opened. You should move it to the Trash/Bin." Users can [disable Gatekeeper with a Terminal command](https://www.makeuseof.com/how-to-disable-gatekeeper-mac/), at least as of [macOS Sequoia](https://en.wikipedia.org/wiki/MacOS_Sequoia), but instructing users of your applications to do so is unlikely to be reassuring for them.

</details>

<details><summary><sup>2</sup> Entitlements <strike>rant</strike>challenges</summary>

For example, if your application tries to load a shared library signed with a different signature than yours, and your app has not declared the `com.apple.security.cs.disable-library-validation` entitlement during the code signing process, the program will crash. And then, if your app did not declare the `com.apple.security.cs.debugger` entitlement, all attempts to debug why it crashed will fail, because no debugger will be able to be attached, and Apple's Console tool will not report the real reason for the crash. Even with the aforementioned entitlements set, loading of unsigned libraries is right out: there is no entitlement to make that possible.

</details>

#### How Jaunch deals with Gatekeeper

Jaunch addresses the macOS code-signing + notarization issue in two ways:

1. **Pre-signed binaries usable out of the box.** Jaunch releases are distributed code-signed and notarized by [the lead Jaunch developer](https://github.com/ctrueden), with [all the entitlements needed](../configs/entitlements.plist) to run Python and Java programs. If you do not need to customize your application's `Info.plist` manifest, and do not need to give your application an icon, and that set of entitlements is sufficient, you can use Jaunch's prebuilt binaries directly.

2. **Tooling to ease app construction and signing.** In cases where your application needs to have its own icon, or customize its manifest, or request different entitlements, you will need to construct your own .app and re-sign and re-notarize the result with your own developer certificate. To help you do that more easily, Jaunch includes a suite of shell scripts to ease the process.

The next section offers step-by-step instructions for the second path: signing and notarizing your customized app launcher.

#### How to sign your application's Jaunch launcher

1. Construct your .app bundle by following the instructions in [SETUP.md](SETUP.md). Make sure the application works on your local computer before proceeding further.

2. Join the Apple Developer Program:
   * In your web browser, navigate to [developer.apple.com](https://developer.apple.com/).
   * Click the "Account" link on the right side of the top menu.
   * Sign in with your Apple account, or click "Create yours now" if you don't already have one.
   * Once you are signed in, figure out how to join the Apple Developer Program. It will cost you $99 USD.

3. Create and install an application certificate:
   * Navigate back to [developer.apple.com/account](https://developer.apple.com/account).
   * In the Certificates, IDs & Profiles section, click the "Certificates" link.
   * Take note of your *developer ID code*, shown in the top right corner of this page under your name; it will look something like `Penelope Realperson - XY1Q234ABC`.
   * Create a new certificate, selecting "Developer ID Application" under the "Software" section.
   * Follow the prompts to generate and download the certificate. You will need to [create a certificate signing request](https://developer.apple.com/help/account/create-certificates/create-a-certificate-signing-request) using the Keychain Access tool.
   * After downloading the certificate, double-click the .cer file to install it into your Keychain. Be sure to choose the ***login*** keychain, rather than *System*.

4. Create an app-specific password:
   - Navigate to your [Apple Account security settings](https://account.apple.com/account/manage/section/security).
   - Click into the "App-Specific Passwords" section.
   - Click the `+` to add a password named `notarytool`.
   - Write down the app-specific password; it will be of the form `abcd-efgh-ijkl-mnop`.

5. Store the app-specific password into your Keychain:
   ```shell
   xcrun notarytool store-credentials notarytool-password \
       --apple-id penelope@realperson.name \
       --team-id XY1Q234ABC \
       --password abcd-efgh-ijkl-mnop
   ```
   replacing `penelope@realperson.name` with your actual Apple ID, and
   replacing `XY1Q234ABC` with your actual team ID from step 3, and
   replacing `abcd-efgh-ijkl-mnop` with your actual app-specific password from step 4.
   Be aware that app-specific passwords eventually expire, at which point you will need to repeat steps 4 and 5.

6. Run Jaunch's code-signing script:
   ```shell
   export DEV_ID="Penelope Realperson (XY1Q234ABC)"
   ~/jaunch-1.0.3/bin/sign.sh /path/to/MyApp.app
   ```
   Where your actual `DEV_ID` value is your developer ID info from step 3, and
   `/path/to/MyApp.app` is the location of the .app bundle generated in step 1.

   *Note: The `sign.sh` script assumes your app-specific password is stored
   under the credential `notarytool-password`, as shown in step 5. If you
   deviate from this name, you must modify the `sign.sh` script accordingly.*

   If all goes well, the script will code-sign, verify, and notarize your application.

P.S. Here are links to Apple documentation about this process:
* [Code signing tasks](https://developer.apple.com/library/archive/documentation/Security/Conceptual/CodeSigningGuide/Procedures/Procedures.html)
* [Customizing the notarization workflow](https://developer.apple.com/documentation/security/customizing-the-notarization-workflow)

### Path randomization

> Starting in OS X v10.12, you can no longer provide external code or data
> alongside your code-signed app in a zip archive or unsigned disk image. An
> app distributed outside the Mac App Store runs from a randomized path when it
> is launched and so cannot access such external resources.

&mdash;"What's New in OS X" circa 2016

In addition to Gatekeeper's requirement that apps be code-signed and notarized, it employs a security feature known as [path randomization](https://en.wikipedia.org/wiki/Gatekeeper_%28macOS%29#Path_randomization) when launching an app downloaded from the Internet. The app is copied into a random directory just before launching, with the goal of making it unable to access sibling resources. Unfortunately, such resources (e.g. the TOML configuration) are exactly what Jaunch needs to successfully launch the application.

To work around this difficulty, Jaunch includes logic to [*"untranslocate"*](https://objective-see.org/blog/blog_0x15.html) itself upon first launch. It works by calling the internal `SecTranslocateIsTranslocatedURL` and `SecTranslocateCreateOriginalPathForURL` functions, which are part of the macOS security framework. The latter function reports the *original* location of the app rather than the transient translocated location; with this information, Jaunch can then remove the `com.apple.quarantine` attribute from the original app bundle, then relaunch it, thus avoiding future translocation by Gatekeeper.
