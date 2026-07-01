import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def body = message.getBody(String) ?: ""
    def parts = body.split("\n", 2)

    if (parts[0].startsWith("#meta")) {
        parts[0]
            .replaceFirst("#meta", "")
            .trim()
            .split("\\s+")
            .each { kv ->
                def p = kv.split("=", 2)
                if (p.size() == 2) {
                    message.setProperty(p[0], p[1])
                }
            }

        message.setBody(parts.size() > 1 ? parts[1] : "")
    }

    return message
}