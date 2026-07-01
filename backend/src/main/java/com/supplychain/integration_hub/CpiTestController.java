package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** TEMPORARY — verifies the outbound CPI leg in isolation. Remove after Phase 1. */
@RestController
@RequestMapping("/api/cpi/test")
@RequiredArgsConstructor
public class CpiTestController {

    private final CpiClient cpiClient;

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestParam(defaultValue = "json") String format) {
        String f = format.toLowerCase();
        String payload = switch (f) {
            case "xml" -> "<orders><order><poNumber>PO-TEST-XML</poNumber><supplierId>S2-VENDOR-01</supplierId>"
                    + "<productId>SKU-100</productId><quantity>500</quantity><deliveryDate>2026-07-05</deliveryDate></order></orders>";
            case "csv" -> "poNumber,supplierId,productId,quantity,deliveryDate\n"
                    + "PO-TEST-CSV,S2-VENDOR-01,SKU-100,500,2026-07-05";
            default -> "{\"order\":{\"poNumber\":\"PO-TEST-JSON\",\"supplierId\":\"S2-VENDOR-01\","
                    + "\"productId\":\"SKU-100\",\"quantity\":\"500\",\"deliveryDate\":\"2026-07-05\"}}";
        };
        try {
            String resp = cpiClient.sendPo("system1", "system2", "PO-TEST-" + f, "PO-TEST-" + f, f, payload);
            return ResponseEntity.ok(Map.of("sentTo", "iFlow1", "format", f, "cpiResponse", resp));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "CPI call failed",
                    "detail", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
