import { useEffect, useState } from "react";
import api from "../api/axios";
import Pagination from "../components/Pagination";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";

const ORDER_STATUS_COLORS = {
  REQUESTED:      { bg: "#f1f5f9", color: "#475569" },
  STOCK_NOTIFIED: { bg: "#fef3c7", color: "#b45309" },
  CONFIRMED:      { bg: "#eff6ff", color: "#1d4ed8" },
  PROCESSING:     { bg: "#f5f3ff", color: "#6d28d9" },
  IN_TRANSIT:     { bg: "#f0f9ff", color: "#0369a1" },
  DELIVERED:      { bg: "#f0fdf4", color: "#15803d" },
  REJECTED:       { bg: "#fef2f2", color: "#b91c1c" },
  CANCELLED:      { bg: "#fef2f2", color: "#b91c1c" },
};

const SHIP_COLORS = {
  PENDING:    { bg: "#f1f5f9", color: "#475569" },
  IN_TRANSIT: { bg: "#f5f3ff", color: "#6d28d9" },
  DELIVERED:  { bg: "#f0fdf4", color: "#15803d" },
  CANCELLED:  { bg: "#fef2f2", color: "#b91c1c" },
};

function StatCard({ label, value, sub, accent }) {
  return (
    <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", padding: "20px 22px", flex: 1, minWidth: 140 }}>
      <div style={{ fontSize: 11, color: "#94a3b8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, color: accent || "#0f172a" }}>{value ?? 0}</div>
      {sub && <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 4 }}>{sub}</div>}
    </div>
  );
}

function SectionTitle({ title, count }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
      <div style={{ fontSize: 15, fontWeight: 700, color: "#0f172a" }}>{title}</div>
      {count != null && <span style={{ fontSize: 12, background: "#f1f5f9", color: "#64748b", borderRadius: 20, padding: "2px 10px", fontWeight: 600 }}>{count}</span>}
    </div>
  );
}

function TableCard({ children }) {
  return (
    <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", overflow: "hidden" }}>
      <div style={{ overflowX: "auto" }}>{children}</div>
    </div>
  );
}

const TH = ({ children }) => (
  <th style={{ padding: "10px 16px", textAlign: "left", color: "#94a3b8", fontWeight: 600, fontSize: 11, borderBottom: "1px solid #e2e8f0", whiteSpace: "nowrap" }}>{children}</th>
);

const TD = ({ children, style = {} }) => (
  <td style={{ padding: "11px 16px", fontSize: 13, ...style }}>{children}</td>
);

export default function AdminVendorView() {
  const [orders,     setOrders]     = useState([]);
  const [orderTotal, setOrderTotal] = useState(0);
  const [delivered,  setDelivered]  = useState(0);
  const [page,       setPage]       = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [shipmentPage, setShipmentPage] = useState(0);
  const [shipmentPageSize, setShipmentPageSize] = useState(10);
  const [inventoryPage, setInventoryPage] = useState(0);
  const [inventoryPageSize, setInventoryPageSize] = useState(10);
  const [shipments,  setShipments]  = useState([]);
  const [inventory,  setInventory]  = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState("");

  // Shipments + inventory load once (small datasets).
  useEffect(() => {
    Promise.all([api.get("/shipments"), api.get("/inventory")])
      .then(([sRes, iRes]) => {
        // /shipments and /inventory return paginated payloads ({ content: [...] }); unwrap safely.
        const toList = d => (Array.isArray(d) ? d : Array.isArray(d?.content) ? d.content : []);
        setShipments(toList(sRes.data).filter(s => s.direction === "OUTBOUND"));
        setInventory(toList(iRes.data));
      })
      .catch(err => {
        console.error("Admin vendor view load failed:", err.response?.data || err.message, err);
        setError("Failed to load vendor data.");
      });
  }, []);

  // Delivered count (server aggregate) for the stat card — independent of the page.
  useEffect(() => {
    api.get("/orders/paged", { params: { direction: "OUTBOUND", tab: "delivered", size: 1 } })
      .then(res => setDelivered(res.data.totalElements || 0))
      .catch(() => {});
  }, []);

  // Outbound orders, paginated server-side. (No loading flash on page change.)
  useEffect(() => {
    api.get("/orders/paged", { params: { direction: "OUTBOUND", page, size: pageSize, sort: "dateDesc" } })
      .then(res => {
        setOrders(res.data.content || []);
        setOrderTotal(res.data.totalElements || 0);
        setTotalPages(res.data.totalPages || 1);
      })
      .catch(() => setError("Failed to load vendor data."))
      .finally(() => setLoading(false));
  }, [page, pageSize]);

  if (loading) return <div style={{ padding: 48, textAlign: "center", color: "#94a3b8", fontFamily: font }}>Loading...</div>;
  if (error)   return <div style={{ padding: 48, color: "#b91c1c", fontFamily: font }}>{error}</div>;

  const inTransit  = shipments.filter(s => s.status === "IN_TRANSIT").length;
  const lowStock   = inventory.filter(i => i.reorderLevel != null && i.quantity != null && i.quantity <= i.reorderLevel).length;
  const fulfillPct = orderTotal > 0 ? Math.round((delivered / orderTotal) * 100) : 0;
  const pagedShipments = shipments.slice(shipmentPage * shipmentPageSize, (shipmentPage + 1) * shipmentPageSize);
  const pagedInventory = inventory.slice(inventoryPage * inventoryPageSize, (inventoryPage + 1) * inventoryPageSize);

  return (
    <div style={{ padding: "32px", fontFamily: font, color: "#0f172a" }}>
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 20, fontWeight: 700 }}>Vendor Overview</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 3 }}>Outbound orders, shipments dispatched, and inventory</div>
      </div>

      <div style={{ display: "flex", gap: 14, marginBottom: 28, flexWrap: "wrap" }}>
        <StatCard label="Outbound Orders"  value={orderTotal} />
        <StatCard label="Orders Delivered" value={delivered} accent="#15803d" sub={`${fulfillPct}% fulfillment rate`} />
        <StatCard label="Active Shipments" value={inTransit} accent="#6d28d9" sub="in transit" />
        <StatCard label="Inventory Items"  value={inventory.length} sub={lowStock > 0 ? `${lowStock} low stock` : "All stocked"} accent={lowStock > 0 ? "#d97706" : "#0f172a"} />
      </div>

      <div style={{ marginBottom: 28 }}>
        <SectionTitle title="Outbound Orders" count={orderTotal} />
        <TableCard>
          <table style={{ width: "100%", minWidth: 720, borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc" }}>
                <TH>Order ID</TH><TH>Item</TH><TH>Qty</TH><TH>Unit Price</TH><TH>Total</TH><TH>Status</TH><TH>Created</TH>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 ? (
                <tr><td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>No outbound orders found.</td></tr>
              ) : orders.map((o, i) => {
                const sc = ORDER_STATUS_COLORS[o.status] || { bg: "#f1f5f9", color: "#475569" };
                const it = (o.items && o.items[0]) || {};
                const itemLabel = !o.items || o.items.length === 0
                  ? "-" : (o.items.length === 1 ? (it.description || "-") : `${o.items.length} items`);
                return (
                  <tr key={o._id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <TD style={{ color: "#1d4ed8", fontFamily: "monospace", fontSize: 12, fontWeight: 500 }}>{o.orderId || (o._id || "").slice(-8) || "-"}</TD>
                    <TD style={{ fontWeight: 500 }}>{itemLabel}</TD>
                    <TD style={{ color: "#475569" }}>{it.quantity ?? "-"}</TD>
                    <TD style={{ color: "#475569" }}>{it.unitPrice != null ? `₹${Number(it.unitPrice).toFixed(2)}` : "-"}</TD>
                    <TD style={{ fontWeight: 600 }}>{o.totalAmount != null ? `₹${Number(o.totalAmount).toFixed(2)}` : "-"}</TD>
                    <TD><span style={{ fontSize: 11, fontWeight: 600, padding: "3px 9px", borderRadius: 20, background: sc.bg, color: sc.color }}>{o.status}</span></TD>
                    <TD style={{ color: "#64748b", whiteSpace: "nowrap" }}>
                      {o.createdAt ? new Date(o.createdAt).toLocaleDateString("en-IN", { month: "short", day: "numeric", year: "numeric" }) : "-"}
                    </TD>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </TableCard>
        <Pagination page={page} pageSize={pageSize} totalRecords={orderTotal} onPageChange={next => setPage(Math.max(0, Math.min(totalPages - 1, next)))} onPageSizeChange={size => { setPageSize(size); setPage(0); }} />
      </div>

      <div style={{ marginBottom: 28 }}>
        <SectionTitle title="Outbound Shipments" count={shipments.length} />
        <TableCard>
          <table style={{ width: "100%", minWidth: 700, borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc" }}>
                <TH>Shipment ID</TH><TH>Receiver</TH><TH>Carrier</TH><TH>Tracking</TH><TH>Route</TH><TH>Est. Delivery</TH><TH>Status</TH>
              </tr>
            </thead>
            <tbody>
              {shipments.length === 0 ? (
                <tr><td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>No outbound shipments found.</td></tr>
              ) : pagedShipments.map((s, i) => {
                const sc = SHIP_COLORS[s.status] || { bg: "#f1f5f9", color: "#475569" };
                return (
                  <tr key={s.id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <TD style={{ color: "#1d4ed8", fontFamily: "monospace", fontSize: 12, fontWeight: 500 }}>{s.shipmentId || (s.id || "").slice(-8) || "-"}</TD>
                    <TD>{s.counterpartyName || "-"}</TD>
                    <TD style={{ color: "#475569" }}>{s.carrier || "-"}</TD>
                    <TD style={{ color: "#475569", fontFamily: "monospace", fontSize: 11 }}>{s.trackingNumber || "-"}</TD>
                    <TD style={{ color: "#475569", whiteSpace: "nowrap" }}>{s.origin && s.destination ? `${s.origin} → ${s.destination}` : "-"}</TD>
                    <TD style={{ color: "#64748b", whiteSpace: "nowrap" }}>
                      {s.estimatedDelivery ? new Date(s.estimatedDelivery).toLocaleDateString("en-IN", { month: "short", day: "numeric", year: "numeric" }) : "-"}
                    </TD>
                    <TD><span style={{ fontSize: 11, fontWeight: 600, padding: "3px 9px", borderRadius: 20, background: sc.bg, color: sc.color }}>{s.status}</span></TD>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </TableCard>
        <Pagination page={shipmentPage} pageSize={shipmentPageSize} totalRecords={shipments.length} onPageChange={setShipmentPage} onPageSizeChange={size => { setShipmentPageSize(size); setShipmentPage(0); }} />
      </div>

      <div style={{ marginBottom: 28 }}>
        <SectionTitle title="Inventory" count={inventory.length} />
        <TableCard>
          <table style={{ width: "100%", minWidth: 740, borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc" }}>
                <TH>Item Name</TH><TH>SKU</TH><TH>Category</TH><TH>Qty</TH><TH>UoM</TH><TH>Reorder At</TH><TH>Unit Price</TH><TH>Location</TH>
              </tr>
            </thead>
            <tbody>
              {inventory.length === 0 ? (
                <tr><td colSpan={8} style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>No inventory found.</td></tr>
              ) : pagedInventory.map((item, i) => {
                const isLow = item.reorderLevel != null && item.quantity != null && item.quantity <= item.reorderLevel;
                return (
                  <tr key={item.id || i} style={{ borderBottom: "1px solid #f1f5f9", background: isLow ? "#fffbeb" : "transparent" }}>
                    <TD style={{ fontWeight: 500 }}>
                      {item.itemName}
                      {isLow && <span style={{ marginLeft: 6, fontSize: 10, background: "#fef9c3", color: "#854d0e", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>Low</span>}
                    </TD>
                    <TD style={{ color: "#64748b", fontFamily: "monospace", fontSize: 11 }}>{item.sku || "-"}</TD>
                    <TD style={{ color: "#475569" }}>{item.category || "-"}</TD>
                    <TD style={{ fontWeight: 600, color: isLow ? "#d97706" : "#0f172a" }}>{item.quantity ?? "-"}</TD>
                    <TD style={{ color: "#475569" }}>{item.unitOfMeasure || "-"}</TD>
                    <TD style={{ color: "#475569" }}>{item.reorderLevel ?? "-"}</TD>
                    <TD style={{ color: "#0f172a" }}>{item.unitPrice != null ? `₹${item.unitPrice.toFixed(2)}` : "-"}</TD>
                    <TD style={{ color: "#475569" }}>{item.warehouseLocation || "-"}</TD>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </TableCard>
        <Pagination page={inventoryPage} pageSize={inventoryPageSize} totalRecords={inventory.length} onPageChange={setInventoryPage} onPageSizeChange={size => { setInventoryPageSize(size); setInventoryPage(0); }} />
      </div>
    </div>
  );
}
