; romloader.asm - starts a plain ROM (8, 16 or 32 KB) from a disk made by Nowind Companion, see RomDisk.kt
;
; The disk has no MSX-DOS, so Disk BASIC starts and runs AUTOEXEC.BAS:
;   10 CLEAR 200,&HC400:BLOAD"LOADER.BIN",R
; GAME.ROM is a 32 KB image of pages 1 and 2 ($4000-$BFFF); the app already placed and mirrored the ROM in it.
; The loader copies it to the RAM of pages 1 and 2 and calls the INIT address of the ROM header,
; the way the BIOS starts a cartridge.
;
; assemble with nowindlibraries/Release/sjasm.exe, see make.bat

ENASLT      equ $0024
CHPUT       equ $00A2
BDOS        equ $F37D               ; Disk BASIC entry for the BDOS functions
RAMAD1      equ $F342               ; slot of the RAM in page 1, set by the disk ROM
EXPTBL      equ $FCC1               ; slot of the main ROM

F_OPEN      equ $0F
F_SETDMA    equ $1A
F_RANDREAD  equ $27                 ; random block read

BUFFER      equ $8000               ; page 2 is RAM in BASIC

        output "romloader.bin"
        org $C400                   ; page 3, below a low disk work area; BASIC keeps its stack below it

start:
        ; page 1 needs RAM, a 32 KB MSX has none there
        call ramPage1
        ld hl,$4000
        ld a,(hl)
        cpl
        ld (hl),a
        cp (hl)
        push af
        call romPage1
        pop af
        ld hl,msgNoRam
        jp nz,fail

        ld de,fcb
        ld c,F_OPEN
        call BDOS
        or a
        ld hl,msgNoFile
        jp nz,fail
        ld hl,1
        ld (fcb+14),hl              ; records of 1 byte
        ld hl,0
        ld (fcb+33),hl              ; from the start of the file
        ld (fcb+35),hl

        ; $4000-$7FFF: the disk ROM is in page 1 while reading, so read into page 2 and copy
        call read16k
        call ramPage1
        ld hl,BUFFER
        ld de,$4000
        ld bc,$4000
        ldir
        call romPage1

        ; $8000-$BFFF: straight into place, this overwrites the BASIC program, there is no way back from here
        call read16k

        call ramPage1
        ld hl,($4000)
        ld de,$4241                 ; "AB"
        or a
        sbc hl,de
        ld hl,($4002)
        jr z,.header
        ld hl,($8000)
        or a
        sbc hl,de
        ld hl,($8002)
        jr z,.header
        call romPage1
        ld hl,msgNoHeader
        jp fail

.header:
        ld a,h
        or l
        jr nz,.init
        call romPage1               ; INIT 0: a BASIC program in ROM, not supported
        ld hl,msgNoInit
        jp fail

.init:
        ei
        call jumpHL
        ; the ROM returned from INIT, nothing else to start
stop:
        ei
        halt
        jr stop

jumpHL:
        jp (hl)

; reads the next 16 KB of GAME.ROM into BUFFER
read16k:
        ld de,BUFFER
        ld c,F_SETDMA
        call BDOS
        ld de,fcb
        ld hl,$4000
        ld c,F_RANDREAD
        call BDOS
        or a
        ret z
        pop hl                      ; do not return to the caller
        ld hl,msgRead
        ; fall through to fail

; prints the message in hl and stops
fail:
        ld a,(hl)
        or a
        jr z,stop
        call CHPUT
        inc hl
        jr fail

ramPage1:
        ld a,(RAMAD1)
        ld h,$40
        jp ENASLT

romPage1:
        ld a,(EXPTBL)
        ld h,$40
        jp ENASLT

fcb:
        db 0                        ; the default drive
        db "GAME    ROM"
        ds 25,0

msgNoRam:
        db "ROM loader: needs 64 KB RAM",13,10,0
msgNoFile:
        db "ROM loader: GAME.ROM not found",13,10,0
msgRead:
        db "ROM loader: could not read GAME.ROM",13,10,0
msgNoHeader:
        db "ROM loader: no ROM header",13,10,0
msgNoInit:
        db "ROM loader: BASIC ROMs are not supported",13,10,0
