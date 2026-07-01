import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def raw   = message.getBody(String) ?: "{}"
    def json  = new JsonSlurper().parseText(raw)
    def items = json.items ?: []
    def total = (json.summary?.totalItems ?: items.size()) as int
    def below = (json.summary?.belowThresholdCount ?: 0) as int
    def appr  = (json.summary?.approachingThresholdCount ?: 0) as int
    def when  = json.generatedAt ?: new Date().format("yyyy-MM-dd HH:mm:ss")
    // Severity for subject/colour: CRITICAL if anything is below threshold, else LOW
    def sev   = items.any { (it.alertLevel ?: '') == 'BELOW_THRESHOLD' } ? 'CRITICAL' : 'LOW'

    message.setProperty("itemCount", total.toString())

    def rows = new StringBuilder()
    items.each { it ->
        def lvl = (it.alertLevel ?: '').toString()
        def lvlColor = (lvl == 'BELOW_THRESHOLD') ? '#c0392b' : '#e67e22'
        rows << "<tr>" +
                "<td>${it.sku ?: ''}</td>" +
                "<td>${it.itemName ?: ''}</td>" +
                "<td style='text-align:right'>${it.quantity ?: 0}</td>" +
                "<td style='text-align:right'>${it.thresholdQuantity ?: 0}</td>" +
                "<td>${it.unit ?: 'pcs'}</td>" +
                "<td style='color:${lvlColor};font-weight:bold'>${lvl}</td>" +
                "</tr>"
    }

    def color = (sev == 'CRITICAL') ? '#c0392b' : '#e67e22'
    def html = """<html><body style="font-family:Arial,Helvetica,sans-serif;color:#222">
  <h2 style="color:${color};margin:0 0 8px">${sev} STOCK ALERT</h2>
  <p>The following System1 inventory items require attention:</p>
  <table cellpadding="6" cellspacing="0" style="border-collapse:collapse;border:1px solid #ccc">
    <thead><tr style="background:#f2f2f2">
      <th align="left">SKU</th><th align="left">Item</th>
      <th align="right">Current</th><th align="right">Threshold</th>
      <th align="left">Unit</th><th align="left">Level</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>
  <p style="margin-top:12px">
    <b>Total affected items:</b> ${total}
    &nbsp;|&nbsp; Below threshold: ${below}
    &nbsp;|&nbsp; Approaching: ${appr}
  </p>
  <p><b>Generated at:</b> ${when}</p>
  <hr><p style="color:#888;font-size:12px">Automated alert from CPI iFlow5 · System1 Inventory</p>
</body></html>"""

    message.setProperty("emailSubject", "[${sev}] System1 Inventory Alert — ${total} item(s)")
    message.setBody(html)
    message.setHeader("Content-Type", "text/html; charset=UTF-8")
    return message
}