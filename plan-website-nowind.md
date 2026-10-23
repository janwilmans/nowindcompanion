# Plan: add the "Nowind" project to nullptr.nl

This plan is for **Claude in the browser side panel**. It contains everything you need, because you can't
open the source repositories this was written from. Follow the steps in order. Where a step says *ask the
user*, stop and ask before you continue.

---

## 0. Your task in one paragraph

nullptr.nl is a WordPress site. Add a project called **Nowind**, with the tagline **"No wind, no waves"**.
The project has **three pages**:

1. **Main page:** what the Nowind cartridge for MSX computers is and what you can do with it.
2. **Subpage 1, "The cartridge":** technical details, how to use it, how to update the firmware, and
   troubleshooting.
3. **Subpage 2, "Nowind Companion":** the new Android app that hosts disks for the cartridge from a phone.

The full text for all three pages is in section 4. Use it as written. You may shorten sentences or change
the layout to fit the site's style, but don't invent facts. Facts that need checking are marked
**[verify]**: leave them out, or ask the user.

---

## 1. Ground rules

- **Work in WordPress admin** (`https://nullptr.nl/wp-admin/`). If you are not logged in, ask the user to
  log in. Never type a password yourself.
- **Save everything as a Draft first.** Only publish after the user has seen the drafts and said yes.
- **Don't change** the theme, plugins, other pages or posts, or site settings. Menus and project listings
  change only in step 5, and only after asking.
- **Don't delete anything.**
- **Never link to `nowind.nl`.** That domain no longer belongs to the project and now hosts unrelated spam.
  For the old site, link to the Internet Archive copy (see section 6).
- **Don't mention any specific download site for disk images.** The app ships with no disk image source.
  Users add their own sources.
- **Language:** English. Match the tone of the existing project pages on nullptr.nl.

---

## 2. Find out how projects work on nullptr.nl

Do this before creating anything, and report what you find to the user.

1. Open `https://nullptr.nl/` and find where projects are listed (menu item, "Projects" page, a portfolio
   section, a category, ...).
2. Open one or two existing projects. Note:
   - Are they **Pages** with a parent ("Projects"), **Posts** in a category, or a **custom post type**
     (for example "Projects" or "Portfolio" in the admin sidebar)?
   - Where does a project's **tagline** appear (subtitle field, excerpt, first line of the page, a theme
     option, a custom field)?
   - Does a project have a **featured image** shown in the listing?
   - Which editor is used: **block editor (Gutenberg)**, the **Classic editor** or a **page builder**
     (Elementor, Divi, ...)?
   - What do the URLs look like, for example `/projects/<name>/`?
3. **Ask the user** to confirm the plan, adapted to what you found. The default if projects are pages:
   - Main page: `Nowind`, slug `nowind`, under the same parent as other projects.
   - Subpage 1: `The cartridge`, slug `cartridge`, with parent `Nowind`.
   - Subpage 2: `Nowind Companion`, slug `companion`, with parent `Nowind`.

   If projects are a custom post type that doesn't support child items, make the main page a project
   entry, make the two subpages normal WordPress pages, and link them from the main page.

---

## 3. Images

You can't upload files from the user's computer. **Ask the user** to upload these files to the Media
Library (Media → Add New), then use them from there.

| File (on the user's PC) | What it shows | Alt text | Use on |
|---|---|---|---|
| `C:\project\nowind-wifi\nowind.jpg` | Nowind Interface cartridge with its USB cable, on a white background (3/4 view) | Nowind Interface cartridge for MSX with USB cable | Main page header and featured image |
| `C:\project\nowind-wifi\nowindv1.jpg` | Top view of the cartridge with the label: a USB plug with a light trail, "Nowind INTERFACE", MSX, MSX2 and turbo R logos, "No wind, no waves" | Label of the Nowind Interface: "No wind, no waves" | The cartridge page |
| `C:\project\nowind-wifi\nowindv2.jpg` | Close-up of the label of a later cartridge with two USB plugs, MSX logos, the Sunrise logo and "No wind, no waves" | Nowind Interface label with the Sunrise for MSX logo | The cartridge page, "Versions" section |
| `C:\project\nowind-wifi\nowindcompanion\apps\Nowindcompanion\store\play-icon-512.png` | App icon: a pixel-art Nowind cartridge with a rainbow USB plug on a blue background | Nowind Companion app icon | Companion page header, and featured image of the Companion page |
| App screenshots (optional): menu, Browse, Nowind status and Settings screens | | Nowind Companion: <screen name> screen | Companion page |

Old photos from the original site, as Internet Archive copies. Use them only if the user wants them; they
can be inserted with the image block's "Insert from URL":
- Cartridge photo: `https://web.archive.org/web/2014/http://www.myquest.nl/nowindusb/files/DSC04065-02.JPG`
- Cartridge photo (thumbnail): `https://web.archive.org/web/2014/http://www.myquest.nl/nowindusb/files/tn_IMG_0094.JPG`
- Prototype board: `https://web.archive.org/web/2017/http://www.myquest.nl/nowindusb/files/proto3.jpg`

If an image isn't available, leave a clearly marked placeholder, like `[IMAGE: nowind.jpg]`, and tell the
user.

---

## 4. Page content

Below is the content per page. `##` and `###` are headings. Bulleted lists become list blocks, tables
become table blocks, and code goes in code blocks (preformatted, monospace).

### 4.1 Main page: "Nowind"

- **Title:** Nowind
- **Tagline / subtitle:** No wind, no waves
- **Excerpt / listing summary:** A USB cartridge that lets a real MSX computer use disk images from a PC or
  an Android phone.
- **Featured image:** `nowind.jpg`

---

*(image: nowind.jpg)*

**Nowind** is an MSX cartridge that connects your MSX computer to a modern computer over USB. The MSX boots
and loads software from disk images stored on a PC, Mac, Linux machine or, with the new Nowind Companion app,
an Android phone. No floppy disks, no worn-out disk drives, and no waiting for a disk to turn: "No wind, no
waves".

#### What can you do with it?

- **Run disk images on a real MSX.** Disk images (`.dsk`) on the host behave like real floppy disks on the
  MSX. Normal floppy images (360 kB, 720 kB and other formats) and large hard disk images work.
- **Use MSX-DOS 1 and MSX-DOS 2.** Both are built into the cartridge, so you don't need a separate DOS2
  cartridge. FAT16 hard disk images with partitions are supported under DOS2.
- **Load quickly.** Transfer speed is about the same as an MSX hard disk interface, about 320 kB/s on a
  7 MHz MSX.
- **Develop software.** Programs built on a PC can be loaded into a real MSX right away. You can share disk
  images between an emulator and a real MSX.
- **Use the ROM drive.** A small disk image stored in the cartridge's own flash memory, which works even
  without a host.
- **Use files from BASIC.** The BASIC device `NOW:` gives MSX BASIC access to files on the host.
- **Use it on every MSX generation:** MSX, MSX2, MSX2+ and MSX turbo R.

#### What it is not

People often ask whether they can plug a USB keyboard or a USB memory stick into the MSX through Nowind.
**That isn't possible.** The cartridge is a USB *device*, not a USB *host*: it turns your MSX into something
a PC or phone can talk to. It always needs a host computer or phone running the Nowind host software. Any
storage the host can read (hard disk, USB stick, network share, or the phone's own storage) can hold your
disk images.

#### Two ways to host your disks

| Host | Software | Best for |
|---|---|---|
| Windows, Linux or macOS computer | `nwhostapp`, a command line program | Developers, hard disk images, firmware updates |
| Android phone | **Nowind Companion** app | A quick setup next to your MSX: browse, download and host disk images from the phone |

→ Read more: **[The cartridge: technical details, usage, firmware and troubleshooting](#)** · **[Nowind Companion for Android](#)**
*(link these to the two subpages)*

#### A little history

Nowind was developed by **Jan Wilmans** and **Aaldert Dekker**. The first prototypes date from around 2006.
The cartridges were first built and sold by the developers themselves. From December 2010 they were produced
and sold by **Sunrise** (Sunrise for MSX), including a new batch in 2013 with a smaller board and more memory.
Sunrise later ended its activities, so new cartridges are no longer made. Owners can still use theirs, and
that is what the new Android app is for.

The original project site can still be read in the Internet Archive:
[nowind.nl as it was in 2014](https://web.archive.org/web/20140517175354/http://nowind.nl/).

#### Open source

All Nowind software is open source under the MIT license: the MSX-side firmware, the host libraries,
`nwhostapp` and an MSX emulator with Nowind support.
- Host software and firmware: <https://github.com/janwilmans/nowindlibraries>
- Android app: <https://github.com/janwilmans/nowindcompanion>



---

### 4.2 Subpage 1: "The cartridge"

- **Title:** The cartridge
- **Parent:** Nowind
- **Excerpt:** Technical details of the Nowind Interface, how to use it with nwhostapp, firmware updates and
  troubleshooting.

---

*(image: nowindv1.jpg)*

## Hardware

| | |
|---|---|
| Type | MSX cartridge with a USB connection |
| USB chip | FTDI FT245 USB FIFO (a USB 1.1 full-speed device) |
| Flash memory | 512 kB in version 1, 4 MB in version 2. The firmware lives in flash, so it can be updated over USB |
| Built-in software | Nowind disk ROM, MSX-DOS 1, MSX-DOS 2, ROM drive, BASIC `NOW:` device |
| Compatible with | MSX, MSX2, MSX2+, MSX turbo R |
| Speed | About 320 kB/s reading on a 7 MHz MSX, similar to an MSX hard disk interface |
| Host | PC (Windows, Linux, macOS) with `nwhostapp`, or an Android phone with Nowind Companion. iPhone is not supported |

### Versions

*(image: nowindv2.jpg)*

- **Version 1:** built by the developers, later produced by Sunrise. 512 kB flash.
- **Version 2** (also the 2013 batch, by Sunrise): smaller board, 4 MB flash and more internal SRAM.

Both versions use **the same USB protocol**, so all host software works with both.

### How it works

The host software waits for commands from the MSX. When the MSX reads from a disk, the Nowind disk ROM
sends the request over USB, and the host reads the sectors from an image file instead of a physical disk.
The protocol checks every block and resends it when needed. That matters, because the MSX reads the USB
buffer directly and can't wait for slow data.

## Supported disk images

- Floppy images: 360 kB and 720 kB (the common ones), plus 160, 180, 320 and 640 kB formats.
- Hard disk images with partitions (FAT16) under MSX-DOS 2.
- Write protection: if an image file is read-only on the host, the MSX sees a write-protected disk.

## Using the cartridge with a PC: nwhostapp

`nwhostapp` is the command line host program for Windows, Linux and macOS.

1. Insert the Nowind cartridge into the MSX **while the MSX is switched off**.
2. Connect the cartridge to the PC with a USB cable.
3. On Windows, install the FTDI D2XX driver if Windows doesn't find one. On Linux, `nwhostapp` may need to
   run as root, or with a udev rule that gives your user access to the FTDI device.
4. Start the host with a disk image:
   ```
   nwhostapp -i game.dsk
   ```
5. Switch on the MSX. It boots from the disk image.

### nwhostapp options

```
Usage: nwhostapp [-2afgijmpz] [-i image.dsk] [-f firmware.rom]
  --image,    -i   disk image or partition image
  --hdimage,  -m   hard disk image
  --romdisk,  -j   enable the ROM drive
  --flash,    -f   update the firmware (works on all versions, verifies)
  --glash,    -g   update the firmware, faster (does not verify, does not update rom headers)
  --dos2,     -2   prefer MSX-DOS 2 (MSX2 only; not needed on a turbo R)
  --phantom,  -p   create phantom drives (like NOT holding CTRL at boot)
  --allow,    -a   allow other disk ROMs to initialize as well
  --dsk2rom,  -z   convert a 360 kB image to a ROM drive image (romdisk.bin)
  --debug,    -d   show debug information
```

Examples:
```
nwhostapp -i image.dsk                 one floppy image
nwhostapp -i disk1.dsk -i disk2.dsk    two drives (A: and B:)
nwhostapp -2 -i image.dsk              boot MSX-DOS 2 on an MSX2
nwhostapp -m hdimage.dsk               all partitions of a hard disk image
nwhostapp -m hdimage.dsk:0             only the first partition
nwhostapp -m hdimage.dsk:1-3           partitions 2 to 4
nwhostapp -j                           add the ROM drive
```

### On the MSX

- **Change disks from BASIC:** `CALL IMAGE "GAME2.DSK"` (or `_IMAGE "GAME2.DSK"`) asks the host to put
  another image file in the **first Nowind drive** (normally A:). The name must be a quoted text, not a
  variable, of at most about 38 characters. It is a path on the PC, relative to the folder where `nwhostapp`
  was started, or a full path. There is no error message when the file doesn't exist. This works with
  `nwhostapp` only; Nowind Companion doesn't support it yet (change disks in the app instead).
- **Change disks from MSX-DOS:** `NIC.COM`, the Nowind Image Changer: `NIC path\file.dsk`. Use `/H` for a
  hard disk image and `/L` to list images.
- **Files on the host from BASIC:** the `NOW:` device reads and writes files in the folder where `nwhostapp`
  was started. Names are MSX style (8.3). Use `FOR INPUT`, `FOR OUTPUT` or `FOR APPEND`; random access isn't
  supported. `nwhostapp` only.
  ```
  10 OPEN "NOW:README.TXT" FOR INPUT AS #1
  20 IF EOF(1) THEN 60
  30 LINE INPUT #1,A$
  40 PRINT A$
  50 GOTO 20
  60 CLOSE #1
  ```
  ```
  10 OPEN "NOW:HELLO.TXT" FOR OUTPUT AS #1
  20 PRINT #1,"Hello from MSX"
  30 CLOSE #1
  ```
- **Test the connection:** `NTA.COM /R` (read test) and `NTA.COM /W` (write test).
- **Other disk drives:** if the MSX has its own disk drive, Nowind starts first and the internal drive is not
  initialized. Start `nwhostapp` with `-a` to allow other disk ROMs to start as well.

## Updating the firmware

The firmware is stored in the cartridge's flash memory and can be updated over USB from a PC.

1. Download `nwhostapp` and the latest firmware `nowind.rom` (see "Downloads" below).
2. Connect the cartridge to the PC. The MSX is still off.
3. **Hold the `F` key** and switch on the MSX. Keep holding it until the screen turns **red** and shows
   *"Nowind Flash Writer Mode"*.
4. On the PC, run:
   ```
   nwhostapp --flash nowind.rom
   ```
   This is the safe method: it verifies the result and updates the rom headers. `-g` is faster, but doesn't
   verify.
5. Wait until `nwhostapp` reports that it's finished, then switch the MSX off and on again.

Tips:
- On an MSX turbo R you don't need to do anything special: the flash writer switches to Z80 mode by itself.
- If flashing with `-g` fails or is irregular (seen on 64-bit Windows 7), use `-f`.
- If flashing fails, don't panic. The flash writer mode is started from the cartridge itself, so you can
  hold `F` at boot and try again.

## Downloads

There are no official releases; these are the most recent builds.

| What | Link | Notes |
|---|---|---|
| Firmware `nowind.rom` (latest, February 2013) | <https://github.com/janwilmans/nowindlibraries/raw/main/msxsrc/nowind.rom> | For version 1 and 2 |
| nwhostapp for Windows, v4.2 (August 2010) | <https://web.archive.org/web/20230226070152/http://www.myquest.nl/nowindusb/files/nwhostapp_v4.2.zip> | Also contains a simple Windows GUI (`NowindInterfaceHostGUI.exe`), `NIC.COM` and an older `nowind.rom`. Needs the FTDI D2XX driver: <https://ftdichip.com/drivers/d2xx-drivers/> |
| nwhostapp for Linux, 32-bit x86 (2013) | <https://github.com/janwilmans/nowindlibraries/raw/main/nwhostapp_linux_x86.zip> | |
| nwhostapp for macOS (2013) | <https://github.com/janwilmans/nowindlibraries/raw/main/nwhostapp.app_mac_portable.zip> | Built in 2013; may not run on recent macOS |
| Source code | <https://github.com/janwilmans/nowindlibraries> | Build it yourself for other systems |

## Troubleshooting

| Problem | What to try |
|---|---|
| The MSX boots without Nowind, or hangs at boot | Is the cartridge inserted firmly, with the MSX off while inserting? Try another cartridge slot. Is the host program running *before* the MSX starts? |
| The host doesn't find the interface | Try another USB cable or port. On Windows, check that the FTDI driver is installed (Device Manager). On Linux, run as root or add a udev rule for the FTDI device. |
| Many read errors or hangs, mostly on Windows | Connect the cartridge to a **USB 2.0 port** or through a USB 2.0 hub, not directly to a USB 3 port. The MSX empties the cartridge's USB buffer faster than some USB 3 ports refill it. |
| A game doesn't start with MSX-DOS 2 | Some games (for example Puyo Puyo) don't like DOS2. Start without `-2`, or switch DOS2 off in the Companion app. |
| The internal disk drive is gone | Expected: Nowind takes over. Use `-a` to let other disk ROMs start too. |
| Fewer or more drives than expected | Holding CTRL at boot gives one drive per disk ROM. `-p` creates phantom drives. |
| Firmware update fails | Use `-f` instead of `-g`. Restart flash writer mode with `F` at boot and try again. |
| You need more information | Run `nwhostapp -d` for debug output and report the problem on GitHub. |

---

### 4.3 Subpage 2: "Nowind Companion"

- **Title:** Nowind Companion
- **Parent:** Nowind
- **Excerpt:** An Android app that turns your phone into the host for the Nowind cartridge: browse, download
  and host MSX disk images from your phone.
- **Featured image:** `play-icon-512.png`

---

*(image: play-icon-512.png, small, next to the introduction)*

**Nowind Companion** is a new Android app that takes the place of the PC. Connect your phone to the Nowind
cartridge with a USB OTG cable, and the phone hosts disk images for your MSX. You can find, download and
switch disk images on the phone itself, without a PC.

> **You need real hardware.** The app does nothing on its own. It needs a **real MSX computer** and a
> **Nowind Interface cartridge** (version 1 or 2). The app doesn't emulate an MSX.

## What you need

- An **MSX computer**: MSX, MSX2, MSX2+ or MSX turbo R.
- A **Nowind Interface** cartridge, version 1 or 2.
- An **Android phone with Android 5.0 or newer** and **USB host (OTG) support**. Most phones have it.
- A **USB OTG adapter or cable** for your phone (USB-C or micro-USB to a full-size USB-A socket), plus the
  USB cable for the Nowind cartridge.

## Getting started

1. **Install** Nowind Companion from Google Play (**coming soon to Google Play**; no link yet).
2. **Insert the Nowind cartridge** into the MSX while the MSX is **switched off**.
3. **Connect the phone** to the cartridge with the OTG adapter and the USB cable.
4. Android asks whether **Nowind Companion** may open for this USB device. Tick **"Always open"** (or
   "Use by default for this USB device") and tap **OK**. From then on the app starts by itself when you plug
   in the cartridge.
5. Open **Settings → Disk image sources** and add where the app should look for disk images (see "Disk image
   sources" below). The app ships with no source.
6. Open **Browse**, find a disk image and tap **Download and host** (▶). The app downloads it, puts it in the
   drive, and the Nowind status shows the disk as hosted.
7. **Switch on the MSX.** It boots from the hosted disk.

## The screens

### Menu
The start screen shows the connection status and the currently hosted disk. It has three cards: **Browse**,
**Settings** and **Nowind**.

### Browse
- Choose a **source** at the top.
- For an archive that supports it, use **Filter** to show only MSX1, MSX2, MSX2+ or turbo R disks.
- For each disk image:
  - **Queue for download** (list icon): download it in the background.
  - **Download and host** (▶): download it and put it in the drive right away.
- The **Library** tab lists the disk images on your phone. Tap **Host** to put one in the drive, or **Delete**
  to remove it.
- Disk images can be `.dsk` files or `.zip` files containing a `.dsk`.

### Nowind
The status of the interface and the phone: connection state, the interface's serial number and description,
the hosted disk, transfer speeds (read and write, average and maximum), and the Android and app versions.
The copy button puts all of this on the clipboard, which is handy for bug reports. There is also a debug log.

### Settings
- **Enable DOS2:** boot MSX-DOS 2 on an MSX2 or newer. Off by default, because some games don't work with
  DOS2.
- **Keep hosting in the background:** while the interface is connected, the app keeps running with a
  notification. The MSX can then keep using its disk while the phone's screen is off or you use another
  app. This uses more battery.
- **Disk image sources:** the places the app looks for disk images. A source can be:
  - a **Nowind API** server,
  - a **web server directory listing**,
  - a **web page that links to `.zip` or `.dsk` files**.

  Tap **Add source** and enter the address. The app checks the address and detects the type itself. You can
  switch sources on and off, and edit the default source with the pencil button.
- **Enable debug information:** log the communication with the MSX, for troubleshooting.

## Good to know

- **The MSX can't write to disk yet.** The app only supports reading for now, so saving games or files on the
  MSX doesn't work yet.
- **Switching disks:** host another disk image in Browse or the Library. The MSX sees the new disk at its
  next disk access.
- **Disk images and copyright:** the app comes without disk images and without a built-in source. Only use
  disk images you are allowed to use: your own disks, freeware or homebrew.
- **Privacy:** see the Privacy section below.

## Troubleshooting

| Problem | What to try |
|---|---|
| The app says "Searching for interface" | Check the OTG adapter and the cable. Not every phone or adapter supports USB host mode, so try another adapter. Unplug and replug, and allow access when Android asks. |
| "USB host not supported" | This phone has no USB host (OTG) support. Use another phone or a PC with `nwhostapp`. |
| The MSX doesn't boot from the disk | Is a disk hosted (Nowind screen → Hosted disk)? Was the phone connected and the app running before you switched on the MSX? |
| A game doesn't work | Switch **Enable DOS2** off and restart the MSX. |
| Hosting stops when the screen turns off | Switch on **Keep hosting in the background**. On some phones (Samsung, Xiaomi and others) also disable battery optimization for Nowind Companion in Android's settings. |
| A source can't be added | Check the address in a browser. Some sites block automated requests now and then; use **Add anyway**. |

## Privacy

*(Give this heading the HTML anchor `privacy`, so `…/companion/#privacy` can be used as the app's privacy
policy URL on Google Play.)*

Nowind Companion doesn't collect, store or share any personal data. It has no accounts, no ads and no
analytics.
- **Network:** the app only connects to the disk image sources you add yourself, to list and download disk
  images.
- **On your phone:** settings and downloaded disk images stay on your phone. Android's own backup, if you have
  it switched on, may include the app's settings.
- **USB:** the app only talks to the Nowind Interface, to host disks for your MSX.
- **Questions:** open an issue at <https://github.com/janwilmans/nowindcompanion/issues>.

## Open source

Nowind Companion is open source (MIT license): <https://github.com/janwilmans/nowindcompanion>

---

## 5. Linking it into the site

After the three drafts are ready:

1. **Ask the user** to review the drafts (send them the preview links).
2. After approval, publish the three pages.
3. **Ask the user** whether to add Nowind to the projects overview or the menu, and do it the same way as the
   existing projects (same menu, same order style, same featured image size).
4. Check that the links between the pages work: the main page links to both subpages, and both subpages link
   back to the main page. Add a "← Back to Nowind" link at the top or bottom of each subpage if the theme
   doesn't show breadcrumbs.

## 6. Links you may use

- Old project site (archived): <https://web.archive.org/web/20140517175354/http://nowind.nl/>
- Host software, firmware, emulator: <https://github.com/janwilmans/nowindlibraries>
- Android app: <https://github.com/janwilmans/nowindcompanion>
- Google Play: not yet, write "coming soon to Google Play"

**Don't use:** `nowind.nl` (now spam), `myquest.nl` (old, not maintained), or any download site for MSX disk
images.

## 7. Done when

- [ ] Three pages exist (Nowind, The cartridge, Nowind Companion), with the right parent and slugs.
- [ ] The tagline "No wind, no waves" shows where the site shows project taglines.
- [ ] Images are in place with alt texts. Missing images are reported to the user.
- [ ] All **[verify]** items are resolved with the user or removed from the text.
- [ ] The pages look right on desktop and phone width (check the preview at both sizes).
- [ ] The user approved and the pages are published, and Nowind appears in the projects overview.
