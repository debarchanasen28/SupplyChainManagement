import { useState, useEffect } from "react";
import axios from "../api/axios";
import Pagination from "../components/Pagination";

const API = "http://localhost:8080/api";

const STATUS_COLORS = {
  REQUESTED: { bg: "#eff6ff", text: "#1d4ed8" },
  STOCK_NOTIFIED: { bg: "#fef9c3", text: "#854d0e" },
  CONFIRMED: { bg: "#f0fdf4", text: "#15803d" },
  PROCESSING: { bg: "#fff7ed", text: "#c2410c" },
  IN_TRANSIT: { bg: "#f5f3ff", text: "#6d28d9" },
  DELIVERED: { bg: "#f0fdf4", text: "#15803d" },
  CANCELLED: { bg: "#fef2f2", text: "#b91c1c" },
  REJECTED: { bg: "#fef2f2", text: "#b91c1c" },
  BUYER_APPROVED: { bg: "#f0fdf4", text: "#15803d" },
  BUYER_REJECTED: { bg: "#fef2f2", text: "#b91c1c" },
};

function StatusBadge({ status }) {
  const c = STATUS_COLORS[status] || { bg: "#f1f5f9", text: "#475569" };

  return (
    <span
      style={{
        background: c.bg,
        color: c.text,
        borderRadius: "999px",
        padding: "4px 10px",
        fontSize: "11px",
        fontWeight: 800,
        letterSpacing: "0.02em",
        whiteSpace: "nowrap",
      }}
    >
      {status || "-"}
    </span>
  );
}

function StatCard({ label, value, sub, color, icon, iconBg }) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: "14px",
        padding: "22px",
        display: "flex",
        alignItems: "center",
        gap: "16px",
        boxShadow: "0 8px 24px rgba(15, 23, 42, 0.04)",
        minWidth: 0,
      }}
    >
      <div
        style={{
          width: "46px",
          height: "46px",
          borderRadius: "14px",
          background: iconBg || "#f3e8ff",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: "22px",
          flexShrink: 0,
        }}
      >
        {icon}
      </div>

      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: "13px",
            color: "#0f172a",
            fontWeight: 700,
            marginBottom: "6px",
          }}
        >
          {label}
        </div>

        <div
          style={{
            fontSize: "27px",
            fontWeight: 800,
            color: color || "#0f172a",
            lineHeight: 1,
          }}
        >
          {value}
        </div>

        {sub && (
          <div style={{ fontSize: "12px", color: "#64748b", marginTop: "8px" }}>
            {sub}
          </div>
        )}
      </div>
    </div>
  );
}

function getItemName(item) {
  return (
    item?.description ||
    item?.productName ||
    item?.itemName ||
    item?.name ||
    item?.sku ||
    "Unnamed item"
  );
}

function getItemQuantity(item) {
  return Number(item?.quantity ?? item?.qty ?? 0);
}

function getOrderItemSummary(order) {
  const orderItems = Array.isArray(order.items)
    ? order.items
    : Array.isArray(order.orderItems)
      ? order.orderItems
      : Array.isArray(order.lineItems)
        ? order.lineItems
        : [];

  const namesFromArray = orderItems
    .map(item => {
      const name = getItemName(item);
      const qty = getItemQuantity(item);
      return qty > 0 ? `${name} × ${qty}` : name;
    })
    .filter(Boolean);

  const namesFromDto = Array.isArray(order.itemNames)
    ? order.itemNames.filter(Boolean)
    : [];

  const names = namesFromArray.length > 0 ? namesFromArray : namesFromDto;

  const count =
    orderItems.length ||
    namesFromDto.length ||
    Number(order.itemCount ?? order.itemsCount ?? 0);

  return { count, names };
}

export default function ProcurementDashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const token = localStorage.getItem("token");

  const fetchStats = () => {
    setLoading(true);
    setError("");

    axios
      .get(`${API}/dashboard/procurement`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(res => {
        setStats(res.data || {});
        setLoading(false);
      })
      .catch(err => {
        console.error("Procurement dashboard load failed:", err.response?.data || err.message, err);
        setError(err.response?.data?.message || err.response?.data?.error || "Failed to load dashboard.");
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchStats();
  }, []);

  if (loading) {
    return (
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          height: "60vh",
        }}
      >
        <span style={{ color: "#64748b", fontSize: "15px" }}>
          Loading dashboard...
        </span>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: "32px" }}>
        <div
          style={{
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "10px",
            padding: "16px",
            color: "#b91c1c",
          }}
        >
          {error}
        </div>
      </div>
    );
  }

  const s = stats || {};
  const recentOrders = s.recentOrders || [];
  const pagedRecentOrders = recentOrders.slice(
    page * pageSize,
    (page + 1) * pageSize
  );

  return (
    <div style={{ padding: "32px", background: "#f8fafc", minHeight: "100vh" }}>
      <div style={{ position: "relative", marginBottom: "24px" }}>
        <div style={{ textAlign: "center", width: "100%" }}>
          <h1
            style={{
              fontSize: "26px",
              fontWeight: 800,
              color: "#0f172a",
              margin: 0,
              letterSpacing: "1px",
            }}
          >
            Procurement Dashboard
          </h1>

          <p
            style={{
              fontSize: "14px",
              color: "#64748b",
              margin: "6px 0 0",
            }}
          >
            Inbound orders and supplier spend overview
          </p>
        </div>

        <button
          onClick={fetchStats}
          style={{
            position: "absolute",
            right: 0,
            top: 0,
            background: "#6d28d9",
            color: "#fff",
            border: "none",
            borderRadius: "10px",
            padding: "10px 20px",
            fontSize: "14px",
            fontWeight: 700,
            cursor: "pointer",
            boxShadow: "0 8px 18px rgba(109, 40, 217, 0.25)",
          }}
        >
          ↻ Refresh
        </button>
      </div>

      {s.unreadAlerts > 0 && (
        <div
          style={{
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "12px",
            padding: "14px 18px",
            marginBottom: "18px",
            color: "#b91c1c",
            fontSize: "14px",
            fontWeight: 700,
          }}
        >
          ⚠ {s.unreadAlerts} unread alert{s.unreadAlerts > 1 ? "s" : ""}
        </div>
      )}

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, 1fr)",
          gap: "16px",
          marginBottom: "16px",
        }}
      >
        <StatCard
          label="Total Orders"
          value={s.totalOrders ?? 0}
          icon="📋"
          iconBg="#f3e8ff"
          sub="All purchase orders"
        />

        <StatCard
          label="Active Orders"
          value={s.totalActive ?? 0}
          color="#6d28d9"
          icon="🧾"
          iconBg="#f3e8ff"
          sub="Currently active"
        />

        <StatCard
          label="Pending Requested"
          value={s.pendingOrders ?? 0}
          color="#d97706"
          icon="🕒"
          iconBg="#fff7ed"
          sub="Awaiting supplier response"
        />
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: "16px",
          marginBottom: "16px",
        }}
      >
        <StatCard
          label="Confirmed"
          value={s.confirmed ?? 0}
          color="#15803d"
          icon="✓"
          iconBg="#dcfce7"
          sub="Supplier confirmed"
        />

        <StatCard
          label="In Transit"
          value={s.inTransit ?? 0}
          color="#0369a1"
          icon="🚚"
          iconBg="#e0f2fe"
          sub="Goods on the way"
        />

        <StatCard
          label="Delivered"
          value={s.delivered ?? 0}
          color="#15803d"
          icon="📦"
          iconBg="#dcfce7"
          sub="Received successfully"
        />

        <StatCard
          label="Cancelled"
          value={s.cancelled ?? 0}
          color="#b91c1c"
          icon="✕"
          iconBg="#fee2e2"
          sub="Orders cancelled"
        />
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1.2fr 1.2fr 1fr",
          gap: "16px",
          marginBottom: "18px",
        }}
      >
        <StatCard
          label="Total Spend"
          value={`₹${(s.totalSpend ?? 0).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}`}
          color="#15803d"
          icon="₹"
          iconBg="#dcfce7"
          sub="From delivered orders"
        />

        <StatCard
          label="Committed Spend"
          value={`₹${(s.committedSpend ?? 0).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}`}
          color="#6d28d9"
          icon="📊"
          iconBg="#ede9fe"
          sub="Active orders value"
        />

        <StatCard
          label="Active Suppliers"
          value={s.supplierCount ?? 0}
          color="#0e7490"
          icon="🏭"
          iconBg="#ccfbf1"
          sub={`${s.shipmentsInTransit ?? 0} shipment${
            (s.shipmentsInTransit ?? 0) !== 1 ? "s" : ""
          } in transit`}
        />
      </div>

      <div
        style={{
          background: "#fff",
          border: "1px solid #e2e8f0",
          borderRadius: "14px",
          overflow: "hidden",
          boxShadow: "0 8px 24px rgba(15, 23, 42, 0.04)",
        }}
      >
        <div
          style={{
            padding: "16px 20px",
            borderBottom: "1px solid #e2e8f0",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <h2
            style={{
              fontSize: "16px",
              fontWeight: 800,
              color: "#0f172a",
              margin: 0,
            }}
          >
            Recent Purchase Orders
          </h2>

          <a
            href="/orders"
            style={{
              fontSize: "13px",
              color: "#6d28d9",
              textDecoration: "none",
              fontWeight: 800,
            }}
          >
            View all orders →
          </a>
        </div>

        {!s.recentOrders || s.recentOrders.length === 0 ? (
          <div
            style={{
              padding: "44px",
              textAlign: "center",
              color: "#94a3b8",
              fontSize: "14px",
            }}
          >
            No purchase orders yet.
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                fontSize: "14px",
                minWidth: "820px",
              }}
            >
              <thead>
                <tr style={{ background: "#f8fafc" }}>
                  {["Order ID", "Supplier", "Items", "Amount", "Status", "Date"].map(h => (
                    <th
                      key={h}
                      style={{
                        padding: "12px 16px",
                        textAlign: "center",
                        color: "#64748b",
                        fontWeight: 800,
                        fontSize: "12px",
                        borderBottom: "1px solid #e2e8f0",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>

              <tbody>
                {pagedRecentOrders.map((order, i) => {
                  const itemSummary = getOrderItemSummary(order);

                  return (
                    <tr key={order.id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                      <td
                        style={{
                          padding: "13px 16px",
                          color: "#6d28d9",
                          fontWeight: 800,
                          textAlign: "center",
                        }}
                      >
                        {order.orderId || order.id}
                      </td>

                      <td
                        style={{
                          padding: "13px 16px",
                          color: "#0f172a",
                          fontWeight: 600,
                          textAlign: "center",
                        }}
                      >
                        {order.counterpartyName || "-"}
                      </td>

                      <td
                        style={{
                          padding: "13px 16px",
                          color: "#475569",
                          textAlign: "center",
                          minWidth: "240px",
                        }}
                      >
                        <div
                          style={{
                            fontWeight: 800,
                            color: "#0f172a",
                            marginBottom: "4px",
                          }}
                        >
                          {itemSummary.count || 0} item
                          {itemSummary.count !== 1 ? "s" : ""}
                        </div>

                        {itemSummary.names.length > 0 && (
                          <div
                            style={{
                              fontSize: "12px",
                              color: "#64748b",
                              lineHeight: 1.4,
                            }}
                          >
                            {itemSummary.names.join(", ")}
                          </div>
                        )}
                      </td>

                      <td
                        style={{
                          padding: "13px 16px",
                          color: "#0f172a",
                          fontWeight: 800,
                          textAlign: "center",
                        }}
                      >
                        ₹
                        {(order.totalAmount ?? 0).toLocaleString("en-IN", {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </td>

                      <td style={{ padding: "13px 16px", textAlign: "center" }}>
                        <StatusBadge status={order.status} />
                      </td>

                      <td
                        style={{
                          padding: "13px 16px",
                          color: "#64748b",
                          whiteSpace: "nowrap",
                          textAlign: "center",
                        }}
                      >
                        {order.createdAt
                          ? new Date(order.createdAt).toLocaleDateString("en-IN", {
                              month: "short",
                              day: "numeric",
                              year: "numeric",
                            })
                          : "-"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <div style={{ padding: "14px 20px 18px" }}>
          <Pagination
            page={page}
            pageSize={pageSize}
            totalRecords={recentOrders.length}
            onPageChange={setPage}
            onPageSizeChange={size => {
              setPageSize(size);
              setPage(0);
            }}
          />
        </div>
      </div>
    </div>
  );
}