package nl.myquest.nowindcompanion

class MessageList(val data: List<String> = listOf()) {

    fun add(message: String) : MessageList {
        var list = data.takeLast(24).toMutableList()
        list.add(message)
        return MessageList(list)
    }
}