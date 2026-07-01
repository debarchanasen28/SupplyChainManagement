import { useEffect, useState, useCallback } from "react";
import api from "../api/axios";
import Pagination from "../components/Pagination";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";
const ACCENT = "#0f766e";

const STATUS_COLORS = {
  REQUESTED:      "#94a3b8",
  STOCK_NOTIFIED: "#f59e0b",
  APPROVED:       "#3b82f6",
  BUYER_APPROVED: "#3b82f6",
  CONFIRMED:      "#3b82f6",
  ACTIVE:         "#0ea5e9",
  PROCESSING:     "#8b5cf6",
  IN_TRANSIT:     "#0ea5e9",
  DELIVERED:      "#22c55e",
  REJECTED:       "#ef4444",
  BUYER_REJECTED: "#ef4444",
  VENDOR_REJECTED:"#ef4444",
  CANCELLED:      "#f87171",
};

const inr = (n) => `₹${(n ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function StatCard({ label, value, sub, accent }) {
  return (
    <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", padding: "18px 20px", flex: 1, minWidth: 150 }}>
      <div style={{ fontSize: 11, color: "#94a3b8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, color: accent || "#0f172a" }}>{value ?? 0}</div>
      {sub && <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 4 }}>{sub}</div>}
    </div>
  );
}

function StatusBadge({ status }) {
  const c = STATUS_COLORS[status] || "#94a3b8";
  return (
    <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 9px", borderRadius: 20, background: c + "22", color: c, whiteSpace: "nowrap" }}>
      {status}
    </span>
  );
}

function Bar({ label, value, max, color }) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
        <span style={{ color: "#475569" }}>{label}</span>
        <span style={{ fontWeight: 600, color: "#0f172a" }}>{value}</span>
      </div>
      <div style={{ height: 7, background: "#f1f5f9", borderRadius: 4, overflow: "hidden" }}>
        <div style={{ height: 7, width: `${pct}%`, background: color || ACCENT, borderRadius: 4, transition: "width 0.5s ease" }} />
      </div>
    </div>
  );
}

const card = (children, style = {}) => (
  <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", padding: "22px 24px", ...style }}>
    {children}
  </div>
);

const title = (t, sub) => (
  <div style={{ marginBottom: 16 }}>
    <div style={{ fontSize: 14, fontWeight: 700, color: "#0f172a" }}>{t}</div>
    {sub && <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 2 }}>{sub}</div>}
  </div>
);

const th = { padding: "9px 14px", textAlign: "left", color: "#64748b", fontWeight: 600, fontSize: 11, borderBottom: "1px solid #e2e8f0", whiteSpace: "nowrap", textTransform: "uppercase", letterSpacing: "0.03em" };
const td = { padding: "10px 14px", fontSize: 13, color: "#0f172a", borderBottom: "1px solid #f1f5f9", whiteSpace: "nowrap" };

export default function Dashboard() {
  const [summary, setSummary] = useState(null);
  const [inv, setInv]         = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState("");
  const [inventoryPage, setInventoryPage] = useState(0);
  const [inventoryPageSize, setInventoryPageSize] = useState(10);
  const [stockPage, setStockPage] = useState(0);
  const [stockPageSize, setStockPageSize] = useState(10);
  const [orderPage, setOrderPage] = useState(0);
  const [orderPageSize, setOrderPageSize] = useState(10);
  const [alertPage, setAlertPage] = useState(0);
  const [alertPageSize, setAlertPageSize] = useState(10);

  const load = useCallback(() => {
    setLoading(true);
    setError("");
    Promise.all([api.get("/admin/summary"), api.get("/admin/inventory-analysis")])
      .then(([s, i]) => { setSummary(s.data); setInv(i.data); })
      .catch(() => setError("Failed to load admin dashboard."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) return <div style={{ padding: 48, textAlign: "center", color: "#94a3b8", fontFamily: font }}>Loading dashboard...</div>;

  if (error) return (
    <div style={{ padding: 32, fontFamily: font }}>
      <div style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: 8, padding: 16, color: "#b91c1c" }}>{error}</div>
    </div>
  );

  const s = summary || {};
  const iv = inv || {};

  const procurement = s.totalProcurementOrders ?? 0;
  const vendor = s.totalVendorOrders ?? 0;
  const splitTotal = (procurement + vendor) || 1;
  const procPct = Math.round((procurement / splitTotal) * 100);

  // Tolerate both plain arrays and paginated { content: [...] } payloads so a shape change
  // can never crash the dashboard render (the old cause of the blank/dark admin screen).
  const asArray = v => (Array.isArray(v) ? v : Array.isArray(v?.content) ? v.content : []);

  const topBought = asArray(iv.topBought).length ? asArray(iv.topBought) : (s.topBoughtItems || []);
  const topSold = asArray(iv.topSold).length ? asArray(iv.topSold) : (s.topSoldItems || []);
  const maxBought = Math.max(...topBought.map(x => x.quantity || 0), 1);
  const maxSold = Math.max(...topSold.map(x => x.quantity || 0), 1);

  const inventory = asArray(iv.inventory);
  const lowStock = asArray(iv.lowStock);
  const recentOrders = s.recentOrders || [];
  const recentAlerts = s.recentAlerts || [];
  const pagedInventory = inventory.slice(inventoryPage * inventoryPageSize, (inventoryPage + 1) * inventoryPageSize);
  const pagedLowStock = lowStock.slice(stockPage * stockPageSize, (stockPage + 1) * stockPageSize);
  const pagedOrders = recentOrders.slice(orderPage * orderPageSize, (orderPage + 1) * orderPageSize);
  const pagedAlerts = recentAlerts.slice(alertPage * alertPageSize, (alertPage + 1) * alertPageSize);

  const thresholdOf = (it) => it.thresholdQuantity ?? it.reorderLevel ?? 0;

  return (
    <div style={{ padding: "32px", fontFamily: font, color: "#0f172a" }}>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700 }}>Admin Dashboard</div>
          <div style={{ fontSize: 13, color: "#64748b", marginTop: 3 }}>System 1 business overview — read-only</div>
        </div>
        <button
          onClick={load}
          style={{ background: ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "8px 18px", fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: font }}
        >
          Refresh
        </button>
      </div>

      {/* KPI cards */}
      <div style={{ display: "flex", gap: 14, marginBottom: 14, flexWrap: "wrap" }}>
        <StatCard label="Procurement Orders" value={procurement} accent="#6d28d9" sub="POs raised" />
        <StatCard label="Vendor Orders" value={vendor} accent="#1d4ed8" sub="Orders received" />
        <StatCard label="Active Orders" value={s.activeOrders ?? 0} accent={ACCENT} />
        <StatCard label="Delivered" value={s.deliveredOrders ?? 0} accent="#15803d" />
      </div>
      <div style={{ display: "flex", gap: 14, marginBottom: 24, flexWrap: "wrap" }}>
        <StatCard label="Cancelled" value={s.cancelledOrders ?? 0} accent="#b91c1c" />
        <StatCard label="Rejected" value={s.rejectedOrders ?? 0} accent="#ef4444" />
        <StatCard label="Active Shipments" value={s.activeShipments ?? 0} accent="#0369a1" />
        <StatCard label="Low Stock Items" value={s.lowStockItemCount ?? 0} accent={(s.lowStockItemCount ?? 0) > 0 ? "#d97706" : "#15803d"} sub={`${s.totalInventoryItems ?? 0} items tracked`} />
      </div>

      {/* Order recovery + system health */}
      <div style={{ display: "flex", gap: 14, marginBottom: 24, flexWrap: "wrap", alignItems: "stretch" }}>
        <StatCard label="Stuck Orders" value={s.stuckOrdersCount ?? 0} accent={(s.stuckOrdersCount ?? 0) > 0 ? "#d97706" : "#15803d"} sub="awaiting recovery" />
        <StatCard label="Recovered (last run)" value={s.recoveredOrdersLastRun ?? 0} accent="#0f766e" />
        <StatCard label="Vendor Stuck" value={s.vendorStuckOrders ?? 0} accent="#1d4ed8" sub="Flow A" />
        <StatCard label="Procurement Stuck" value={s.procurementStuckOrders ?? 0} accent="#6d28d9" sub="Flow B" />
        <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", padding: "18px 20px", flex: 1, minWidth: 220 }}>
          <div style={{ fontSize: 11, color: "#94a3b8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>Generator Health</div>
          {(() => {
            const cap = s.openPoCapStatus || {};
            const reached = !!cap.capReached;
            return (
              <>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ width: 9, height: 9, borderRadius: "50%", background: reached ? "#d97706" : "#22c55e", display: "inline-block" }} />
                  <span style={{ fontSize: 14, fontWeight: 700, color: reached ? "#b45309" : "#15803d" }}>
                    {reached ? "Paused — open-PO cap reached" : "Running"}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: "#64748b", marginTop: 6 }}>
                  {(cap.openPoCount ?? 0)} / {(cap.cap ?? 0)} open POs
                  {reached ? " · throttled by design (not an error)" : ""}
                </div>
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
                  Last recovery: {s.lastRecoveryRunAt ? new Date(s.lastRecoveryRunAt).toLocaleString("en-IN") : "—"}
                </div>
              </>
            );
          })()}
        </div>
      </div>

      {/* Procurement vs Vendor + Goods bought/sold */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 16 }}>
        {card(
          <>
            {title("Procurement vs Vendor Orders", "Inbound POs vs outbound sales")}
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div>
                <div style={{ fontSize: 11, color: "#6d28d9", fontWeight: 600, textTransform: "uppercase" }}>Procurement</div>
                <div style={{ fontSize: 28, fontWeight: 700, color: "#6d28d9" }}>{procurement}</div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div style={{ fontSize: 11, color: "#1d4ed8", fontWeight: 600, textTransform: "uppercase" }}>Vendor</div>
                <div style={{ fontSize: 28, fontWeight: 700, color: "#1d4ed8" }}>{vendor}</div>
              </div>
            </div>
            <div style={{ height: 10, background: "#f1f5f9", borderRadius: 5, overflow: "hidden", display: "flex" }}>
              <div style={{ width: `${procPct}%`, background: "#6d28d9" }} />
              <div style={{ width: `${100 - procPct}%`, background: "#1d4ed8" }} />
            </div>
          </>
        )}
        {card(
          <>
            {title("Goods Bought vs Sold", "Total units across all orders")}
            <div style={{ display: "flex", gap: 12 }}>
              <div style={{ flex: 1, background: "#f5f3ff", borderRadius: 8, padding: "14px 16px" }}>
                <div style={{ fontSize: 11, color: "#6d28d9", fontWeight: 600, marginBottom: 4 }}>BOUGHT (PROCUREMENT)</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: "#6d28d9" }}>{(s.totalGoodsBought ?? iv.totalGoodsBought ?? 0).toLocaleString("en-IN")}</div>
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>units</div>
              </div>
              <div style={{ flex: 1, background: "#eff6ff", borderRadius: 8, padding: "14px 16px" }}>
                <div style={{ fontSize: 11, color: "#1d4ed8", fontWeight: 600, marginBottom: 4 }}>SOLD (VENDOR)</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: "#1d4ed8" }}>{(s.totalGoodsSold ?? iv.totalGoodsSold ?? 0).toLocaleString("en-IN")}</div>
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>units</div>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Bought vs Sold item analysis */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 16 }}>
        {card(
          <>
            {title("Top Bought Items", "By procurement volume")}
            {topBought.length === 0
              ? <div style={{ color: "#94a3b8", fontSize: 13 }}>No procurement data yet</div>
              : topBought.map((x, i) => <Bar key={i} label={x.item} value={x.quantity} max={maxBought} color="#6d28d9" />)}
          </>
        )}
        {card(
          <>
            {title("Top Sold Items", "By vendor volume")}
            {topSold.length === 0
              ? <div style={{ color: "#94a3b8", fontSize: 13 }}>No vendor data yet</div>
              : topSold.map((x, i) => <Bar key={i} label={x.item} value={x.quantity} max={maxSold} color="#1d4ed8" />)}
          </>
        )}
      </div>

      {/* Low stock alerts */}
      {card(
        <>
          {title("Low Stock Alerts", `${lowStock.length} item${lowStock.length !== 1 ? "s" : ""} below threshold`)}
          {lowStock.length === 0
            ? <div style={{ color: "#15803d", fontSize: 13 }}>All inventory above threshold.</div>
            : (
              <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
                {pagedLowStock.map((it, i) => (
                  <div key={it.id || i} style={{ background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 8, padding: "10px 14px", minWidth: 180 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a" }}>{it.itemName || it.sku || "Item"}</div>
                    <div style={{ fontSize: 12, color: "#b45309", marginTop: 2 }}>
                      {(it.quantity ?? 0)} / {thresholdOf(it)} {it.unit || ""}
                    </div>
                  </div>
                ))}
              </div>
            )}
          <Pagination page={stockPage} pageSize={stockPageSize} totalRecords={lowStock.length} onPageChange={setStockPage} onPageSizeChange={size => { setStockPageSize(size); setStockPage(0); }} />
        </>,
        { marginBottom: 16 }
      )}

      {/* Inventory table */}
      {card(
        <>
          {title("Inventory Levels", `${inventory.length} item${inventory.length !== 1 ? "s" : ""} · ${(s.totalInventoryQuantity ?? iv.totalInventoryQuantity ?? 0).toLocaleString("en-IN")} total units`)}
          {inventory.length === 0
            ? <div style={{ color: "#94a3b8", fontSize: 13 }}>No inventory records.</div>
            : (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 640 }}>
                  <thead><tr style={{ background: "#f8fafc" }}>
                    {["Item", "SKU", "Category", "Quantity", "Threshold", "Status"].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {pagedInventory.map((it, i) => {
                      const qty = it.quantity ?? 0;
                      const thr = thresholdOf(it);
                      const low = qty < thr;
                      return (
                        <tr key={it.id || i}>
                          <td style={{ ...td, fontWeight: 500 }}>{it.itemName || "-"}</td>
                          <td style={{ ...td, color: "#64748b" }}>{it.sku || "-"}</td>
                          <td style={{ ...td, color: "#64748b" }}>{it.category || "-"}</td>
                          <td style={td}>{qty.toLocaleString("en-IN")} {it.unit || ""}</td>
                          <td style={{ ...td, color: "#64748b" }}>{thr.toLocaleString("en-IN")}</td>
                          <td style={td}>
                            <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 9px", borderRadius: 20, background: low ? "#fef3c7" : "#f0fdf4", color: low ? "#b45309" : "#15803d" }}>
                              {low ? "Low" : "OK"}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          <Pagination page={inventoryPage} pageSize={inventoryPageSize} totalRecords={inventory.length} onPageChange={setInventoryPage} onPageSizeChange={size => { setInventoryPageSize(size); setInventoryPage(0); }} />
        </>,
        { marginBottom: 16 }
      )}

      {/* Recent orders + recent alerts */}
      <div style={{ display: "grid", gridTemplateColumns: "1.6fr 1fr", gap: 16 }}>
        {card(
          <>
            {title("Recent Orders", "Procurement & vendor")}
            {recentOrders.length === 0
              ? <div style={{ color: "#94a3b8", fontSize: 13 }}>No recent orders.</div>
              : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 560 }}>
                    <thead><tr style={{ background: "#f8fafc" }}>
                      {["Order ID", "Type", "Counterparty", "Amount", "Status", "Date"].map(h => <th key={h} style={th}>{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {pagedOrders.map((o, i) => (
                        <tr key={o.id || o.orderId || i}>
                          <td style={{ ...td, fontWeight: 500 }}>{o.orderId || o.id}</td>
                          <td style={td}>
                            <span style={{ fontSize: 11, fontWeight: 600, color: o.direction === "OUTBOUND" ? "#1d4ed8" : "#6d28d9" }}>
                              {o.direction === "OUTBOUND" ? "Vendor" : "Procurement"}
                            </span>
                          </td>
                          <td style={{ ...td, color: "#475569" }}>{o.counterpartyName || "-"}</td>
                          <td style={td}>{inr(o.totalAmount)}</td>
                          <td style={td}><StatusBadge status={o.status} /></td>
                          <td style={{ ...td, color: "#64748b" }}>
                            {o.createdAt ? new Date(o.createdAt).toLocaleDateString("en-IN", { month: "short", day: "numeric", year: "numeric" }) : "-"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            <Pagination page={orderPage} pageSize={orderPageSize} totalRecords={recentOrders.length} onPageChange={setOrderPage} onPageSizeChange={size => { setOrderPageSize(size); setOrderPage(0); }} />
          </>
        )}
        {card(
          <>
            {title("Recent Alerts")}
            {recentAlerts.length === 0
              ? <div style={{ color: "#94a3b8", fontSize: 13 }}>No alerts.</div>
              : pagedAlerts.map((a, i) => (
                <div key={a.id || i} style={{ padding: "9px 0", borderBottom: i < pagedAlerts.length - 1 ? "1px solid #f1f5f9" : "none" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
                    <div style={{ fontSize: 13, color: "#0f172a", flex: 1 }}>{a.message}</div>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 7px", borderRadius: 10, background: "#fef3c7", color: "#b45309", whiteSpace: "nowrap" }}>
                      {(a.type || "").replace(/_/g, " ")}
                    </span>
                  </div>
                </div>
              ))}
            <Pagination page={alertPage} pageSize={alertPageSize} totalRecords={recentAlerts.length} onPageChange={setAlertPage} onPageSizeChange={size => { setAlertPageSize(size); setAlertPage(0); }} />
          </>
        )}
      </div>

    </div>
  );
}
