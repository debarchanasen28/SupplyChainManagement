import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService
def Message processData(Message message) {
    message.getHeaders().keySet().toArray().each { k ->
        if (k.toString().startsWith("CamelHttp")) message.getHeaders().remove(k)
    }
    def svc = ITApiFactory.getService(SecureStoreService.class, null)
    def cred = svc.getUserCredential("backend_inbound_secret")
    message.setHeader("X-CPI-Secret", new String(cred.getPassword()))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("ngrok-skip-browser-warning", "true")
    return message
}