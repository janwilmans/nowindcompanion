package nl.myquest.nowindcompanion

enum class DetectedNowindVersion {
    None {
        override fun toString(): String {
            return "None"
        }
    },
    V1 {
        override fun toString(): String {
            return "Nowind interface V1"
        }
    },
    V2 {
        override fun toString(): String {
            return "Nowind interface V2"
        }
    }
}

enum class MsxVersion(val value: Int) {
    One(0),
    Two(1),
    TwoPlus(2),
    TurboR(3);
}

fun toMsxVersionEnum(value: Int): MsxVersion? {
    return enumValues<MsxVersion>().find { it.value == value }
}

enum class NowindCommand(val value: Int) {
    DSKIO(0x80),
    DSKCHG(0x81),
    GETDPB(0x82),
    CHOICE(0x83),
    DSKFMT(0x84),
    DRIVES(0x85),
    INIENV(0x86),
    GETDATE(0x87),
    DEVICEOPEN(0x88),
    DEVICECLOSE(0x89),
    DEVICERNDIO(0x8a),
    DEVICEWRITE(0x8b),
    DEVICEREAD(0x8c),
    DEVICEEOF(0x8d),
    AUXIN(0x8e),
    AUXOUT(0x8f),
    MESSAGE(0x90),
    CHANGEIMAGE(0x91),
    GETDOSVERSION(0x92),
    CMDREQUEST(0x93),
    BLOCKREAD(0x94),
    BLOCKWRITE(0x95),
    CPUINFO(0x96),
    COMMAND(0x97),
    STDOUT(0x98)
}

public fun toCommandEnum(value: Int): NowindCommand? {
    return enumValues<NowindCommand>().find { it.value == value }
}

enum class BlockRead(val value: Int) {
    EXIT_MORE_DATA_AHEAD(0),
    FASTTRANSFER(1),
    SLOWTRANSFER(2),
    EXIT(3),
    ERROR(128)
}
