# nowindcompanion

# Nowind Interface Host <> MSX Communication Protocol Description

## Objective

This protocol allow an MSX Home Computer to communicate with an USB host.
The communication includes but is not limited to accessing disk-images on the host device.
The host device can be a PC or any device with USB OTG-support like a mobile phone.

## Scope

This protocol applies specifically to the Nowind Interface V1, developed by Jan Wilmans and Aaldert Dekker between 2010 and 2012. It is also applicable to the Nowind Interface V2, produced and sold by Sunrise. Although the V2 version features more internal SRAM and a smaller form factor, the protocols for both versions are identical.
The 'Nowind Interface' is a dedicated cartridge designed to be a low cost way to let an MSX computer access modern hardware.

The protocol was specifically designed around the hardware properties of the 'Nowind Interface' and contains specific sequences to deal with data loss.
If the hardware used is not identical to the original design it is unlikely to function properly without modifications to the protocol.

## Definitions and Abbreviations

-   USB Host Device: a PC running Windows, Linux or MacOS, or an android phone. (IPhone not supported)
-   Nowind Interface: the physical cartridge that is inserted into the MSX Home Computer
-   Nowind Protocol: the communication protocol between the MSX Disk ROM and and the USB Host Device
-   Nowind Companion: Nowind Host Application Software for Android phones
-   MSX: MSX Computer running a custom ROM also known as 'the client software'
-   nwhostapp: Nowind Host Application Software for Linux, Windows and MacOS.
-   NowindHost: the host-side protocol implementation (`nowindlibraries/nwhost/NowindHost.cc`) that this document is derived from.

## Roles and Responsibilities

The USB Host Device is a service waiting for commands from the Nowind Interface connected to the MSX. When the MSX attempts to read from a disk this request is send to the Host where it reads from a file instead of a physical disk.
This relationship can in theory be reversed so the the MSX starts waiting for commands from the Host. There is some preliminary work done to make this possible but there are no actual commands implemented.

Most commands (such a read/write to disk) follow a pattern roughly like this:

-   the Host is waiting for a command
-   the MSX sends a command
-   the Host queues a series of replies
-   the MSX starts waiting for a reply, reads it and sends back a 'marker' to acknowledge the reply.
-   the Host checks the 'marker' and queues a re-transmission if it is incorrect.
-   the MSX keeps waiting for more replies until it receives a 'done' command, or a timeout occurs.
-   the Host returns to a 'waiting for command' state after all replies have been acknowledged or a timeout occurs.

## Materials and Equipment

For the system to operate you will need:

-   a physical Nowind Interface cartridge
-   an MSX Computer (aka 'the client')
-   a USB Host Device

Alternatively, the whole operation including the MSX Home Computer, the Nowind Interface and the Host application can be emulated using the [Nowind MSX Emulator](https://github.com/janwilmans/nowindlibraries/tree/master/emuv1)

## Procedure

### Conventions

-   All multi-byte values are little-endian (low byte first). `send16(x)` means `x & 0xFF`, `x >> 8`.
-   Direction is named from the MSX point of view: _MSX -> Host_ is *written* by the MSX, _Host -> MSX_ is *read* by the MSX.
-   The Host keeps a FIFO of bytes waiting to be read by the MSX (the "response"). When the MSX reads while the FIFO is empty the interface returns `0xFF`. This is a hardware property, the _READ signal never reaches the FT245 if its buffer is empty. Both the hardware and the protocol rely on this.
-   Command names, byte values and states below are taken from `NowindHost.cc`, `NowindHost.hh`, `BlockRead.cc` and `DataBlock.cc`.

### Framing: Host -> MSX (reply header)

Every reply from the Host starts with a 3 byte header:

```
0xFF 0xAF 0x05
```

The leading `0xFF` is needed because "the first read might fail" (a hardware design choice); the MSX waits for `0xAF 0x05`.

### Framing: MSX -> Host (command)

Every command from the MSX is:

```
0xAF 0x05 <C> <B> <E> <D> <L> <H> <F> <A> <CMD>
```

-   `0xAF 0x05` is the sync header, followed by the Z80 registers `C B E D L H F A` (8 bytes) and the command byte `CMD` (1 byte). The 9 bytes after the header are called `cmdData`.
-   Some commands are followed by extra data (parameters), see the command table.
-   Sending `0xAF 0xFF` instead signals an **MSX reset** to the Host (device handles are reset).
-   Sending `0xAF 0xAF` is tolerated (stays waiting for `0x05`); any other byte after `0xAF` falls back to waiting for `0xAF`.

### Host state machine

| State                        | Waiting for                                                                       |
| ---------------------------- | --------------------------------------------------------------------------------- |
| `STATE_SYNC1`                | `0xAF`                                                                            |
| `STATE_SYNC2`                | `0x05` (or `0xFF` = MSX reset)                                                    |
| `STATE_RECEIVE_COMMAND`      | the 9 `cmdData` bytes                                                             |
| `STATE_RECEIVE_PARAMETERS`   | `parameterLength` extra bytes (37 for the BDOS proxy commands that need an FCB)  |
| `STATE_EXECUTING_COMMAND`    | the command is (still) executing, every received byte re-invokes the command      |
| `STATE_DISKWRITE`            | `0xAA <transferSize bytes> 0xAA` (old disk write)                                 |
| `STATE_BLOCKREAD`            | acknowledge ('tail') bytes of transmitted data blocks                             |
| `STATE_BLOCKWRITE`           | data blocks of a write (new disk write, not used, untested)                       |
| `STATE_DEVOPEN`              | 11 bytes (device filename)                                                        |
| `STATE_IMAGE`                | filename of a disk image, terminated by `0x00` or `:` or at most 40 bytes         |
| `STATE_MESSAGE`              | zero terminated debug message, at most 239 bytes                                  |
| `STATE_CPUINFO`              | 11 data bytes + 34 stack bytes (45 bytes)                                         |
| `STATE_RECEIVE_DATA`         | `reg_B` bytes of API command data                                                 |

After a command has completed the Host returns to `STATE_SYNC1`, unless the command explicitly selects another state.

**Timeout:** if more than 500 ms pass between two bytes received from the MSX while the Host is not in `STATE_SYNC1`, the Host purges its response FIFO and returns to `STATE_SYNC1` (the byte that caused the detection is then processed as a new first byte).

### Command overview

| CMD            | Name                | Extra data MSX -> Host                  | Reply Host -> MSX                                                          |
| -------------- | ------------------- | --------------------------------------- | -------------------------------------------------------------------------- |
| `0x0D`..`0x30` | BDOS proxy (below)  | 37 byte FCB for `0x0F`, `0x11`, `0x27`  | handled by `BDOSProxy`, not described here                                 |
| `0x80`         | DSKIO               | write: see _DSKIO write_                | read: see _DSKIO read_                                                     |
| `0x81`         | DSKCHG              | -                                       | header, changed (`0xFF`/`0x00`), media descriptor (or `0xFF` dummy)        |
| `0x82`         | GETDPB              | -                                       | header, `E`, `D` (destination address), 18 byte DPB                        |
| `0x83`         | CHOICE              | -                                       | not implemented                                                            |
| `0x84`         | DSKFMT              | -                                       | not implemented                                                            |
| `0x85`         | DRIVES              | -                                       | header, flags, `A` (bit 7 set unless other diskroms allowed), number of drives                              |
| `0x86`         | INIENV              | -                                       | header, drive number of the ROM disk (`255` if none)                       |
| `0x87`         | GETDATE             | -                                       | header, day, month, year (16 bit)                                          |
| `0x88`         | DEVICEOPEN          | 11 byte filename                        | handled by `Device`                                                        |
| `0x89`         | DEVICECLOSE         | -                                       | handled by `Device`                                                        |
| `0x8A`         | DEVICERNDIO         | -                                       | not implemented                                                            |
| `0x8B`         | DEVICEWRITE         | -                                       | handled by `Device`                                                        |
| `0x8C`         | DEVICEREAD          | -                                       | handled by `Device`                                                        |
| `0x8D`         | DEVICEEOF           | -                                       | not implemented                                                            |
| `0x8E`         | AUXIN               | -                                       | header, one character read from the Host's stdin                           |
| `0x8F`         | AUXOUT              | -                                       | none, character in `A` is printed on the Host's stdout                     |
| `0x90`         | MESSAGE             | zero terminated string (max 239 bytes)  | none, Host logs registers and message                                      |
| `0x91`         | CHANGEIMAGE         | filename (max 40 bytes, ends at 0 or `:`) | none, Host inserts the image in drive `A`                                |
| `0x92`         | GETDOSVERSION       | -                                       | header, DOS version (1 or 2)                                               |
| `0x93`         | CMDREQUEST          | -                                       | see _Command requests_                                                     |
| `0x94`         | BLOCKREAD           | -                                       | not dispatched by the Host (reserved)                                      |
| `0x95`         | BLOCKWRITE          | -                                       | not dispatched by the Host (reserved)                                      |
| `0x96`         | CPUINFO             | 11 data bytes + 34 stack bytes          | none, Host reports CPU info                                                |
| `0x97`         | COMMAND (API)       | `B` bytes of API arguments              | see _API commands_                                                         |
| `0x98`         | STDOUT              | -                                       | none, character in `A` is collected into a line and logged                 |

Commands with an unknown `CMD` are logged and ignored (no reply).

### DSKIO

Register usage on entry (taken from `cmdData`): `A` = drive number, `B` = number of sectors, `C` = media descriptor / high sector bits, `DE` = logical sector number, `HL` = transfer address, `F` carry flag = 1 for write, 0 for read.

-   The drive number `A` is used as an index into the Host's list of drives. If the drive does not exist, or has no disk inserted, **no reply is sent** and the MSX is expected to time out.
-   Start sector = `DE`. If `C < 0x80` then `C` supplies bits 16..23 of the sector number (FAT16 / harddisk addressing): `sector = DE + (C << 16)`. Sector size is fixed at 512 bytes.

#### DSKIO read (blockRead)

The Host reads `B * 512` bytes from the disk image, and sends them to the MSX as a series of data blocks, using the MSX transfer address `HL`. The Host switches to `STATE_BLOCKREAD`.

Each transmission after the header starts with an _action_ byte:

| Action | Name                         | Meaning                                                                                    |
| ------ | ---------------------------- | ------------------------------------------------------------------------------------------ |
| 0      | `BLOCKREAD_EXIT_MORE_DATE_AHEAD` | stop page 0/1 transfer, more data follows for page 2/3 (MSX continues in blockRead23)  |
| 1      | `BLOCKREAD_FASTTRANSFER`     | fast transfer follows                                                                      |
| 2      | `BLOCKREAD_SLOWTRANSFER`     | slow transfer follows                                                                      |
| 3      | `BLOCKREAD_EXIT`             | done, transfer complete                                                                    |
| 128    | `BLOCKREAD_ERROR`            | error (defined, but not currently sent by `NowindHost`)                                    |

Each _data block_ consists of 128 bytes (fast) or fewer than 128 bytes (slow) and is framed by a **marker**: the lowest byte value (0..255) that does not occur in the block's data. The marker is sent as `head marker`, then the data, then the same marker again as `tail marker`. The MSX echoes the tail marker it received back to the Host as acknowledgement.

**Fast transfer** (block size is a multiple of 128 bytes, used for the bulk of the data):

```
header, 0x01, send16(address of the END of the transfer), N (number of 128-byte blocks, 0 means 256),
   then N times:  marker, 128 data bytes in REVERSE order (last byte first), marker
```

The data is reversed and the end address is sent because the MSX writes with the stack pointer (back to front). Blocks are queued by the Host in reverse order, but sent from the front of the queue.

**Slow transfer** (less than 128 bytes remain, or block of an unaligned remainder):

```
header, 0x02, send16(start address), send16(size), marker, size data bytes in normal order, marker
```

**Acknowledge:** after each block the MSX sends one byte, the tail marker it read. The Host compares it with the marker of the oldest unacknowledged block:

-   equal: the block is dropped from the queue; when the queue is empty the Host sends the next batch of blocks (`blockReadContinue`), or, if all data was sent, `header, 0x03` (`BLOCKREAD_EXIT`) and returns to `STATE_SYNC1`.
-   not equal: the Host resends that block: `header, 0x01, send16(end address of that block), 0x01, <block>` (fast) or `header, 0x02, send16(address), send16(size), <block>` (slow), and moves the block to the back of the queue. On high MSX clock speeds some failures are expected and part of the design; the failed block is repeated.

Page handling: while the transfer address is < 0x8000 the Host limits a batch so it does not cross the 0x8000 boundary. Once the remaining data continues at or above 0x8000 the Host first sends `header, 0x00` (`EXIT_MORE_DATE_AHEAD`), after which the MSX continues with the page 2/3 routine (blockRead23).

The Host reports the achieved speed (bytes / ms) when a read finishes.

Existing MSX-side description of the two routines (from the DiskROM):

-   DSKIO
    -   ROMDISK_DSKIO
    -   C_DSKIO
        -   blockRead (b = number of sectors to read/write, de = logical sector number, hl = transfer address)
            -   call blockRead01 (if transfer address < 0x8000)
            ```
            > - getHeaderInPage2 (wait for 0xAF 0x05)
            | - read 1 byte action (0, = goto blockRead23, 1 = fast, 2, = slow, other = done)
            |  - do action()
            |    - fastTransfer
            |    - blockReadTransfer
            |    - get 2 byte 'transfer address' (pointing to the end of the block, because SP is used to write back->front)
            |    - get 1 byte 'number of loops: N' (max. 256*128 = 32Kb, N=0 means 256.)
            |    - get 1 byte 'head marker', keep reading until != 255 (probably never wrong)
            |    - transfer N times 128 bytes
            |    - get 1 byte 'tail marker' and compare against the head-marker, keeps reading until correct marker is found
            \__ LOOP
            ```
            -   call blockRead23 (transfer address >= 0x8000)
            ```
            > - getHeaderInPage2 (wait for 0xAF 0x05)
            | - read 1 byte action (1 = fast, 2, = slow, other = done)
            |  - do action()
            |    - slowTransfer
            |    - get 2 byte 'transfer address'
            |    - get 2 byte 'size'
            |    - get 1 byte 'head marker' (exit if 255, because we defined that as an invalid marker, probably never)
            |    - transfer N times 128 bytes
            |    - get 1 byte 'tail marker' and compare against the start-marker, keeps reading until correct marker is found
                \__ LOOP
            ```
        -   blockWrite

#### DSKIO write (currently used: "old diskwrite")

The Host only accepts the write if the disk is not write protected; otherwise it replies `header, 0x01, 0x00` (write protected) and the command ends. Otherwise the transfer proceeds in chunks of at most 240 bytes:

1.  Host -> MSX: `header, 0x00 (data ahead), send16(address), send16(chunk size), 0xAA (block sequence number)`.
2.  The Host enters `STATE_DISKWRITE`.
3.  MSX -> Host: `0xAA`, `chunk size` data bytes, `0xAA`.
4.  If both `0xAA` bytes match the chunk is accepted. If the chunk ended exactly at address 0x8000 and more data is left, the Host sends `header, 0xFE` ("more data for page 2/3"). If they do not match, the Host purges its response FIFO and requests the same chunk again.
5.  Repeat from 1 until all data has been received. Then the Host writes the sectors to the disk image and replies `header, 0xFF` (done).

A chunk never crosses the 0x8000 boundary; the chunk size is limited so it ends on it. At most 128 sectors are accepted per command (`min(128, B)`).

A newer implementation (`BlockWrite`, `STATE_BLOCKWRITE`, end code `BLOCKWRITE_END = 255`, page 2/3 code `0xFE`, error `128`) exists but is disabled (`USE_OLD_DISKWRITE` is defined); its code is marked "not used yet, completely untested", and it does not currently write the received data to the disk.

### DSKCHG (0x81)

Host -> MSX: `header`, then `0xFF` + media descriptor if the disk was changed (the Host reads the first byte of sector 1, the first FAT sector), or `0x00` + `0xFF` (dummy) if unchanged. With no disk, no reply is sent.

### GETDPB (0x82)

The Host reads the boot sector (sector 0) of the disk and converts it to an MSX Disk Parameter Block. Reply: `header`, `E`, `D` (destination address on the MSX), and 18 DPB bytes (the drive number byte, offset 0, is not sent):

| Offset | Field      | Value                                                                       |
| ------ | ---------- | --------------------------------------------------------------------------- |
| 1      | ID         | media type (boot sector offset 21)                                          |
| 2-3    | SECSIZ     | bytes per sector                                                            |
| 4      | DIRMSK     | `bytesPerSector/32 - 1`                                                     |
| 5      | DIRSHFT    | number of 1-bits in DIRMSK                                                  |
| 6      | CLUSMSK    | `sectorsPerCluster - 1`                                                     |
| 7      | CLUSSHFT   | number of 1-bits in CLUSMSK, plus 1                                         |
| 8-9    | FIRFAT     | first FAT sector (boot sector offset 14)                                    |
| 10     | FATCNT     | number of FAT copies                                                        |
| 11     | MAXENT     | root directory entries                                                      |
| 12-13  | FIRREC     | first data sector                                                           |
| 14-15  | MAXCLUS    | `(totalSectors - FIRREC) / sectorsPerCluster + 1`                           |
| 16     | FATSIZ     | sectors per FAT                                                             |
| 17-18  | FIRDIR     | first directory sector `FIRFAT + FATCNT * FATSIZ`                           |

If the boot sector's 16-bit total sector count is 0, the 32-bit count at offset 32 is used. On a read error or without a disk no reply is sent.

### DRIVES (0x85)

The MSX sends the number of drives already installed in `A`. Host -> MSX: `header`, flags (`0x02` if phantom drives are enabled, else `0`), `A` (with bit 7 set unless "allow other disk roms" is enabled), number of drives (minimum 1). The Host remembers `A` as the drive offset and the index of the ROM disk drive (for INIENV).

### INIENV (0x86)

Host -> MSX: `header`, drive number of the ROM disk (as found by DRIVES; `255` if none).

### GETDATE (0x87)

Host -> MSX: `header`, day of month, month (1-12), year (16 bit, e.g. 2026).

### GETDOSVERSION (0x92)

The MSX sends the MSX type in `A` (0=MSX1, 1=MSX2, 2=MSX2+, 3=MSX Turbo R). Host -> MSX: `header`, `2` if DOS2 is enabled and the machine is an MSX2 or MSX2+, otherwise `1`.

### AUXIN / AUXOUT

AUXIN: the Host reads one character from stdin and sends it back. AUXOUT: the Host prints the character in `A` to stdout.

### STDOUT (0x98)

The Host buffers characters (register `A`) until a CR, LF, `0x00` or `$` is received, then logs the buffered line as `MSX says> ...`. No reply is sent.

### Command requests (CMDREQUEST, 0x93)

The MSX asks whether the Host has a command for it to execute. `B` selects the type, `C` an argument.

-   `B = 0` (at startup, from the DiskROM `INIHDR` sequence): `C = 0` restarts at the first startup request, `C != 0` requests the next. Host -> MSX: `header` followed by the bytes of the next startup request, or a single `0x00` when there are no more. The startup queue is not consumed; the same requests are sent at each MSX boot until the user application clears them. The Host always has one default startup request `01 00` ("requestWait", marked as a test).
-   `B = 1` (anytime): Host -> MSX: `header` followed by `0x00` if the queue is empty. Otherwise one request is removed from the queue and sent as `header`, a continuation flag (`0x00` = this was the last request, `0x01` = more requests are waiting), followed by the request bytes.
-   Other values of `B` are logged and no reply is sent.

### API commands (COMMAND, 0x97)

`C` selects the API, `A` must be the extended BIOS function number (4), `B` is the number of bytes of argument data. Currently only `API_NOWMAP` (`C = 0`) exists:

1.  MSX -> Host: command, with `A = 4`, `B > 0`, `DE` = MSX address for the result.
2.  MSX -> Host: `B` bytes of arguments (the command line).
3.  Host -> MSX: the result as a zero terminated text, sent to address `DE` using the blockRead mechanism described above.

The `nowmap` arguments are `<drive_id> <partition> [/Nx] [/L]`. `/L` lists the drives; otherwise the active partition of a harddisk image is set. If `A` != 4 or `B` == 0 the command is logged as an error and ignored.

## Possible bugs and issues in the implementation (NowindHost.cc)

These were found by reading the code; they are not (all) verified on hardware. Roughly ordered by severity.

### Likely bugs

1.  **API data state is overwritten.** `executeCommand()` handles `C_COMMAND` via `apiCommand()`, which calls `setState(STATE_RECEIVE_DATA)`. But `executeCommand()` ends with `setState(nextState)` and `nextState` is still `STATE_SYNC1`, so the Host never waits for the argument bytes. The command line sent by the MSX is then interpreted as sync bytes and `apiReceiveData()` is never reached (`nowmap` cannot work). The state set inside `apiCommand()` should be returned through `nextState`.
2.  **DSKIO with a missing drive/disk does not reset state.** In `case C_DSKIO` the `return` for "no such drive" skips `setState(nextState)`, so the Host stays in `STATE_EXECUTING_COMMAND` and `activeCommand` stays `0x80`. Every following byte from the MSX re-executes DSKIO (and `getDisk()` is re-evaluated on a command register that is now stale) until the 500 ms timeout resets the state. Contrast with `DSKCHG`/`GETDPB`, which return from the helper function and do reset the state.
3.  **Disk write errors are reported as success.** In `doDiskWrite1()` a non-zero result from `writeSectors()` is only logged; the Host still replies `0xFF` ("done") to the MSX.
4.  **Writes of more than 128 sectors are silently truncated.** `diskWriteInit_old()` limits the buffer to `min(128, B)` sectors, but the final reply is still "done". The remaining sectors are never written and the MSX is not told. Likewise `B = 0` is not interpreted as 256 sectors (reads/writes zero sectors; `&buffer[0]` on an empty vector is also undefined behaviour).
5.  **Reads of 128 or more sectors overflow the size.** `BlockRead::init(word address, word size, ...)` takes a `word` (the transfer code assumes at most 16 bit, see `BlockRead.hh`), while `diskReadInit()` passes `sectorAmount * 512` (up to 130560). `128 * 512 = 65536` wraps to `0`. In addition, in `BlockRead::blockReadHelper()` the number of blocks is `byte blocks = size / 128`, so a size of exactly `0x8000` (256 blocks) becomes `0`: a fast transfer header with `N = 0` is sent, no data block is queued and a following acknowledge would hit `assert(dataBlockQueue.size() != 0)` (undefined behaviour without asserts).
6.  **Wrong-address underflow in disk write.** In `doDiskWrite1()` a chunk that crosses 0xFFFF (address + size wraps past 0x10000) also satisfies `(address ^ endAddress) & 0x8000` and then `transferSize = 0x8000 - address` underflows (address >= 0x8000), producing a huge transfer size.
7.  **`auxIn()` sends the header twice.** `response->sendHeader()` is called both before and after reading stdin; the MSX will see `FF AF 05 FF AF 05 <char>` and read the second header as data. It also blocks the protocol thread on `std::cin` and does not handle EOF/failure (`input` is uninitialized then).
8.  **`apiReceiveData()` does not terminate the argument string.** The string is built from `extraData` with `string(char*)`, which needs a terminating 0, but only `extraData[0]` was zeroed and the MSX sends exactly `B` bytes. Stale bytes from earlier commands are appended unless the MSX includes the terminator in `B`. If `B` is larger than `extraData`'s size (it is a 8 bit value, 255 max) the write is out of bounds (no bounds check, the `assert` is not used in this state).
9.  **`nowMap()` null pointer / buffer problems.** `dynamic_cast<Image*>(drives[i]->getSectorMedium())` is used without a null check (both when listing and when selecting); a drive that is not an `Image` (or has no medium) crashes the host. `sprintf` into `char temp[250]` with a user controlled `getDescription()` can overflow. The documented option `/Nx` is not parsed: with it `args.size() != 2` and an error is returned. Double or trailing spaces create empty tokens (`split(..., keep_empty = true)`) and also fail the 2 argument check. The partition number is not validated before `SetActivePartition()`. Error messages use `\n` while the listing uses `\r\n`.
10. **`STATE_IMAGE` truncates filenames at `:`.** The image name ends at `0x00`, `:` or after 40 bytes. A Windows path such as `C:\disks\game.dsk` is cut to `C`. A 40 byte name has no terminator and is cut off.
11. **`GETDPB` can divide by zero.** `bytesPerSector` or `sectorsPerCluster` read from a non-FAT/blank boot sector (for example the MBR of a harddisk image, or an empty disk) can be 0, giving a division by zero in `entriesPerSector`/`maxClusters`. `numberOfSectors - firstRecordSector` is unsigned and underflows if the layout is inconsistent. `directorySizeInSectors` is not rounded up (see the `TODO`).
12. **`DSKCHG` assumes the FAT starts at sector 1.** The media descriptor is read from sector 1 instead of from the boot sector's first FAT sector (or from the boot sector itself), and a read error silently reports descriptor `0` (see `TODO read error`).

### Weaknesses of the protocol / implementation

-   **Block sequence number is constant.** In the old disk write, the "sequence number" is always `0xAA`. It can only detect a truncated or shifted block, not a lost or duplicated one. There is no checksum.
-   **No error replies for reads.** `BLOCKREAD_ERROR` (128) is defined but never sent. A failed `readSectors()` or a missing disk gives no reply; the MSX only learns about it through a timeout.
-   **Page-boundary state inconsistent.** `BlockRead::init` uses `transferingToPage01 = (startAddress <= 0x8000)` while `blockRead()` treats `< 0x8000` as page 0/1. For a start address of exactly `0x8000` and a transfer that needs more than one batch, the Host sends a spurious `BLOCKREAD_EXIT_MORE_DATE_AHEAD` (0) in the middle of a page 2/3 transfer, which the MSX blockRead23 routine treats as "done".
-   **Endless resend on a corrupt channel.** Any acknowledge byte that does not match the marker of the oldest block triggers a resend, including stray bytes. There is no retry limit; recovery relies on the MSX timing out and the Host's 500 ms timeout.
-   **Resends can race with outstanding blocks.** The retry is queued after the blocks of a fast batch that are already in the FIFO and the failed block is moved to the end of the queue; this works only because the MSX acknowledges in order. This is a delicate invariant that is not documented anywhere.
-   **`commandRequestedAtStartup()` keeps its index in a function-level `static`.** It is shared by all `NowindHost` instances (for example when emulating more than one interface) and is only reset when the MSX sends `C = 0`. A startup request with no length field must be self-describing for the MSX.
-   **`stdOutCatch()` also uses a function-level `static` buffer** (shared between instances, unbounded, never flushed without a terminator, and `$` is treated as a line end even when it is a printable character).
-   **Local variable `command` shadows the member `command`** in `commandRequestedAtStartup()` and `commandRequestedAnytime()` (harmless now, but fragile).
-   **Timeouts silently discard state.** After a 500 ms gap the response FIFO is purged; a slow host (for example an Android phone doing disk I/O) that takes longer than this between two MSX bytes loses its queued replies.
-   **Unknown/partial commands.** `CHOICE`, `DSKFMT`, `DEVICERNDIO`, `DEVICEEOF`, `BLOCKREAD` and `BLOCKWRITE` are listed in the enum but not handled and fall through to "Unknown command" with no reply.
-   **`DRIVES`:** `driveOffset` (the `A` register) is only used by `nowmap` for display. Whether `A` of later commands is already corrected for the offset (so that it can be used directly as an index into the Host's drive list) is not verified in the host code.

## Safety precautions and Compliance requirements

If you connect your host PC to an MSX Computer it is recommended to connect only ONE of them to the earth ground.
MSX Computers typically do not have a earth ground lead, but it is possible to create a ground loop through a monitor or amplifier!
Make sure nothing attached to the MSX powered by a grounded lead.

Note that is also possible to create this problem using a phone if you charge the phone using a grounded device.
Although problems with these restrictions are extremely rare in practice, failing to comply to these requirements can permanently damage the MSX Computer and the Nowind Interface.

9. Quality Control
   Outline quality control measures to ensure the protocol is followed correctly.
   Include checkpoints and criteria for quality assurance.
10. Troubleshooting
    Provide a troubleshooting guide for common issues that may arise during the procedure.
11. Documentation and Reporting
    Specify the documentation requirements for recording results and observations.
    Include templates or forms if applicable.
12. References
    List all references used to develop the protocol.
    Include scientific articles, books, or standard operating procedures (SOPs).
13. Appendix
    Include any additional information that supports the protocol, such as detailed calculations, additional figures, or extended data.
