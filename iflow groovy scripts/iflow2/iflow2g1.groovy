import com.sap.gateway.ip.core.customdev.util.Message
def Message processData(Message message) {
    def body = message.getBody(String) ?: ""
    def parts = body.split("\n", 2)
    def first = (parts[0] ?: "").trim()
    if (!first.startsWith("#meta")) throw new IllegalArgumentException("Missing #meta control line")
    def props = [:]
    first.replaceFirst("#meta", "").trim().split("\\s+").each { tok ->
        def kv = tok.split("=", 2)
        if (kv.length == 2) props[kv[0]] = kv[1]
    }
    if (!props.source || !props.target) throw new IllegalArgumentException("#meta must include source and target")
    props.each { k, v -> message.setProperty(k, v) }
    message.setBody(parts.length > 1 ? parts[1] : "")
    return message
}