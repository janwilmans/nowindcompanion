class MessageList {

    public var messages = mutableListOf<String>()

    public final fun write(message: String)
    {
        messages.add(message)
    }


}