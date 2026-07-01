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

const SUPPLIER_STATUS_COLORS = {
  ACTIVE:   { bg: "#dcfce7", color: "#15803d" },
  INACTIVE: { bg: "#f1f5f9", color: "#64748b" },
  PENDING:  { bg: "#fef3c7", color: "#b45309" },
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

export default function AdminProcurementView() {
  const [orders,     setOrders]     = useState([]);
  const [orderTotal, setOrderTotal] = useState(0);
  const [received,   setReceived]   = useState(0);
  const [page,       setPage]       = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [shipmentPage, setShipmentPage] = useState(0);
  const [shipmentPageSize, setShipmentPageSize] = useState(10);
  const [supplierPage, setSupplierPage] = useState(0);
  const [supplierPageSize, setSupplierPageSize] = useState(10);
  const [stockPage, setStockPage] = useState(0);
  const [stockPageSize, setStockPageSize] = useState(10);
  const [shipments,  setShipments]  = useState([]);
  const [suppliers,  setSuppliers]  = useState([]);
  const [inventory,  setInventory]  = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState("");

  // Shipments, suppliers, inventory load once (small datasets).
  useEffect(() => {
    Promise.all([api.get("/shipments"), api.get("/suppliers"), api.get("/inventory")])
      .then(([sRes, supRes, iRes]) => {
        // These endpoints return paginated payloads ({ content: [...] }); unwrap safely.
        const toList = d => (Array.isArray(d) ? d : Array.isArray(d?.content) ? d.content : []);
        setShipments(toList(sRes.data).filter(s => s.direction === "INBOUND"));
        setSuppliers(toList(supRes.data));
        setInventory(toList(iRes.data));
      })
      .catch(err => {
        console.error("Admin procurement view load failed:", err.response?.data || err.message, err);
        setError("Failed to load procurement data.");
      });
  }, []);

  // Received count (server aggregate) for the stat card.
  useEffect(() => {
    api.get("/orders/paged", { params: { direction: "INBOUND", tab: "delivered", size: 1 } })
      .then(res => setReceived(res.data.totalElements || 0))
      .catch(() => {});
  }, []);

  // Inbound purchase orders, paginated server-side. (No loading flash on page change.)
  useEffect(() => {
    api.get("/orders/paged", { params: { direction: "INBOUND", page, size: pageSize, sort: "dateDesc" } })
      .then(res => {
        setOrders(res.data.content || []);
        setOrderTotal(res.data.totalElements || 0);
        setTotalPages(res.data.totalPages || 1);
      })
      .catch(() => setError("Failed to load procurement data."))
      .finally(() => setLoading(false));
  }, [page, pageSize]);

  if (loading) return <div style={{ padding: 48, textAlign: "center", color: "#94a3b8", fontFamily: font }}>Loading...</div>;
  if (error)   return <div style={{ padding: 48, color: "#b91c1c", fontFamily: font }}>{error}</div>;

  const inTransit       = shipments.filter(s => s.status === "IN_TRANSIT").length;
  const activeSuppliers = suppliers.filter(s => s.integrationStatus === "ACTIVE").length;
  const lowStock        = inventory.filter(i => i.reorderLevel != null && i.quantity != null && i.quantity <= i.reorderLevel).length;
  const fulfillPct      = orderTotal > 0 ? Math.round((received / orderTotal) * 100) : 0;
  const pagedShipments = shipments.slice(shipmentPage * shipmentPageSize, (shipmentPage + 1) * shipmentPageSize);
  const pagedSuppliers = suppliers.slice(supplierPage * supplierPageSize, (supplierPage + 1) * supplierPageSize);
  const lowStockItems = inventory.filter(i => i.reorderLevel != null && i.quantity != null && i.quantity <= i.reorderLevel);
  const pagedLowStock = lowStockItems.slice(stockPage * stockPageSize, (stockPage + 1) * stockPageSize);

  return (
    <div style={{ padding: "32px", fontFamily: font, color: "#0f172a" }}>
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 20, fontWeight: 700 }}>Procurement Overview</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 3 }}>Inbound orders, incoming shipments, suppliers, and stock levels</div>
      </div>

      <div style={{ display: "flex", gap: 14, marginBottom: 28, flexWrap: "wrap" }}>
        <StatCard label="Purchase Orders"    value={orderTotal} />
        <StatCard label="Orders Received"    value={received} accent="#15803d" sub={`${fulfillPct}% received`} />
        <StatCard label="Incoming Shipments" value={inTransit} accent="#6d28d9" sub="in transit" />
        <StatCard label="Active Suppliers"   value={activeSuppliers} accent="#0f766e" sub={`of ${suppliers.length} registered`} />
        <StatCard label="Low Stock Items"    value={lowStock} accent={lowStock > 0 ? "#d97706" : "#15803d"} sub={lowStock > 0 ? "Need restocking" : "All stocked"} />
      </div>

      <div style={{ marginBottom: 28 }}>
        <SectionTitle title="Purchase Orders (Inbound)" count={orderTotal} />
        <TableCard>
          <table style={{ width: "100%", minWidth: 720, borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc" }}>
                <TH>Order ID</TH><TH>Item</TH><TH>Qty</TH><TH>Unit Price</TH><TH>Total</TH><TH>Status</TH><TH>Created</TH>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 ? (
                <tr><td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>No inbound orders found.</td></tr>
              ) : orders.map((o, i) => {
                const sc = ORDER_STATUS_COLORS[o.status] || { bg: "#f1f5f9", color: "#475569" };
                const it = (o.items && o.items[0]) || {};
                const itemLabel = !o.items || o.items.length === 0
                  ? "-" : (o.items.length === 1 ? (it.description || "-") : `${o.items.length} items`);
                return (
                  <tr key={o._id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <TD style={{ color: "#6d28d9", fontFamily: "monospace", fontSize: 12, fontWeight: 500 }}>{o.orderId || (o._id || "").slice(-8) || "-"}</TD>
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
        <SectionTitle title="Inbound Shipments" count={shipments.length} />
        <TableCard>
          <table style={{ width: "100%", minWidth: 700, borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc" }}>
                <TH>Shipment ID</TH><TH>Supplier</TH><TH>Carrier</TH><TH>Tracking</TH><TH>Route</TH><TH>Est. Delivery</TH><TH>Status</TH>
              </tr>
            </thead>
            <tbody>
              {shipments.length === 0 ? (
                <tr><td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>No inbound shipments found.</td></tr>
              ) : pagedShipments.map((s, i) => {
                const sc = SHIP_COLORS[s.status] || { bg: "#f1f5f9", color: "#475569" };
                return (
                  <tr key={s.id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <TD style={{ color: "#6d28d9", fontFamily: "monospace", fontSize: 12, fontWeight: 500 }}>{s.shipmentId || (s.id || "").slice(-8) || "-"}</TD>
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
        <SectionTitle title="Suppliers" count={suppliers.length} />
        <TableCard>
          <table style={{ width: "100%", minWidth: 720, borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc" }}>
                <TH>Company</TH><TH>Contact</TH><TH>Category</TH><TH>Email / Phone</TH><TH>Status</TH><TH>Rating</TH>
              </tr>
            </thead>
            <tbody>
              {suppliers.length === 0 ? (
                <tr><td colSpan={6} style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>No suppliers registered.</td></tr>
              ) : pagedSuppliers.map((s, i) => {
                const sc = SUPPLIER_STATUS_COLORS[s.integrationStatus] || { bg: "#f1f5f9", color: "#64748b" };
                return (
                  <tr key={s._id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <TD style={{ fontWeight: 600 }}>{s.companyName}</TD>
                    <TD style={{ color: "#334155" }}>{s.contactPersonName || "-"}</TD>
                    <TD style={{ color: "#64748b" }}>{s.businessCategory || "-"}</TD>
                    <TD style={{ color: "#64748b" }}>
                      <div>{s.email || "-"}</div>
                      {s.phone && <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 1 }}>{s.phone}</div>}
                    </TD>
                    <TD><span style={{ fontSize: 11, fontWeight: 600, padding: "3px 9px", borderRadius: 6, background: sc.bg, color: sc.color }}>{s.integrationStatus || "-"}</span></TD>
                    <TD style={{ color: "#64748b" }}>{s.rating != null ? `${Number(s.rating).toFixed(1)} / 5` : "-"}</TD>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </TableCard>
        <Pagination page={supplierPage} pageSize={supplierPageSize} totalRecords={suppliers.length} onPageChange={setSupplierPage} onPageSizeChange={size => { setSupplierPageSize(size); setSupplierPage(0); }} />
      </div>

      {lowStock > 0 && (
        <div style={{ marginBottom: 28 }}>
          <SectionTitle title="Low Stock Watchlist" count={lowStock} />
          <TableCard>
            <table style={{ width: "100%", minWidth: 600, borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ background: "#f8fafc" }}>
                  <TH>Item Name</TH><TH>SKU</TH><TH>Current Qty</TH><TH>Reorder At</TH><TH>Supplier</TH><TH>Location</TH>
                </tr>
              </thead>
              <tbody>
                {pagedLowStock.map((item, i) => (
                    <tr key={item.id || i} style={{ borderBottom: "1px solid #f1f5f9", background: "#fffbeb" }}>
                      <TD style={{ fontWeight: 500 }}>
                        {item.itemName}
                        <span style={{ marginLeft: 6, fontSize: 10, background: "#fef9c3", color: "#854d0e", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>Low</span>
                      </TD>
                      <TD style={{ color: "#64748b", fontFamily: "monospace", fontSize: 11 }}>{item.sku || "-"}</TD>
                      <TD style={{ fontWeight: 700, color: "#d97706" }}>{item.quantity}</TD>
                      <TD style={{ color: "#475569" }}>{item.reorderLevel}</TD>
                      <TD style={{ color: "#475569" }}>{item.supplierName || "-"}</TD>
                      <TD style={{ color: "#475569" }}>{item.warehouseLocation || "-"}</TD>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </TableCard>
          <Pagination page={stockPage} pageSize={stockPageSize} totalRecords={lowStockItems.length} onPageChange={setStockPage} onPageSizeChange={size => { setStockPageSize(size); setStockPage(0); }} />
        </div>
      )}
    </div>
  );
}
