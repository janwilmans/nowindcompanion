class MessageList {

    var data = mutableListOf<String>()

    fun write(message: String)
    {
        data.add(message)
    }
}