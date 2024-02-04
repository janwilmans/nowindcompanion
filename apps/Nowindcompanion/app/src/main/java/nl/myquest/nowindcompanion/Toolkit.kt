package nl.myquest.nowindcompanion

fun toHexString(bytes: ByteArray): String {
    val hexArray = "0123456789ABCDEF".toCharArray()
    val hexChars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        hexChars[i * 2] = hexArray[v ushr 4]
        hexChars[i * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

fun toHexString(values: List<Int>): String {
    val hexArray = "0123456789ABCDEF".toCharArray()
    val hexChars = CharArray(values.size * 2)
    for (i in 0..values.size - 1) {
        val v = values[i] and 0xff
        hexChars[i * 2] = hexArray[(v ushr 4) and 0x0f]
        hexChars[i * 2 + 1] = hexArray[v and 0x0f]
    }
    return String(hexChars)
}
