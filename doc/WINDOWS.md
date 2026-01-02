## Windows notes

Like [Linux](LINUX.md) and [macOS](MACOS.md), Windows has some specific concerns and idiosynchrasies that Jaunch must accommodate.

### Console and GUI executables

On Unix-like systems, all executables attach to the console from which they are launched; if no console is present, no new one is created.

Windows is different: when building your EXE file, you must choose between targeting the *console* subsystem or the *GUI* subsystem. Which one you choose impacts how your program behaves with respect to the console:

* A **console** program will attach to the parent process's console when it launches, as happens in Unix-like environments. This means:
  - The console shell will *block* while the program runs, not offering a new shell prompt until after the program terminates.
  - You will see output from the program's standard output and standard error streams in the console as the program produces it.
  - You can type keystrokes while the console has the focus, and the program will receive them on its standard input stream.

  Unlike Unix-like environments, however, if the executable is launched from a non-shell context, such as double-clicking the EXE file from the File Explorer interface, a *new console* linked to the just-launched program will appear in a new window.

* A **GUI** program normally has no console. As with typical windowing systems of Unix-like environments (e.g. GNOME or KDE), if launched from a non-shell context such as File Explorer, the program's GUI (if any) will appear, but no new console gets created. Even if launched from a shell like PowerShell or Command Prompt, though, the program will start up but not block the shell prompt, returning control immediately to the shell&mdash;and crucially, it does not inherit the shell's console by default, meaning you will not see the program's standard output or error streams within the shell used to launch.

A consequence of this dichotomy is it becomes tricky to create a graphical program that also functions as a command-line tool.

#### Inspecting executables

The `file` command, available as part of MinGW and easily accessible via [Git Bash](https://gitforwindows.org/#bash), is one simple way to inspect an EXE file to determine whether it was built in console mode or GUI mode:

```shell
$ file app/jy-windows-x64.exe
app/jy-windows-x64.exe: PE32+ executable (console) x86-64, for MS Windows, 3 sections
$ file app/hello-windows-x64.exe
app/hello-windows-x64.exe: PE32+ executable (GUI) x86-64, for MS Windows, 3 sections
```

#### Workarounds

What we'd really like is for our Windows EXE to behave the same way as Unix-like systems: inheriting the shell console when launched from a shell, but *not* spawning a new console when launched from outside a shell. Alas, it is not meant to be: Windows simply does not work that way. Fortunately, there are workarounds:

* You can launch a GUI executable from a shell in "wait mode":
  - In Command Prompt, launch the GUI executable with:
    ```cmd
    start /wait myProgram.exe
    ```
  - Or in PowerShell, use:
    ```powershell
    Start-Process -Wait .\myProgram.exe
    ```

* With either shell, you can run a batch file that wraps the GUI executable invocation, effectively converting it into a console program. Jaunch's [demo examples](EXAMPLES.md) offer such a batch file for each example app. (Thanks to [this blog post](https://lastpixel.tv/win32-application-as-both-gui-and-console/) for the tip!)

* You can ship two launchers with your application: one for each mode. This is what Python does with `python.exe` (console) and `pythonw.exe` (GUI), and what OpenJDK does with `java.exe` (console) and `javaw.exe` (GUI). For this reason, the Jaunch native launcher is compiled in both GUI and console modes, and you can choose which of the two modes (or both) you want to ship with your application. (In case you were wondering: the configurator portion of Jaunch is always built in console mode only; when the Jaunch native launcher invokes it as a separate process, it does so with the `CREATE_NO_WINDOW` flag so that the configurator's console does not spawn a visible window.)

#### Attaching to the parent console

Windows programs compiled in GUI mode do not attach to the calling process's console by default *even in wait mode*; it must be done explicitly using the [`AttachConsole`](https://learn.microsoft.com/en-us/windows/console/attachconsole) function. Fortunately, the Jaunch native launcher does this, so that the above tricks (wait mode and batch file wrappers) work as desired.

Unfortunately, even with `AttachConsole`&mdash;and even if we go further and use `freopen` to reopen the stdin/stdout/stderr, the invoked launcher is still in a bad state when run directly from the shell (without wait mode and without wrapping in a batch file). On Windows, consoles can have multiple processes attached to them and "there is no guarantee that input is received by the process for which it was intended" ([source](https://learn.microsoft.com/en-us/windows/console/creation-of-a-console)); when control returns immediately to the shell before the launched program terminates, and then keystrokes are typed, each character gets sent to either the launched process or the shell process in a haphazard way, effectively splitting the input between the processes, resulting in a big mess.

#### PowerShell, Command Prompt, and Git Bash

Unlike on macOS and Linux, where POSIX-friendly shells like bash and zsh are the norm, Windows comes equipped with two different terminal programs, PowerShell and Command Prompt, which have very different syntax from POSIX shells and from each other. Fortunately, the [MSYS2](https://www.msys2.org/) and [Cygwin](https://cygwin.com/) projects both provide a bash shell for Windows; one easy way to install bash on Windows is via the awesome [Git for Windows](https://gitforwindows.org/) project, which comes equipped with a [Git BASH](https://gitforwindows.org/#bash) tool built on MSYS2.

This diversity of technological riches leaves Jaunch in the awkward position of attempting to work properly across all of these various shells, and documenting the quirks of each.

As of this writing, Jaunch detects whether it was launched from Command Prompt (`cmd.exe`), PowerShell (`powershell.exe` or `pwsh.exe`), Bash (`bash.exe`), Windows Explorer (`explorer.exe`), or something else.

Jaunch always attempts to attach to its parent process's existing console, if any. But there are some differences depending on the shell it was launched from:

* **PowerShell**: When Jaunch is running in GUI mode, it warns that `Start-Process -Wait` should be used to avoid console contamination as described above. Unfortunately, as of this writing, Jaunch is not smart enough to recognize whether its launch was in fact wrapped in this way; if you know how to detect it, pull requests are very welcome!

* **Command Prompt**: When Jaunch is running in GUI mode, it warns that `start /wait` should be used to avoid console contamination as described above. Unfortunately, as of this writing, Jaunch is not smart enough to recognize whether its launch was in fact wrapped in this way; if you know how to detect it, pull requests are very welcome!

* **Bash**: With most parent types, after successfully attaching to the parent's existing console, the `freopen` function must be used to reconnect to that console's standard I/O streams to make Jaunch's input and output work. But when run from Bash, the opposite is true: using `freopen` makes Jaunch's I/O streams *stop* working! So Jaunch makes a best effort to invoke `freopen` or not depending on the parent shell. This approach still fails when running `bash.exe` from inside a PowerShell or Command Prompt, though&mdash;if you know how to make that scenario work, pull requests are very welcome!

* **Unknown parent type**: When Jaunch is running in GUI mode, it warns that it could not detect what kind of parent it has, and that console output may be contaminated as described above.

As of this writing, Jaunch has not been tested in Cygwin's bash, or any other Windows shell not mentioned above (with the exception of WSL, which doesn't count because it is actually Linux).

#### Recommendations

If you want to provide a hybrid GUI+console application, we recommend using the GUI mode executable to prevent extraneous consoles from appearing, and shipping a batch file wrapper (the Jaunch distribution's `launcher.bat` should work unmodified&mdash;just rename it) for use from the command line with proper console behavior. Or else ship both the GUI and console mode executables, like Python and Java do.

### Code signing

#### How Windows protects users from malware

* The [Windows Defender SmartScreen](https://en.wikipedia.org/wiki/Microsoft_SmartScreen) security layer checks every program you launch to discern whether it is safe to run.
* If the program is not crypticographically signed by its developer, the program launch is blocked<sup>1</sup>.
* If the program *is* code-signed but its certificate has not accumulated sufficient [reputation](https://www.digicert.com/blog/ms-smartscreen-application-reputation), the program launch is blocked<sup>1</sup>.
* The program accumulates reputation by being launched by many people over time. The program's reputation score can also be boosted by submitting the program to Microsoft's anti-malware scanner.
* When the code signing certificate expires and is renewed, and a new version of the program is released using the renewed certificate, the [program must start again to build reputation from zero](https://security.stackexchange.com/q/222140) (although there are [hacky ways to avoid the issue](https://stackoverflow.com/q/58213713/1207769)).

For this system to work:
* **For users:** Windows must contact Microsoft servers when you launch an application, in order to verify its standing as a safe program. Every application you run&mdash;and much of your other day-to-day computing actions and behavior&mdash;gets reported to Microsoft.<sup>2</sup>
* **For developers:** you can buy a code-signing certificate from a third party, and use it to code-sign your applications (EXE files). But the process is confusing and complicated.<sup>3</sup> Even after code signing, users will still be blocked<sup>1</sup> by SmartScreen until your certificate achieves sufficient reputation.<sup>4</sup>

<details><summary><sup>1</sup> SmartScreen blocks program launches</summary>

In this context, "blocked" means that the launch is interrupted with a dialog box saying that "Windows protected your PC" and "prevented an unrecognized app from starting. Running this app might put your PC at risk." The only option to dismiss the dialog is a "Don't run" button; to actually run the program, you must click the "More info" link written in a smaller font below the message, at which point a "Run anyway" button becomes available. See [this blog post](https://www.beholdgenealogy.com/blog/?p=3823) for a clear walkthrough with screenshots of the difficulties.

</details>

<details><summary><sup>2</sup> Windows privacy <strike>rant</strike>concerns</summary>

Microsoft [claims](https://www.microsoft.com/en-us/privacy/faq) to be "committed to giving customers transparency and control over their data," but Windows silently sends many of your activities to Microsoft by default, including the programs you run and the things you type into the [built-in search feature](https://support.microsoft.com/en-us/windows/search-for-anything-anywhere-b14cc5bf-c92a-1e73-ea18-2845891e6cc8).
When they do offer the choice to opt out of activity reporting, they use persuasive language such as "Windows is better when settings and files automatically sync" and describing being opted out as a "limited experience."
It is technically possible to disable most reporting of your activities, by [circumventing the requirement to use a Microsoft account for login](https://www.reddit.com/r/GeekSquad/comments/1chkoy2/solutions_for_microsofts_forced_ms_account_at/) in order to [use a local account instead](https://www.youtube.com/watch?v=pIRNpDvGF4w), then working your way through the 33 different sections of [Microsoft's 11,000-word telemetry management guide](https://learn.microsoft.com/en-us/windows/privacy/manage-connections-from-windows-operating-system-components-to-microsoft-services#how-to-configure-each-setting). Or you can trust Microsoft to be a responsible steward of your activities data, even though they [historically haven't been](https://en.wikipedia.org/wiki/Criticism_of_Microsoft#Privacy_issues), and the increasing focus on AI is [exacerbating the issue](https://doublepulsar.com/recall-stealing-everything-youve-ever-typed-or-viewed-on-your-own-windows-pc-is-now-possible-da3e12e9465e).

</details>

<details><summary><sup>3</sup> Challenges of purchasing a code-signing certificate</summary>

Figuring out what to buy, and from whom, is its own challenge. Certificate prices vary, anywhere between €49 to €379 per year or more, and depending on which option you choose, you need either a physical piece of hardware or a cloud-based code signing mechanism (see [this SO post](https://stackoverflow.com/q/47284317/1207769) for details). There are [many vendors](https://en.wikipedia.org/wiki/Certificate_authority#Providers) selling various kinds of certificates including OV (Organization Validated), EV (Extended Validation), and IV (Individual Validated) varieties. Certificates are used for other purposes [beyond only code signing](https://en.wikipedia.org/wiki/Public_key_certificate), and the complexity of the cryptography industry has given rise to an ample niche of jargon&mdash;
[CA](https://en.wikipedia.org/wiki/Certificate_authority),
[SSL](https://en.wikipedia.org/wiki/Secure_Sockets_Layer),
[TLS](https://en.wikipedia.org/wiki/Transport_Layer_Security),
[X.509](https://en.wikipedia.org/wiki/X.509),
[PKI](https://en.wikipedia.org/wiki/Public_key_infrastructure),
[CMP](https://en.wikipedia.org/wiki/Certificate_Management_Protocol),
[EST](https://en.wikipedia.org/wiki/Enrollment_over_Secure_Transport),
[OCSP](https://en.wikipedia.org/wiki/Online_Certificate_Status_Protocol),
[CRL](https://en.wikipedia.org/wiki/Certificate_revocation_list),
[RSA](https://en.wikipedia.org/wiki/RSA_%28cryptosystem%29),
[DSA](https://en.wikipedia.org/wiki/Digital_Signature_Algorithm),
[ECDSA](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm),
[HSM](https://en.wikipedia.org/wiki/Hardware_security_module),
[TPM](https://en.wikipedia.org/wiki/Trusted_Platform_Module),
[FIPS 140 Level 2-certified product](https://support.globalsign.com/code-signing/code-signing-best-practices),
etc.&mdash;making it more difficult to find and understand relevant information on this topic.

</details>

<details><summary><sup>4</sup> Signed apps are still initially blocked.</summary>

It [can take 2 to 8 weeks](https://stackoverflow.com/a/65653792/1207769) to achieve enough reputation for SmartScreen warnings to stop&mdash;although there are [ways to speed up the process](https://stackoverflow.com/a/66582477/1207769) including submitting your app to Microsoft's anti-malware scanner, and/or paying much more money for an EV (Extended Validation) certificate if you happen to be a business entity. Unfortunately, how much various actions help is not fully known, as Microsoft [does not make it easy for developers to know their certificate's reputation](https://stackoverflow.com/q/19767462/1207769), nor exactly what the requirements are to satisfy the SmartScreen criteria.

</details>

#### How Jaunch deals with SmartScreen

Jaunch addresses the Windows code-signing + reputation issue in two ways:

1. **Pre-signed binaries usable out of the box.** Jaunch releases are distributed code-signed by [the lead Jaunch developer](https://github.com/ctrueden), with all the associated reputation, so your app doesn't have to start from zero. If you do not need to embed icons into your application's EXE files, you can use Jaunch's prebuilt binaries directly.

2. **Tooling to ease app construction and signing.** In cases where your application does need to have its own icon, you will need to re-sign your launcher EXEs with your own developer certificate. To help you do that more easily, Jaunch includes a suite of shell scripts to ease the process.

The next section offers step-by-step instructions for the second path: re-signing your customized app launcher.

#### How to sign your application's Jaunch launcher

*Note: Due to the heterogeneity of certificate vendors, these instructions describe only one particular path to acquiring and using a code-signing certificate: the one Jaunch uses for its release binaries. You can certainly do your own research and make different choices, but if you just want to get up and running quickly and cheaply, the instructions here should serve you well.*

1. Construct your app bundle by following the instructions in [SETUP.md](SETUP.md). Among other helpful actions, the appify script will embed your app's custom icon into the launcher EXEs. Make sure the application works on your local computer before proceeding further.

2. Buy an [Open Source Code Signing in the Cloud](https://shop.certum.eu/open-source-code-signing-on-simplysign.html) certificate from [Certum](https://www.certum.eu/en/). It costs €49 per year.
   - Before any certificates can be activated, you must [complete the identity verification process](https://support.certum.eu/en/how-to-start-the-automatic-identity-verification/).
   - You will receive an email with subject "Identity verification" which includes a link taking you to the Certum website where you can generate a verification link. From the email:
     > * Verification is performed using the IDnow system.
     > * The link will allow you to initiate the verification process once and will be valid for 24 hours.
     > * For the verification you need a device with a camera and access to the Internet - we recommend performing the verification on a mobile device such as a smartphone. 
     > * The entire verification process will not take more than 5 minutes.
   - It may take a few days for the verification to be completed.
   - You might receive an email requesting any needed adjustments to the certificate; for example, issuing Jaunch's certificate required confirming a change from `SP=WI` to `SP=Wisconsin`.
   - Once everything is in order, you will receive another email with subject "Welcome to Certum!"
   - You will then finally be able to finish [activating your "Open Source Code Signing in the Cloud" certificate](https://support.certum.eu/en/how-to-activate-a-code-signing-certificate/).

3. Install the [SimplySign Desktop application](https://support.certum.eu/en/installation-of-the-simplysign-application/) on the Windows system you plan to use for code signing. The software to download is **proCertum SmartSign** in the "Software and libraries" section of Certum's [Downloads page](https://support.certum.eu/en/cert-offer-software-and-libraries/). As explained in the software description:
   - "The SimplySign Desktop application emulates the connection of a physical crypto card and a card reader on your computer. This solution allows you to use SimplySign in applications that [normally] require the use of a physical card." In other words: it is analogous to a VPN, making your certificate available to `signtool.exe` only while you are connected.
   - The SimplySign Desktop application is also available for Linux and macOS. But since you will be using it in conjunction with `signtool.exe`, it's simplest to stick with Windows here.

4. Install the [SimplySign Mobile application](https://support.certum.eu/en/installation-of-the-simplysign-application/) onto an Android phone [from the Google Play Store](https://play.google.com/store/apps/details?id=com.assecods.certum.simplysign&hl=en_US), or onto an iPhone or iPad [from the Mac App Store](https://apps.apple.com/us/app/certum-simplysign/id1244415465).
   - In order to activate SimplySign on your mobile device, you must first activate your account by receiving two separate emails: one with a six-character activation code, and another with a long unique URL that you must visit and enter the code.
   - You will then receive a QR code in the browser, which you must scan with the mobile device.
   - Once you do that, you can start using the SimplySign mobile app to generate six-digit 2FA tokens for use logging into the SimplySign Desktop app.

5. Connect to SimplySign using SimplySign Desktop on your Windows machine:
   - When first launched, SimplySign Desktop takes the form of a system tray icon, which is initially hidden. To make it visible: right-click taskbar, "Taskbar settings" gear, "System tray icons" section, "Other system tray icons" dropdown, toggle the "SimplySign Desktop" icon from "Off" to "On".
   - Right-click the now-visible SimplySign icon, "Connect to SimplySign" from context menu.
   - In the SimplySign Mobile app on your mobile device, generate a token, then use it from the desktop app to log in.
   - Once logged in, mousing over the SimplySign system tray icon should display a tooltip with "Status: connected" message.

6. Obtain the certificate thumbprint:
   - Right-click the SimplySign system tray icon, Manage certificates, Certificate list.
   - Right-click on certificate info, Show certificate.
   - Details tab.
   - Scroll to the bottom of table.
   - Click the last row, the "Thumbprint" field.
   - Copy the 40-digit hex value shown in the lower text area.

7.  Install `signtool.exe` if not already installed:
    - It comes as part of the [Windows SDK](https://developer.microsoft.com/en-us/windows/downloads/windows-sdk/); download the installer&mdash;version 10.0.26100.1742 is known to work.
    - When run, the installer will prompt you to choose which of 14 different features to install. The only feature needed is "Windows SDK Signing Tools for Desktop Apps".
    - You might also consider installing "MSI Tools" if you want to make a .msi installer for your application, although doing so is outside the scope of this guide.
    - Selecting only these two features should reduce the installation footprint from 3.5 GB to 24.3 MB.

8. Run Jaunch's code-signing script:
   ```shell
   export THUMBPRINT="effaced0abcdef12345678900987654321fedcba"
   ~/jaunch-2.1.0/bin/sign.sh /path/to/myapp/*.exe /path/to/myapp/jaunch/*.exe
   ```
   Where your actual `THUMBPRINT` value is the one from step 6, and the
   `/path/to/...` expressions reference the EXE files constructed in step 1.

   If all goes well, the script will code-sign your EXEs.

9. (OPTIONAL) Submit the signed EXEs to Microsoft's online anti-malware scanner:
   - Open the [Microsoft Windows Defender Security Intelligence portal](https://www.microsoft.com/en-us/wdsi/filesubmission?persona=SoftwareDeveloper) in your web browser.
   - Fill in the web form. Here are some suggested values:
     * **Select the Microsoft security product used to scan the file**: Microsoft Defender Smartscreen
     * **What do you believe this file is?**: Incorrectly detected as malware/malicious
     * **Select the file**: A zip file of all your EXEs to be submitted. Encrypting it is not required.
     * **Detection name**: SmartScreen warning
     * **Additional information**: Signed binaries for MyApp: https://mycompany.com/myapp
     Replace `MyApp` with your app's title, and `https://mycompany.com/myapp` with your official project URL.

P.S. Certum's documentation about the code signing process is [here](https://support.certum.eu/en/signing-the-code-using-tools-like-signtool-and-jarsigner-instruction/). The relevant document is the "Instruction – Certum Code Signing in the Cloud – Using Singtool and Jarsigner" PDF.
