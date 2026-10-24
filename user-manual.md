# Nowind Companion user manual

Nowind Companion turns an Android phone into the host for the **Nowind Interface**. The Nowind Interface is an MSX cartridge with a USB connection. The MSX reads its disks from the phone as if they were in a disk drive. The app finds and downloads disk images and ROMs, starts them on the MSX, and updates the firmware of the cartridge.

## Contents

1. [What you need](#what-you-need)
2. [Getting started](#getting-started)
3. [Browse: finding disks and ROMs](#browse-finding-disks-and-roms)
4. [Running ROMs](#running-roms)
5. [Booting without disk](#booting-without-disk)
6. [Commands on the MSX](#commands-on-the-msx)
7. [Keys at startup](#keys-at-startup)
8. [Settings](#settings)
9. [Firmware update](#firmware-update)
10. [The Nowind screen](#the-nowind-screen)
11. [Files on the phone](#files-on-the-phone)
12. [Troubleshooting](#troubleshooting)
13. [About the Nowind Interface](#about-the-nowind-interface)

## What you need

- A **Nowind Interface**, version 1 or version 2 (the small form factor made by Sunrise in 2013). Both work the same way.
- An **MSX**, MSX2, MSX2+ or MSX turbo R.
- An **Android phone with USB host (OTG) support**, and a USB OTG cable or adapter for the cartridge's USB cable.

## Getting started

1. Insert the Nowind Interface into the MSX while the MSX is **switched off**.
2. Connect the cartridge to the phone with the USB OTG cable.
3. Open Nowind Companion. Android asks whether the app may use the USB device. Tick **use by default for this USB device**, and Android won't ask again on every connection.
4. On the first start the app asks for its **storage folder**:
   1. Tap **Choose folder**. The system folder picker opens in `Download`.
   2. Open or create the folder `nowindcompanion`.
   3. Tap **Use this folder**.

   The app keeps downloaded disks, ROMs, firmware backups and its settings in this folder. You can reach the folder over USB file transfer, and it stays when the app is removed. After you reinstall the app, choose the same folder again: your library and settings come back.
5. Pick a disk in **Browse** and switch on the MSX. It boots from the disk on the phone.

The main menu shows the connection state, the disk being hosted and an activity light that blinks when the MSX talks to the phone. It has three entries:

- **Browse** finds, downloads and hosts disk images and ROMs.
- **Settings** has the options of the app and the firmware update.
- **Nowind** shows the interface status, transfer speed and debug log.

## Browse: finding disks and ROMs

Browse has two tabs, **Archive** and **Library**.

### Archive

The row of chips at the top selects the **source**. You add and enable sources in **Settings → Disk image sources**. A source can be:

- **A Nowind API.** Under the source chips you choose **Disks** or **ROMs**, and an MSX generation (**All**, **MSX1**, **MSX2**, **MSX2+**, **Turbo R**).
- **A web server directory listing or a web page** that links to `.dsk`, `.zip` or `.rom` files. You open folders by tapping them, the arrow goes up a level, and the back button walks back through the folders you opened.

Each file has two buttons:

- **Queue** downloads the file in the background. You can queue several files; a bar at the bottom shows the progress and the number of files waiting.
- **Download and host** jumps the queue, downloads the file and inserts it in the Nowind drive straight away.

A check mark shows that a file is already in your library.

**Search.** Type in the search field above the list. Every word is a keyword, and case doesn't matter. The results are ordered like this:

1. names with all keywords, alphabetically
2. then names with only the first keyword
3. then names with the second keyword (but not the first), and so on

For example, `nemesis 1986` lists *Nemesis (1986)* first, then the other *Nemesis* titles, then the other games from 1986.

### Library

The Library tab lists everything you have downloaded, disks and ROMs together. ROMs have a chip icon. The disk being hosted is marked **Hosted**.

- The **play** button hosts a file.
- The **bin** button deletes it (not while it is hosted).

The last hosted disk is inserted again when the app starts.

**Disk writes.** When the MSX writes to a disk, the change is saved back to the file in your library. Disks inside a `.zip` file can't be saved back, so the MSX sees them as write protected.

**Multi-disk games** from a Nowind API are downloaded as a set and saved as separate disks, for example `Game (Disk 1 of 2).dsk` and `Game (Disk 2 of 2).dsk`.

## Running ROMs

The Nowind Interface emulates a disk drive, not a cartridge slot, so the app starts a ROM from a disk.

1. Host the ROM from Browse or the Library. The app shows "reset the MSX to start it".
2. Reset the MSX. It boots into Disk BASIC from a disk that the app makes in memory, and a small loader copies the ROM into RAM and starts it like a cartridge.

**Supported ROMs:**

- Plain ROMs of **8, 16 or 32 KB** that start in `$4000-$BFFF`, which is most early games, for example *Knightmare* and *Sweet Acorn*.
- An MSX with **64 KB RAM**. On a smaller MSX the loader prints "needs 64 KB RAM".

**Not supported yet** (the app shows a message instead of starting them):

- MegaROMs and other ROMs over 32 KB
- mirrored ROMs that start in page 0, such as *Beamrider*, which starts at `$0004`
- BASIC programs in ROM

**Good to know:**

- After a reset the MSX starts the ROM again, the same as a cartridge.
- The ROM disk is write protected.
- If a ROM doesn't start, the loader shows a message on the MSX screen, for example "ROM loader: no ROM header".

## Booting without disk

Sometimes you want the MSX to start without the disk, for example to get to BASIC instead of starting a game.

1. Tap the **eject** button at the top of Browse. A line under the tabs says *The next boot starts without disk*.
2. Reset the MSX. It starts without disk. The line now says *Booted without disk, the next boot finds the disk again*.
3. The next reset finds the disk again by itself.

Tap the eject button again to cancel before the boot, or to bring the disk back straight away.

**Good to know:**

- The boot without disk can take a little longer, because the MSX waits for an answer from the drive before it gives up.
- During that session, disk commands in BASIC give *Disk offline* until you reset or bring the disk back.

## Commands on the MSX

### CALL IMAGE / _IMAGE: changing the disk from the MSX

`CALL IMAGE` (short: `_IMAGE`) asks the phone for another disk or ROM. Put the name in quotes:

```basic
_IMAGE("KNIGHTMARE")
CALL IMAGE("ALESTE 2,EN")
```

The app looks for the name in this order:

1. **A library file with exactly this name**, with or without extension: `_IMAGE("GAME.DSK")` or `_IMAGE("GAME")`.
2. **The best match in your library** for the keywords.
3. **The best match in the archive** of your enabled sources. The app downloads it and then hosts it. It searches both disks and ROMs of a Nowind API, and the files on the first page of a web source.

The app shows a message with what it found, or that nothing matched.

**How the name is matched:**

- **Words in any order.** `_IMAGE("METAL GEAR")` finds a name with *Metal* and *Gear*, in any order, anywhere in the name.
- **Keys in order, separated by commas.** `_IMAGE("KNIGHTMARE,KONAMI")` finds a name with *Knightmare* and, somewhere after it, *Konami*. A key may contain spaces: `ALESTE 2,EN` means *Aleste 2* followed by *en*.
- **Every keyword must be in the name.** When several names match, the **shortest name** wins, then alphabetical order. Hacks, translations and trainers usually add to the name of the original, so the shortest match is usually the original release.
- **ROMs over 32 KB are skipped**, because they can't be started yet (see [Running ROMs](#running-roms)).
- Upper or lower case doesn't matter, and an extension like `.DSK` or `.ROM` is ignored.

**DOS2 on or off.** End the name with `+2` to turn DOS2 on, or with `+1` to turn it off:

```basic
_IMAGE("PUYO PUYO+1")
_IMAGE("+2")
```

`_IMAGE("PUYO PUYO+1")` hosts Puyo Puyo and boots DOS1 from the next boot. `_IMAGE("+2")` only turns DOS2 on. This is the same setting as **Enable DOS2** in Settings. It stays as you set it, and it takes effect when you reset the MSX.

**Examples:**

| You type | The phone hosts |
|---|---|
| `_IMAGE("ROAD FIGHTER")` | *Road Fighter (1985)(Konami)* |
| `_IMAGE("METAL GEAR")` | *Metal Gear (1987)(Konami)* |
| `_IMAGE("KNIGHTMARE,KONAMI")` | *Knightmare. Majyo Densetsu (1986)(Konami)* |
| `_IMAGE("NEMESIS,KONAMI")` | *Gradius. Nemesis (1986)(Konami)* |
| `_IMAGE("ALESTE 2,EN+2")` | *Aleste 2 (1988)(Compile)(en)(Disk1of3)*, with DOS2 from the next boot |

(The results depend on the sources you have enabled.)

**Tips:**

- `_IMAGE` returns straight away. A match from the archive is inserted when its download finishes, usually after a few seconds, so wait a moment before `FILES`.
- If the match is a ROM, reset the MSX to start it.
- If a short keyword finds the wrong title, add a second key with a comma, for example the publisher or the year.
- The name can be up to about 38 characters.

### The NOW: device: files on the phone from BASIC

The `NOW:` device reads and writes files in the `now` folder of the storage folder (`Download/nowindcompanion/now`):

```basic
OPEN "NOW:SCORES.TXT" FOR OUTPUT AS #1
PRINT #1, "HELLO"
CLOSE #1
SAVE "NOW:PROGRAM.BAS",A
LOAD "NOW:PROGRAM.BAS"
```

- Names are MSX file names (8.3).
- Input, output and append work. Random access doesn't.

### MSX-DOS tools

The firmware comes with small MSX-DOS tools:

- `NIC.COM` changes the disk image from MSX-DOS.
- `NOWMAP.COM` lists the drives, or selects a partition of a hard disk image.
- `NTA.COM` is a test application; `/R` and `/W` test reading and writing.

## Keys at startup

Hold a key directly after switching on or resetting the MSX:

| Key | Effect |
|---|---|
| **1** | boot MSX-DOS 1 instead of MSX-DOS 2 |
| **6** | switch between 50 Hz and 60 Hz (MSX2 and newer) |
| **CTRL** | one drive per disk ROM, no phantom drive B: |
| **F** | Flash Writer Mode, for a [firmware update](#firmware-update) |

## Settings

- **Keep hosting in the background.** While the interface is connected, the app stays active with a notification, so the MSX can use its disk while the screen is off or another app is open. This uses more battery. On Android 13 and newer, the app asks for permission to show the notification.
- **Disk image sources.** The sources Browse searches. Switch them on and off, delete them, or add one with **Add source**.
  - When you add a source, the app checks it and detects what kind of source it is: a Nowind API, a directory listing or a web page with links.
  - The **default source** is empty until you enter a name for it. A name without dots stands for a Nowind API: `example` means `https://api.example.com/nowind.php`. A name with dots is used as a web address.
- **Enable DOS2.** Boots MSX-DOS 2 on MSX2 and newer. It's off by default, because some games (*Puyo Puyo*) don't work with DOS2. It takes effect at the next reset. `_IMAGE("...+2")` and `_IMAGE("...+1")` change this setting too.
- **Firmware update.** See [Firmware update](#firmware-update).
- **Enable debug information.** Logs the communication with the MSX in the debug log (see [The Nowind screen](#the-nowind-screen)). With this off, all debug output is off.

## Firmware update

**Settings → Interface → Firmware update** checks and updates the firmware in the flash memory of the cartridge. The phone does the same job as `nwhostapp --flash` on a PC.

### Put the MSX in Flash Writer Mode

1. Turn on the MSX (or reset it).
2. Hold the **F** key while you switch on the MSX. Keep holding it until the screen turns red and shows "Nowind Flash Writer Mode".

Every button on this screen needs Flash Writer Mode. If the MSX isn't in it, the app says so within a few seconds, and nothing is changed.

### Choose the firmware

- **Download.** Tap the edit button and enter the link to a firmware file.
  - A name without dots stands for `https://<name>.nl/nowind/files/nowind.rom`. A link that contains `://` is used exactly as you type it.
  - **Check** downloads the file and shows its version and size, for example *Nowind Interface v4.3 · Nowind Flash Writer v2.1 · 512 KB*. Then **Save** it. If the check fails, you can still choose **Save anyway**.
- **File on this phone.** Tap **Choose** and pick a `.rom` file, for example a backup you made earlier.

The app refuses files that don't look like Nowind firmware: a file without a ROM header, or one larger than 4 MB.

### Erase method

- **Whole flash rom** works on every interface. It takes a few minutes and also erases a ROM disk in the flash memory.
- **Only the sectors that are written** is faster (like `nwhostapp --glash`). **Use it only on the V2 small form factor interface; the V1 interface does not support it.** Use **Check version** first if you aren't sure which interface you have.

### The buttons

- **Check version** reports everything the interface can tell about itself, and changes nothing:
  - the interface (V1 or V2) and its flash chip, for example *AMD AM29F040, 512 KB* or *ST M29F032-D, 4 MB*
  - the firmware version and the flash writer version
  - the USB chip, VID/PID, serial number and power settings

  An older flash writer doesn't answer the ID question; then the interface shows as *unknown, probably V1*.
- **Back up** saves the current firmware (the first 512 KB of the flash memory) as `Download/nowindcompanion/firmware/nowind-YYMMDD-HHmmss.rom`. To restore it later, choose that file as **File on this phone** and tap **Update**. On a V2, a ROM disk stored after the first 512 KB isn't part of the backup.
- **Verify** checks whether the interface already has the chosen firmware, and changes nothing. It also shows the versions inside the chosen file, so you can compare them with **Check version**.
- **Update** erases the flash memory, writes the firmware and verifies it. It asks you to confirm first. While it runs:
  - Don't switch off the MSX or disconnect the phone.
  - The phone keeps its screen on.
  - You can leave the screen; the update continues.

When the update is done, switch the MSX off and on.

## The Nowind screen

The **Nowind** entry of the main menu shows:

- **Interface:** the connection state, the hosted disk and the USB device.
- **Transfer speed:** read and write speeds, with **Reset** to start counting again.
- **This device:** the phone, and a link to the **Debug log**. The log shows every command from the MSX when **Enable debug information** is on.

## Files on the phone

Everything lives in `Download/nowindcompanion`. You can see it from a computer over USB file transfer, and it stays when you remove the app.

| Folder or file | Contents |
|---|---|
| `disks/` | downloaded disk images (`.dsk`, `.zip`) |
| `roms/` | downloaded ROMs (`.rom`) |
| `firmware/` | firmware backups (`nowind-YYMMDD-HHmmss.rom`) |
| `now/` | the files of the `NOW:` device |
| `settings.json` | the settings of the app; read back after a reinstall |

**Adding your own files.** Copy your own `.dsk` files into `disks/` and `.rom` files into `roms/`. They appear in the Library.

**Copying from a Windows PC fails?** If copying to the phone stalls halfway and nothing arrives, try another USB port (preferably USB 2) or another cable. The problem is in the USB connection, not in the folder.

## Troubleshooting

| Problem | What to try |
|---|---|
| The app doesn't find the interface | Check the OTG cable or adapter, and that the phone supports USB host. Reconnect the cable. |
| The MSX doesn't boot from the disk | Insert the cartridge with the MSX off, try another slot, and connect the phone before you switch on the MSX. |
| A game fails with DOS2 | Turn off **Enable DOS2**, or use `_IMAGE("...+1")`, then reset. |
| A ROM shows "not supported yet" | MegaROMs, ROMs over 32 KB and ROMs for page 0 can't be started yet. Look for a disk version in the archive. |
| A ROM keeps restarting or crashes | Switch the MSX off and on once; a reset keeps the RAM. |
| Hosting stops when the screen goes off | Turn on **Keep hosting in the background** in the settings mmenu. |
| The firmware update can't reach the MSX | Hold **F** at power on until the red "Nowind Flash Writer Mode" screen appears. |
| Something else | Turn on **Enable debug information**, and look at the debug log on the Nowind screen. Report the issue. |

Report issues to at https://github.com/janwilmans/nowindcompanion/issues

## About the Nowind Interface

The Nowind Interface is an MSX cartridge with an FTDI FT245 USB chip and flash memory that holds its firmware: the Nowind disk ROM, MSX-DOS 1 and 2, a ROM drive and the `NOW:` device. It reads at about 320 KB/s on a 7 MHz MSX.

| Version | Made by | Flash memory |
|---|---|---|
| Version 1 | first batch, later made by Sunrise | 512 KB |
| Version 2 | small form factor, Sunrise (2013) | 4 MB |

Both versions use the same USB protocol, so Nowind Companion and `nwhostapp` work with either.

More information, the PC host `nwhostapp` and the firmware are on [nullptr.nl/projects/nowind/cartridge](https://nullptr.nl/projects/nowind/cartridge/).
