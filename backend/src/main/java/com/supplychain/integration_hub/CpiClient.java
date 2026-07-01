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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Outbound bridge to SAP CPI iFlow 1, secured with OAuth2 client credentials.
 * Fetches + caches a Bearer token, then POSTs "#meta\n<payload>" to the iFlow.
 * Hardened with connect/read timeouts and retry-with-backoff on transient failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CpiClient {

    private final CpiAuditService audit;
    private RestClient rest;

    @Value("${cpi.base-url}")           private String baseUrl;
    @Value("${cpi.po-outbound-path}")   private String poOutboundPath;
    @Value("${cpi.approval-callback-path:/http/approval-callback}")
    private String approvalCallbackPath;
    @Value("${cpi.stock-offer-path:/http/http/stock-offer}")
    private String stockOfferPath;
    @Value("${cpi.iflow3-stock-offer-url:}")
    private String iflow3StockOfferUrl;
    @Value("${cpi.iflow4-inventory-update-url:}")
    private String iflow4InventoryUpdateUrl;
    @Value("${cpi.token-url}")          private String tokenUrl;
    @Value("${cpi.client-id}")          private String clientId;
    @Value("${cpi.client-secret}")      private String clientSecret;
    @Value("${cpi.connect-timeout-ms}") private int connectTimeoutMs;
    @Value("${cpi.read-timeout-ms}")    private int readTimeoutMs;
    @Value("${cpi.max-retries}")        private int maxRetries;
    @Value("${cpi.retry-backoff-ms}")   private long retryBackoffMs;

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
        log.info("CPI OAuth token refreshed, expires in {}s", expiresIn);
        return cachedToken;
    }

    public String sendPo(String source, String target, String correlationId,
                         String idempotencyKey, String format, String payload) {

        // Proven-good control line: the tenant's ParseControlParams reads source/target here.
        // CPI's EnrichPO derives correlationId itself as "<source>-<poNumber>".
        String meta = String.format("#meta source=%s target=%s format=%s", source, target, format);
        String body = meta + "\n" + payload;

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                String resp = rest.post()
                    .uri(baseUrl + poOutboundPath)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.TEXT_PLAIN)   // body read as raw text; #meta stripped in Groovy
                    .body(body)
                    .retrieve()
                    .body(String.class);
                log.info("CPI sendPo OK corrId={} attempt={}", correlationId, attempt);
                return resp;
            } catch (HttpServerErrorException | ResourceAccessException transientEx) {
                // 5xx or IO/timeout — retry with linear backoff
                if (attempt >= maxRetries) {
                    auditFailure("PO", idempotencyKey, correlationId, source, target,
                            attempt, transientEx);
                    log.error("CPI sendPo FAILED corrId={} after {} attempts: {}",
                        correlationId, attempt, transientEx.getMessage());
                    throw new RuntimeException("CPI send failed after " + attempt
                        + " attempts: " + transientEx.getMessage(), transientEx);
                }
                long wait = retryBackoffMs * attempt;
                auditRetry("PO", idempotencyKey, correlationId, source, target,
                        attempt, transientEx);
                log.warn("CPI sendPo transient failure corrId={} attempt={} — retrying in {}ms: {}",
                    correlationId, attempt, wait, transientEx.getMessage());
                sleep(wait);
            } catch (RuntimeException fatal) {
                // 4xx (auth/mapping) — not retryable
                auditFailure("PO", idempotencyKey, correlationId, source, target,
                        attempt, fatal);
                log.error("CPI sendPo FAILED corrId={} (non-retryable): {}", correlationId, fatal.getMessage());
                throw new RuntimeException("CPI send failed: " + fatal.getMessage(), fatal);
            }
        }
    }

    public String sendApproval(String source, String target, String correlationId,
                               String poNumber, String decision, String decidedBy, String reason) {
        Map<String, Object> payload = Map.of(
            "sourceSystem", source,
            "targetSystem", target,
            "correlationId", correlationId,
            "poNumber", poNumber,
            "decision", decision,
            "decidedBy", decidedBy,
            "reason", reason == null ? "" : reason
        );
        String body = "#meta source=" + source + " target=" + target + "\n"
            + "{\"correlationId\":\"" + json(correlationId) + "\","
            + "\"poNumber\":\"" + json(poNumber) + "\","
            + "\"decision\":\"" + json(decision) + "\","
            + "\"sourceSystem\":\"" + json(source) + "\","
            + "\"targetSystem\":\"" + json(target) + "\","
            + "\"decidedBy\":\"" + json(decidedBy) + "\","
            + "\"reason\":\"" + json(reason == null ? "" : reason) + "\"}";

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.info("CPI iFlow2 approval POST url={} payload={}",
                    baseUrl + approvalCallbackPath, payload);
                String resp = rest.post()
                    .uri(baseUrl + approvalCallbackPath)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .retrieve()
                    .body(String.class);
                log.info("CPI sendApproval OK corrId={} decision={} attempt={}",
                    correlationId, decision, attempt);
                return resp;
            } catch (HttpServerErrorException | ResourceAccessException transientEx) {
                if (attempt >= maxRetries) {
                    auditFailure("APPROVAL", poNumber, correlationId, source, target,
                            attempt, transientEx);
                    log.error("CPI sendApproval FAILED corrId={} after {} attempts: {}",
                        correlationId, attempt, transientEx.getMessage());
                    throw new RuntimeException("CPI approval failed after " + attempt
                        + " attempts: " + transientEx.getMessage(), transientEx);
                }
                long wait = retryBackoffMs * attempt;
                auditRetry("APPROVAL", poNumber, correlationId, source, target,
                        attempt, transientEx);
                log.warn("CPI sendApproval transient failure corrId={} attempt={} — retrying in {}ms: {}",
                    correlationId, attempt, wait, transientEx.getMessage());
                sleep(wait);
            } catch (RuntimeException fatal) {
                auditFailure("APPROVAL", poNumber, correlationId, source, target,
                        attempt, fatal);
                log.error("CPI sendApproval FAILED corrId={} (non-retryable): {}",
                    correlationId, fatal.getMessage());
                throw new RuntimeException("CPI approval failed: " + fatal.getMessage(), fatal);
            }
        }
    }

    /**
     * Sends a vendor stock decision to the counterparty via CPI iFlow 3 (stock-notification).
     * decision = OFFER (vendor can supply offeredQuantity) | REJECT (vendor cannot supply).
     * correlationId is passed through untouched so the receiver matches the order.
     */
    public String sendStockOffer(String source, String target, String correlationId,
                                 String poNumber, String decision,
                                 int offeredQuantity, int requiredQuantity, String note) {
        String body = "#meta source=" + source + " target=" + target + " format=json\n"
            + "{\"correlationId\":\"" + json(correlationId) + "\","
            + "\"poNumber\":\"" + json(poNumber) + "\","
            + "\"sourceSystem\":\"" + json(source) + "\","
            + "\"targetSystem\":\"" + json(target) + "\","
            + "\"decision\":\"" + json(decision) + "\","
            + "\"offeredQuantity\":" + offeredQuantity + ","
            + "\"requiredQuantity\":" + requiredQuantity + ","
            + "\"note\":\"" + json(note == null ? "" : note) + "\"}";
        log.info("CPI iFlow3 stock-offer payload={}", body);

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.info("CPI iFlow3 stock-offer POST url={} corrId={} decision={}",
                    baseUrl + stockOfferPath, correlationId, decision);
                String resp = rest.post()
                    .uri(baseUrl + stockOfferPath)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .retrieve()
                    .body(String.class);
                log.info("CPI sendStockOffer OK corrId={} decision={} attempt={}",
                    correlationId, decision, attempt);
                return resp;
            } catch (HttpServerErrorException | ResourceAccessException transientEx) {
                if (attempt >= maxRetries) {
                    auditFailure("STOCK_OFFER", poNumber, correlationId, source, target,
                            attempt, transientEx);
                    log.error("CPI sendStockOffer FAILED corrId={} after {} attempts: {}",
                        correlationId, attempt, transientEx.getMessage());
                    throw new RuntimeException("CPI stock-offer failed after " + attempt
                        + " attempts: " + transientEx.getMessage(), transientEx);
                }
                long wait = retryBackoffMs * attempt;
                auditRetry("STOCK_OFFER", poNumber, correlationId, source, target,
                        attempt, transientEx);
                log.warn("CPI sendStockOffer transient failure corrId={} attempt={} — retrying in {}ms: {}",
                    correlationId, attempt, wait, transientEx.getMessage());
                sleep(wait);
            } catch (RuntimeException fatal) {
                auditFailure("STOCK_OFFER", poNumber, correlationId, source, target,
                        attempt, fatal);
                log.error("CPI sendStockOffer FAILED corrId={} (non-retryable): {}",
                    correlationId, fatal.getMessage());
                throw new RuntimeException("CPI stock-offer failed: " + fatal.getMessage(), fatal);
            }
        }
    }

    /**
     * Sends a System1 Vendor stock offer/reject to SAP CPI iFlow 3 using the dedicated
     * iFlow 3 endpoint (CPI_IFLOW3_STOCK_OFFER_URL). CPI iFlow3 forwards the payload to the
     * backend POST /api/cpi/inbound/stock-offer, which updates the System2 procurement order.
     * Body is the exact stock-offer JSON contract; correlationId is passed through untouched.
     */
    public String sendStockOfferViaIflow3(String source, String target, String correlationId,
                                          String poNumber, String decision,
                                          int offeredQuantity, int requiredQuantity, String note) {
        if (iflow3StockOfferUrl == null || iflow3StockOfferUrl.isBlank()) {
            throw new IllegalStateException(
                "CPI_IFLOW3_STOCK_OFFER_URL is not configured — cannot send vendor stock offer via iFlow3");
        }
        String body = "{\"correlationId\":\"" + json(correlationId) + "\","
            + "\"poNumber\":\"" + json(poNumber) + "\","
            + "\"sourceSystem\":\"" + json(source) + "\","
            + "\"targetSystem\":\"" + json(target) + "\","
            + "\"decision\":\"" + json(decision) + "\","
            + "\"offeredQuantity\":" + offeredQuantity + ","
            + "\"requiredQuantity\":" + requiredQuantity + ","
            + "\"note\":\"" + json(note == null ? "" : note) + "\"}";

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.info("CPI iFlow3 stock-offer POST url={} corrId={} poNumber={} decision={} offered={} required={} attempt={}",
                    iflow3StockOfferUrl, correlationId, poNumber, decision,
                    offeredQuantity, requiredQuantity, attempt);
                String resp = rest.post()
                    .uri(iflow3StockOfferUrl)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
                log.info("CPI iFlow3 stock-offer OK corrId={} decision={} attempt={}",
                    correlationId, decision, attempt);
                audit.recordCpiEvent("CPI_SUCCESS", poNumber, correlationId, source, target,
                        "SUCCESS", "iFlow3 stock-offer delivered", attempt, null);
                return resp;
            } catch (HttpServerErrorException | ResourceAccessException transientEx) {
                if (attempt >= maxRetries) {
                    auditFailure("STOCK_OFFER", poNumber, correlationId, source, target,
                            attempt, transientEx);
                    log.error("CPI iFlow3 stock-offer FAILED corrId={} after {} attempts: {}",
                        correlationId, attempt, transientEx.getMessage());
                    throw new RuntimeException("CPI iFlow3 stock-offer failed after " + attempt
                        + " attempts: " + transientEx.getMessage(), transientEx);
                }
                long wait = retryBackoffMs * attempt;
                auditRetry("STOCK_OFFER", poNumber, correlationId, source, target,
                        attempt, transientEx);
                log.warn("CPI iFlow3 stock-offer transient failure corrId={} attempt={} — retrying in {}ms: {}",
                    correlationId, attempt, wait, transientEx.getMessage());
                sleep(wait);
            } catch (RuntimeException fatal) {
                auditFailure("STOCK_OFFER", poNumber, correlationId, source, target,
                        attempt, fatal);
                log.error("CPI iFlow3 stock-offer FAILED corrId={} (non-retryable): {}",
                    correlationId, fatal.getMessage());
                throw new RuntimeException("CPI iFlow3 stock-offer failed: " + fatal.getMessage(), fatal);
            }
        }
    }

    /**
     * Sends a System1 inventory movement to SAP CPI iFlow 4. CPI iFlow4 forwards it to the backend
     * POST /api/cpi/inbound/inventory-update, which applies the change:
     *   eventType VENDOR_SUPPLY       -> DECREASE (System1 vendor supplied goods)
     *   eventType PROCUREMENT_RECEIVE -> INCREASE (System1 procurement received goods)
     * Same CpiClient style as iFlow1/iFlow2/iFlow3 (OAuth Bearer + retry/backoff).
     */
    public String sendInventoryUpdate(String correlationId, String orderId,
                                      String sourceSystem, String targetSystem, String eventType,
                                      String sku, String itemName, int quantity,
                                      String unit, String reason) {
        if (iflow4InventoryUpdateUrl == null || iflow4InventoryUpdateUrl.isBlank()) {
            throw new IllegalStateException(
                "CPI_IFLOW4_INVENTORY_UPDATE_URL is not configured — cannot send inventory update via iFlow4");
        }
        // NOTE: iFlow4's XSD (xs:all) accepts only these 8 fields. sourceSystem/targetSystem are
        // intentionally NOT serialized — including them fails iFlow4 XSD validation (xs:all group).
        String body = "{\"correlationId\":\"" + json(correlationId) + "\","
            + "\"orderId\":\"" + json(orderId) + "\","
            + "\"eventType\":\"" + json(eventType) + "\","
            + "\"sku\":\"" + json(sku == null ? "" : sku) + "\","
            + "\"itemName\":\"" + json(itemName) + "\","
            + "\"quantity\":" + quantity + ","
            + "\"unit\":\"" + json(unit == null ? "pcs" : unit) + "\","
            + "\"reason\":\"" + json(reason == null ? "" : reason) + "\"}";

        // [TEMP DEBUG iFlow4-trace] BEFORE IFLOW4 CALL
        log.info("[iFlow4-trace] BEFORE-CALL orderId={} sku={} qty={} eventType={} targetUrl={} payload={}",
            orderId, sku, quantity, eventType, iflow4InventoryUpdateUrl, body);

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.info("CPI iFlow4 inventory-update POST url={} corrId={} orderId={} eventType={} item={} qty={} attempt={}",
                    iflow4InventoryUpdateUrl, correlationId, orderId, eventType, itemName, quantity, attempt);
                String resp = rest.post()
                    .uri(iflow4InventoryUpdateUrl)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
                // [TEMP DEBUG iFlow4-trace] AFTER IFLOW4 CALL
                log.info("[iFlow4-trace] AFTER-CALL orderId={} attempt={} responseBody={}",
                    orderId, attempt, resp);
                // iFlow4's Exception Subprocess returns HTTP 200 with an ERROR body when its
                // receiver cannot reach the backend. Treat that as a real failure, not a success,
                // so the inventory sync is retried instead of being silently marked applied.
                if (resp != null && resp.contains("\"status\"") && resp.contains("ERROR")) {
                    throw new RuntimeException("iFlow4 returned ERROR response: " + resp);
                }
                log.info("CPI iFlow4 inventory-update OK corrId={} orderId={} item={} attempt={}",
                    correlationId, orderId, itemName, attempt);
                audit.recordCpiEvent("CPI_SUCCESS", orderId, correlationId, "system1", "system1",
                        "SUCCESS", "iFlow4 inventory-update delivered", attempt, null);
                return resp;
            } catch (HttpServerErrorException | ResourceAccessException transientEx) {
                // [TEMP DEBUG iFlow4-trace] full exception (class + message) on transient failure
                log.warn("[iFlow4-trace] CALL-EXCEPTION orderId={} attempt={} type={} message={}",
                    orderId, attempt, transientEx.getClass().getName(), transientEx.getMessage());
                if (attempt >= maxRetries) {
                    auditFailure("INVENTORY_UPDATE", orderId, correlationId, "system1", "system1",
                            attempt, transientEx);
                    log.error("CPI iFlow4 inventory-update FAILED corrId={} after {} attempts: {}",
                        correlationId, attempt, transientEx.getMessage());
                    throw new RuntimeException("CPI iFlow4 inventory-update failed after " + attempt
                        + " attempts: " + transientEx.getMessage(), transientEx);
                }
                long wait = retryBackoffMs * attempt;
                auditRetry("INVENTORY_UPDATE", orderId, correlationId, "system1", "system1",
                        attempt, transientEx);
                log.warn("CPI iFlow4 inventory-update transient failure corrId={} attempt={} — retrying in {}ms: {}",
                    correlationId, attempt, wait, transientEx.getMessage());
                sleep(wait);
            } catch (RuntimeException fatal) {
                // [TEMP DEBUG iFlow4-trace] full exception (class + message) on non-retryable failure
                log.warn("[iFlow4-trace] CALL-EXCEPTION-FATAL orderId={} attempt={} type={} message={}",
                    orderId, attempt, fatal.getClass().getName(), fatal.getMessage());
                auditFailure("INVENTORY_UPDATE", orderId, correlationId, "system1", "system1",
                        attempt, fatal);
                log.error("CPI iFlow4 inventory-update FAILED corrId={} (non-retryable): {}",
                    correlationId, fatal.getMessage());
                throw new RuntimeException("CPI iFlow4 inventory-update failed: " + fatal.getMessage(), fatal);
            }
        }
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void auditRetry(String operation, String orderId, String correlationId,
                            String source, String target, int attempt, Exception error) {
        audit.recordCpiEvent("CPI_RETRY", orderId, correlationId, source, target,
                "PENDING", "Retrying CPI " + operation + " request (attempt " + attempt + ")",
                attempt, error.getMessage());
    }

    private void auditFailure(String operation, String orderId, String correlationId,
                              String source, String target, int attempt, Exception error) {
        audit.recordCpiEvent("CPI_FAILURE", orderId, correlationId, source, target,
                "FAILED", "CPI " + operation + " request failed", attempt, error.getMessage());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
