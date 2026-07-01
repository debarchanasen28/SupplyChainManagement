import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
def Message processData(Message message) {
    def body = message.getBody(String) ?: ""
    def json
    try { json = new JsonSlurper().parseText(body) }
    catch (e) { throw new IllegalArgumentException("Body is not valid JSON") }
    if (!json.correlationId) throw new IllegalArgumentException("correlationId is required")
    def decision = (json.decision ?: "").toString().toUpperCase()
    if (decision != "APPROVED" && decision != "REJECTED")
        throw new IllegalArgumentException("decision must be APPROVED or REJECTED")
    json.decision = decision
    json.processedAt = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
    message.setProperty("correlationId", json.correlationId.toString())
    message.setProperty("decision", decision)
    message.setBody(JsonOutput.toJson(json))
    return message
}