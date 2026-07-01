import { useState, useEffect } from "react";
import axios from "../api/axios";

const API = "http://localhost:8080/api";

function StatCard({ label, value, sub, color, border }) {
  return (
    <div style={{
      background: "#fff",
      border: `1px solid ${border || "#e2e8f0"}`,
      borderRadius: "10px",
      padding: "20px 24px",
      display: "flex",
      flexDirection: "column",
      gap: "6px",
      minWidth: 0
    }}>
      <span style={{ fontSize: "13px", color: "#64748b", fontWeight: 500 }}>{label}</span>
      <span style={{ fontSize: "28px", fontWeight: 700, color: color || "#0f172a" }}>{value}</span>
      {sub && <span style={{ fontSize: "12px", color: "#94a3b8" }}>{sub}</span>}
    </div>
  );
}

const STATUS_COLORS = {
  REQUESTED: { bg: "#eff6ff", text: "#1d4ed8" },
  STOCK_NOTIFIED: { bg: "#fef9c3", text: "#854d0e" },
  CONFIRMED: { bg: "#f0fdf4", text: "#15803d" },
  PROCESSING: { bg: "#fff7ed", text: "#c2410c" },
  IN_TRANSIT: { bg: "#f5f3ff", text: "#6d28d9" },
  DELIVERED: { bg: "#f0fdf4", text: "#15803d" },
  CANCELLED: { bg: "#fef2f2", text: "#b91c1c" },
  REJECTED: { bg: "#fef2f2", text: "#b91c1c" },
};

function StatusBadge({ status }) {
  const c = STATUS_COLORS[status] || { bg: "#f1f5f9", text: "#475569" };
  return (
    <span style={{
      background: c.bg,
      color: c.text,
      borderRadius: "20px",
      padding: "2px 10px",
      fontSize: "12px",
      fontWeight: 600
    }}>
      {status}
    </span>
  );
}

function OrderTrendChart({ data }) {
  if (!data || data.length === 0) return null;
  const max = Math.max(...data.map(d => d.orders), 1);
  const chartH = 80;

  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: "8px", height: `${chartH + 24}px`, paddingTop: "8px" }}>
      {data.map((d, i) => {
        const barH = max === 0 ? 0 : Math.round((d.orders / max) * chartH);
        return (
          <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: "4px" }}>
            <span style={{ fontSize: "11px", color: "#64748b" }}>{d.orders}</span>
            <div style={{
              width: "100%",
              height: `${barH || 4}px`,
              background: "#0f766e",
              borderRadius: "4px 4px 0 0",
              opacity: barH === 0 ? 0.2 : 1
            }} />
            <span style={{ fontSize: "11px", color: "#94a3b8" }}>{d.day}</span>
          </div>
        );
      })}
    </div>
  );
}

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [vendorStats, setVendorStats] = useState(null);
  const [procStats, setProcStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState("overview");

  const token = localStorage.getItem("token");
  const headers = { Authorization: `Bearer ${token}` };

  const fetchAll = () => {
    setLoading(true);
    setError("");
    Promise.all([
      axios.get(`${API}/dashboard/stats`, { headers }),
      axios.get(`${API}/dashboard/vendor`, { headers }).catch(() => ({ data: null })),
      axios.get(`${API}/dashboard/procurement`, { headers }).catch(() => ({ data: null }))
    ])
      .then(([main, vendor, proc]) => {
        setStats(main.data);
        setVendorStats(vendor.data);
        setProcStats(proc.data);
        setLoading(false);
      })
      .catch(() => {
        setError("Failed to load dashboard.");
        setLoading(false);
      });
  };

  useEffect(() => { fetchAll(); }, []);

  if (loading) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "60vh" }}>
        <span style={{ color: "#64748b", fontSize: "15px" }}>Loading dashboard...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: "32px" }}>
        <div style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", padding: "16px", color: "#b91c1c" }}>
          {error}
        </div>
      </div>
    );
  }

  const s = stats || {};
  const v = vendorStats || {};
  const p = procStats || {};

  const tabs = ["overview", "vendor", "procurement"];

  return (
    <div style={{ padding: "32px", maxWidth: "1300px", margin: "0 auto" }}>

      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "28px" }}>
        <div style={{ textAlign: "center", width: "100%" }}>
          <h1 style={{ fontSize: "22px", fontWeight: 700, color: "#0f172a", margin: 0, letterSpacing: "1px" }}>
            Admin Dashboard
          </h1>
          <p style={{ fontSize: "13px", color: "#64748b", margin: "4px 0 0" }}>
            Full system overview — inbound and outbound
          </p>
        </div>
        <button
          onClick={fetchAll}
          style={{
            background: "#0f766e",
            color: "#fff",
            border: "none",
            borderRadius: "8px",
            padding: "8px 18px",
            fontSize: "13px",
            fontWeight: 600,
            cursor: "pointer"
          }}
        >
          Refresh
        </button>
      </div>

      {/* High severity alert banner */}
      {s.highSeverityAlerts > 0 && (
        <div style={{
          background: "#fef2f2",
          border: "1px solid #fecaca",
          borderRadius: "8px",
          padding: "12px 16px",
          marginBottom: "24px",
          color: "#b91c1c",
          fontSize: "14px",
          fontWeight: 500
        }}>
          {s.highSeverityAlerts} high severity alert{s.highSeverityAlerts > 1 ? "s" : ""} require attention
        </div>
      )}

      {/* Tabs */}
      <div style={{ display: "flex", gap: "4px", marginBottom: "24px", borderBottom: "1px solid #e2e8f0" }}>
        {tabs.map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            style={{
              padding: "8px 20px",
              border: "none",
              background: "transparent",
              borderBottom: activeTab === tab ? "2px solid #0f766e" : "2px solid transparent",
              color: activeTab === tab ? "#0f766e" : "#64748b",
              fontWeight: activeTab === tab ? 600 : 500,
              fontSize: "14px",
              cursor: "pointer",
              marginBottom: "-1px",
              textTransform: "capitalize"
            }}
          >
            {tab === "vendor" ? "Vendor (Outbound)" : tab === "procurement" ? "Procurement (Inbound)" : "Overview"}
          </button>
        ))}
      </div>

      {/* OVERVIEW TAB */}
      {activeTab === "overview" && (
        <>
          {/* Main stat cards */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px", marginBottom: "16px" }}>
            <StatCard label="Total Orders" value={s.totalOrders ?? 0} />
            <StatCard label="Pending Orders" value={s.pendingOrders ?? 0} color="#d97706" />
            <StatCard label="Active Shipments" value={s.activeShipments ?? 0} color="#0369a1" />
            <StatCard label="In Transit" value={s.inTransitShipments ?? 0} color="#6d28d9" />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "16px", marginBottom: "28px" }}>
            <StatCard label="Open Alerts" value={s.openAlerts ?? 0} color={s.openAlerts > 0 ? "#b91c1c" : "#15803d"} />
            <StatCard label="High Severity Alerts" value={s.highSeverityAlerts ?? 0} color={s.highSeverityAlerts > 0 ? "#b91c1c" : "#15803d"} />
            <StatCard label="Active Suppliers" value={s.activeSuppliers ?? 0} color="#0f766e" />
          </div>

          {/* Order trend + Recent alerts */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px", marginBottom: "28px" }}>

            {/* Order trend */}
            <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: "10px", padding: "20px" }}>
              <h2 style={{ fontSize: "15px", fontWeight: 600, color: "#0f172a", margin: "0 0 16px" }}>
                Order Trend (Last 7 Days)
              </h2>
              <OrderTrendChart data={s.orderTrend} />
            </div>

            {/* Recent alerts */}
            <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: "10px", padding: "20px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
                <h2 style={{ fontSize: "15px", fontWeight: 600, color: "#0f172a", margin: 0 }}>
                  Recent Alerts
                </h2>
                <a href="/alerts" style={{ fontSize: "13px", color: "#0f766e", textDecoration: "none", fontWeight: 500 }}>
                  View all
                </a>
              </div>
              {!s.recentAlerts || s.recentAlerts.length === 0 ? (
                <p style={{ color: "#94a3b8", fontSize: "14px", margin: 0 }}>No active alerts.</p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  {s.recentAlerts.map((alert, i) => (
                    <div key={alert.id || i} style={{
                      background: "#f8fafc",
                      borderRadius: "8px",
                      padding: "10px 14px",
                      borderLeft: "3px solid #0f766e"
                    }}>
                      <div style={{ fontSize: "13px", color: "#0f172a", fontWeight: 500 }}>{alert.type}</div>
                      <div style={{ fontSize: "12px", color: "#64748b", marginTop: "2px" }}>{alert.message}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Recent orders */}
          <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: "10px", overflow: "hidden" }}>
            <div style={{ padding: "16px 20px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2 style={{ fontSize: "15px", fontWeight: 600, color: "#0f172a", margin: 0 }}>Recent Orders</h2>
              <a href="/orders" style={{ fontSize: "13px", color: "#0f766e", textDecoration: "none", fontWeight: 500 }}>View all</a>
            </div>
            {!s.recentOrders || s.recentOrders.length === 0 ? (
              <div style={{ padding: "40px", textAlign: "center", color: "#94a3b8", fontSize: "14px" }}>No orders yet.</div>
            ) : (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "14px" }}>
                  <thead>
                    <tr style={{ background: "#f8fafc" }}>
                      {["Order ID", "Direction", "Counterparty", "Amount", "Status", "Date"].map(h => (
                        <th key={h} style={{ padding: "10px 16px", textAlign: "left", color: "#64748b", fontWeight: 600, fontSize: "12px", borderBottom: "1px solid #e2e8f0" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {s.recentOrders.map((order, i) => (
                      <tr key={order.id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                        <td style={{ padding: "12px 16px", color: "#0f766e", fontWeight: 500 }}>{order.orderId || order.id}</td>
                        <td style={{ padding: "12px 16px" }}>
                          <span style={{
                            background: order.direction === "OUTBOUND" ? "#eff6ff" : "#f5f3ff",
                            color: order.direction === "OUTBOUND" ? "#1d4ed8" : "#6d28d9",
                            borderRadius: "20px",
                            padding: "2px 10px",
                            fontSize: "12px",
                            fontWeight: 600
                          }}>
                            {order.direction || "-"}
                          </span>
                        </td>
                        <td style={{ padding: "12px 16px", color: "#0f172a" }}>{order.counterpartyName || "-"}</td>
                        <td style={{ padding: "12px 16px", color: "#0f172a", fontWeight: 500 }}>
                          ${(order.totalAmount ?? 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </td>
                        <td style={{ padding: "12px 16px" }}><StatusBadge status={order.status} /></td>
                        <td style={{ padding: "12px 16px", color: "#64748b" }}>
                          {order.createdAt ? new Date(order.createdAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "-"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {/* VENDOR TAB */}
      {activeTab === "vendor" && (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px", marginBottom: "16px" }}>
            <StatCard label="Total Outbound Orders" value={v.totalOrders ?? 0} />
            <StatCard label="Active" value={v.totalActive ?? 0} color="#1d4ed8" />
            <StatCard label="Pending Approvals" value={v.pendingApprovals ?? 0} color="#d97706" />
            <StatCard label="Awaiting Buyer Response" value={v.awaitingBuyerResponse ?? 0} color="#7c3aed" />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px", marginBottom: "28px" }}>
            <StatCard label="Confirmed" value={v.confirmed ?? 0} color="#15803d" />
            <StatCard label="In Transit" value={v.inTransit ?? 0} color="#0369a1" />
            <StatCard label="Delivered" value={v.delivered ?? 0} color="#15803d" />
            <StatCard label="Cancelled" value={v.cancelled ?? 0} color="#b91c1c" />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "16px" }}>
            <StatCard
              label="Total Revenue"
              value={`$${(v.totalRevenue ?? 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
              color="#15803d"
              sub="From delivered outbound orders"
            />
            <StatCard
              label="Pipeline Value"
              value={`$${(v.pipelineValue ?? 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
              color="#1d4ed8"
              sub="Active outbound orders"
            />
          </div>
        </>
      )}

      {/* PROCUREMENT TAB */}
      {activeTab === "procurement" && (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px", marginBottom: "16px" }}>
            <StatCard label="Total Inbound Orders" value={p.totalOrders ?? 0} />
            <StatCard label="Active" value={p.totalActive ?? 0} color="#6d28d9" />
            <StatCard label="Pending (Requested)" value={p.pendingOrders ?? 0} color="#d97706" />
            <StatCard label="Awaiting Our Response" value={p.awaitingOurResponse ?? 0} color="#dc2626" />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px", marginBottom: "28px" }}>
            <StatCard label="Confirmed" value={p.confirmed ?? 0} color="#15803d" />
            <StatCard label="In Transit" value={p.inTransit ?? 0} color="#0369a1" />
            <StatCard label="Delivered" value={p.delivered ?? 0} color="#15803d" />
            <StatCard label="Cancelled" value={p.cancelled ?? 0} color="#b91c1c" />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "16px" }}>
            <StatCard
              label="Total Spend"
              value={`$${(p.totalSpend ?? 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
              color="#15803d"
              sub="From delivered inbound orders"
            />
            <StatCard
              label="Committed Spend"
              value={`$${(p.committedSpend ?? 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
              color="#6d28d9"
              sub="Active inbound orders"
            />
            <StatCard label="Active Suppliers" value={p.supplierCount ?? 0} color="#0369a1" />
          </div>
        </>
      )}

    </div>
  );
}