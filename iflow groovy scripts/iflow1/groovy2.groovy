import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {

    // 1. Read the incoming JSON body (output of XML-to-JSON converter)
    def body = message.getBody(java.lang.String) as String
    def parsed = new JsonSlurper().parseText(body)

    // 2. Read the exchange properties set by the Content Modifier
    def props        = message.getProperties()
    def sourceSystem = props.get("sourceSystem")
    def targetSystem = props.get("targetSystem")
    def receivedAt   = props.get("receivedAt")
    def correlationId = props.get("correlationId")

    // 3. Normalize purchaseOrder to ALWAYS be a list
    //    (single PO = object, CSV batch = array)
    def poNode = parsed?.purchaseOrders?.purchaseOrder
    def poList = []
    if (poNode instanceof List) {
        poList = poNode
    } else if (poNode != null) {
        poList = [poNode]
    }

    // 4. Enrich every PO
    def nowUtc = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
    poList.each { po ->
        po.correlationId = po.poNumber          // per-PO correlation key
        po.sourceSystem  = sourceSystem
        po.targetSystem  = targetSystem
        po.status        = "SENT"
        po.createdAt     = receivedAt ?: nowUtc
        // cast quantity to a number when possible
        if (po.quantity != null) {
            try { po.quantity = (po.quantity as String).trim() as Integer }
            catch (ignored) { /* keep original value */ }
        }
    }

    // 5. Rebuild a clean canonical payload: { "purchaseOrders": [ {..}, {..} ] }
    def out = [ purchaseOrders: poList ]
    message.setBody(JsonOutput.toJson(out))

    // 6. Surface a header for logging / monitoring
    message.setHeader("X-Correlation-Id",
        correlationId ?: (poList ? poList[0].poNumber : ""))

    // 7. Strip inbound HTTP routing headers so the HTTP receiver uses its
    //    configured Address instead of being hijacked by CamelHttpUri.
    ["CamelHttpUri", "CamelHttpUrl", "CamelHttpPath",
     "CamelHttpQuery", "CamelHttpRawQuery"].each { message.getHeaders().remove(it) }

    return message
}