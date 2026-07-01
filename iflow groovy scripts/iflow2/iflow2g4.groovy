import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
def Message processData(Message message) {
    def ex = message.getProperty("CamelExceptionCaught")
    def err = [ errorType    : "ApprovalCallbackError",
                errorMessage : (ex ? ex.getMessage() : "Unknown error"),
                iFlowName     : "iFlow2_Approval_Callback",
                correlationId : (message.getProperty("correlationId") ?: ""),
                timestamp     : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC")) ]
    message.setBody(JsonOutput.toJson(err))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("CamelHttpResponseCode", 400)
    return message
}