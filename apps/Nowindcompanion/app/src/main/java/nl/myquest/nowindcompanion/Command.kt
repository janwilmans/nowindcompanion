package nl.myquest.nowindcompanion

class Command(val bc: Int, val de: Int, val hl: Int, val f: Int, val a: Int, val cmd: Int) {

    override fun toString(): String {
        val commandName = toCommandEnum(cmd)?.name ?: "unknown"
        return "$commandName (%H) BC=%04X, DE=%04X, HL=%04X, F=%X, A=%X".format(cmd, bc, de, hl, f, a)
    }

    private fun getLow(value: Int): Int {
        return value and 0xff;
    }

    private fun getHigh(value: Int): Int {
        return (value / 256) and 0xff;
    }

    fun getB(): Int {
        return getHigh(bc)
    }

    fun getC(): Int {
        return getLow(bc)
    }

    fun getD(): Int {
        return getHigh(de)
    }

    fun getE(): Int {
        return getLow(de)
    }

    fun getH(): Int {
        return getHigh(hl)
    }

    fun getL(): Int {
        return getLow(hl)
    }

    fun toEnum(): NowindCommand? {
        return toCommandEnum(cmd)
    }
}
