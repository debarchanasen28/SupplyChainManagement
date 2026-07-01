import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {
    def body = message.getBody(String) ?: ""

    if (!body.trim()) {
        throw new IllegalArgumentException("Stock offer body is empty")
    }

    def json = new JsonSlurper().parseText(body)

    def correlationId = json.correlationId
    def poNumber = json.poNumber
    def decision = json.decision
    def offeredQuantity = json.offeredQuantity
    def requiredQuantity = json.requiredQuantity

    if (!correlationId) {
        throw new IllegalArgumentException("correlationId is required")
    }

    if (!poNumber) {
        throw new IllegalArgumentException("poNumber is required")
    }

    if (!decision) {
        throw new IllegalArgumentException("decision is required")
    }

    if (!(decision in ["OFFER", "REJECT"])) {
        throw new IllegalArgumentException("decision must be OFFER or REJECT")
    }

    if (offeredQuantity == null) {
        offeredQuantity = 0
    }

    if (requiredQuantity == null) {
        requiredQuantity = 0
    }

    def normalizedPayload = [
        correlationId   : correlationId,
        poNumber        : poNumber,
        sourceSystem    : json.sourceSystem ?: "system1",
        targetSystem    : json.targetSystem ?: "system2",
        decision        : decision,
        offeredQuantity : offeredQuantity as Integer,
        requiredQuantity: requiredQuantity as Integer,
        note            : json.note ?: ""
    ]

    message.setProperty("correlationId", correlationId)
    message.setProperty("poNumber", poNumber)
    message.setProperty("decision", decision)
    message.setHeader("x-cpi-secret", "b6a8225ac3059aeb15aa5e25f3f4bc051341ca1151eac317")
    message.setHeader("Content-Type", "application/json")
    message.setBody(JsonOutput.toJson(normalizedPayload))

    return message
}