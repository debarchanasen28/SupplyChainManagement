package com.supplychain.integration_hub;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Isolated outbound client for SAP CPI iFlow5 (consolidated inventory alert email).
 *
 * Self-contained on purpose: it reuses the shared CPI OAuth client-credentials config but holds its
 * own RestClient + token cache so this feature never touches CpiClient or any existing flow. Spring
 * Boot never sends email directly — it only POSTs the batch payload to iFlow5, which sends the email.
 * JSON is hand-built (same style as CpiClient) to avoid any extra dependency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CpiInventoryAlertClient {

    @Value("${cpi.iflow5-alert-email-url:}") private String iflow5Url;
    @Value("${cpi.token-url}")               private String tokenUrl;
    @Value("${cpi.client-id}")               private String clientId;
    @Value("${cpi.client-secret}")           private String clientSecret;
    @Value("${cpi.connect-timeout-ms:5000}") private int connectTimeoutMs;
    @Value("${cpi.read-timeout-ms:15000}")   private int readTimeoutMs;

    private RestClient rest;
    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        rf.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.rest = RestClient.builder().requestFactory(rf).build();
    }

    @SuppressWarnings("unchecked")
    private synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(30))) {
            return cachedToken;
        }
        String basic = Base64.getEncoder().encodeToString(
            (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        Map<String, Object> resp = rest.post()
            .uri(tokenUrl)
            .header("Authorization", "Basic " + basic)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        cachedToken = (String) resp.get("access_token");
        Object exp = resp.get("expires_in");
        long expiresIn = (exp instanceof Number) ? ((Number) exp).longValue() : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    /** POST the consolidated alert batch to iFlow5. Throws on failure; caller decides how to react. */
    public String sendAlertEmail(InventoryAlertPayload payload) {
        if (iflow5Url == null || iflow5Url.isBlank()) {
            throw new IllegalStateException(
                "CPI_IFLOW5_ALERT_EMAIL_URL is not configured — cannot send inventory alert email");
        }
        String body = toJson(payload);

        log.info("CPI iFlow5 alert email POST url={} correlationId={} items={}",
            iflow5Url, payload.getCorrelationId(),
            payload.getItems() == null ? 0 : payload.getItems().size());
        String resp = rest.post()
            .uri(iflow5Url)
            .header("Authorization", "Bearer " + getToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
        log.info("CPI iFlow5 alert email OK correlationId={}", payload.getCorrelationId());
        return resp;
    }

    private String toJson(InventoryAlertPayload p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{")
          .append("\"correlationId\":\"").append(esc(p.getCorrelationId())).append("\",")
          .append("\"eventType\":\"").append(esc(p.getEventType())).append("\",")
          .append("\"generatedAt\":\"").append(esc(p.getGeneratedAt())).append("\",");

        sb.append("\"recipients\":[");
        List<InventoryAlertPayload.Recipient> rs = p.getRecipients();
        for (int i = 0; rs != null && i < rs.size(); i++) {
            InventoryAlertPayload.Recipient r = rs.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"role\":\"").append(esc(r.getRole())).append("\",")
              .append("\"email\":\"").append(esc(r.getEmail())).append("\"}");
        }
        sb.append("],");

        InventoryAlertPayload.Summary s = p.getSummary();
        sb.append("\"summary\":{")
          .append("\"totalItems\":").append(s == null ? 0 : s.getTotalItems()).append(",")
          .append("\"belowThresholdCount\":").append(s == null ? 0 : s.getBelowThresholdCount()).append(",")
          .append("\"approachingThresholdCount\":").append(s == null ? 0 : s.getApproachingThresholdCount())
          .append("},");

        sb.append("\"items\":[");
        List<InventoryAlertItemPayload> items = p.getItems();
        for (int i = 0; items != null && i < items.size(); i++) {
            InventoryAlertItemPayload it = items.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"sku\":\"").append(esc(it.getSku())).append("\",")
              .append("\"itemName\":\"").append(esc(it.getItemName())).append("\",")
              .append("\"quantity\":").append(it.getQuantity() == null ? 0 : it.getQuantity()).append(",")
              .append("\"thresholdQuantity\":").append(it.getThresholdQuantity() == null ? 0 : it.getThresholdQuantity()).append(",")
              .append("\"unit\":\"").append(esc(it.getUnit())).append("\",")
              .append("\"alertLevel\":\"").append(esc(it.getAlertLevel())).append("\",")
              .append("\"message\":\"").append(esc(it.getMessage())).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String esc(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", " ");
    }
}
