import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    def raw = (message.getBody(java.lang.String) ?: "")

    // This tenant strips both custom headers AND the query string, so control
    // values ride on an optional FIRST LINE of the payload:
    //   #meta source=system1 target=system2
    // We read it, then strip it so the converters get a clean payload.
    def src = null
    def tgt = null
    def parts = raw.split("\r?\n", 2)
    if (parts.length > 0 && parts[0].trim().startsWith("#meta")) {
        def meta = parts[0]
        def ms = (meta =~ /source=(\S+)/); if (ms.find()) src = ms.group(1)
        def mt = (meta =~ /target=(\S+)/); if (mt.find()) tgt = mt.group(1)
        raw = (parts.length > 1 ? parts[1] : "")
        message.setBody(raw)
    }

    // format: sniff the (clean) body's first non-space character
    def body = raw.trim()
    def fmt
    if (body.startsWith("{") || body.startsWith("["))  fmt = "json"
    else if (body.startsWith("<"))                      fmt = "xml"
    else                                                fmt = "csv"

    message.setProperty("format",       fmt)
    message.setProperty("sourceSystem", src)
    message.setProperty("targetSystem", tgt)
    message.setProperty("receivedAt",
        new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC")))

    return message
}
