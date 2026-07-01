import { useState, useEffect, useCallback } from "react";
import axios from "../api/axios";
import Pagination from "../components/Pagination";

const API = "http://localhost:8080/api";

const SHIPMENT_STATUS_COLORS = {
  PENDING: { bg: "#f1f5f9", text: "#475569", border: "#e2e8f0" },
  VENDOR_CONFIRMED: { bg: "#eff6ff", text: "#1d4ed8", border: "#bfdbfe" },
  PROCESSING: { bg: "#fff7ed", text: "#c2410c", border: "#fed7aa" },
  IN_TRANSIT: { bg: "#f5f3ff", text: "#6d28d9", border: "#ddd6fe" },
  SHIPPED: { bg: "#ecfeff", text: "#0e7490", border: "#a5f3fc" },
  DELIVERED: { bg: "#f0fdf4", text: "#15803d", border: "#bbf7d0" },
  CANCELLED: { bg: "#fef2f2", text: "#b91c1c", border: "#fecaca" },
};

function StatusBadge({ status }) {
  const c = SHIPMENT_STATUS_COLORS[status] || SHIPMENT_STATUS_COLORS.PENDING;

  return (
    <span
      style={{
        display: "inline-block",
        background: c.bg,
        color: c.text,
        border: `1px solid ${c.border}`,
        borderRadius: "8px",
        padding: "5px 11px",
        fontSize: "11px",
        fontWeight: 800,
        whiteSpace: "nowrap",
      }}
    >
      {(status || "-").replace(/_/g, " ")}
    </span>
  );
}

function DirectionBadge({ direction }) {
  return (
    <span
      style={{
        background: direction === "OUTBOUND" ? "#eff6ff" : "#f5f3ff",
        color: direction === "OUTBOUND" ? "#1d4ed8" : "#6d28d9",
        border: `1px solid ${direction === "OUTBOUND" ? "#bfdbfe" : "#ddd6fe"}`,
        borderRadius: "999px",
        padding: "4px 10px",
        fontSize: "11px",
        fontWeight: 800,
        whiteSpace: "nowrap",
      }}
    >
      {direction || "-"}
    </span>
  );
}

const getVendorShippingStatus = order => {
  if (order.poStatus === "SHIPPED") return "SHIPPED";
  if (order.status === "CONFIRMED" && order.vendorFinalDecision === "CONFIRMED") {
    return "VENDOR_CONFIRMED";
  }
  return order.shippingStatus || order.status;
};

const getItemName = item =>
  item.description ||
  item.productName ||
  item.itemName ||
  item.name ||
  item.materialName ||
  item.sku ||
  "-";

const getItemQuantity = item =>
  Number(item.quantity ?? item.qty ?? item.orderedQuantity ?? item.requestedQuantity ?? 0);

function VendorShipmentTable({
  orders,
  accentColor,
  counterpartyLabel = "Buyer",
  resolveStatus = getVendorShippingStatus,
}) {
  console.log("SHIPMENT DATA:", orders);
  const thStyle = {
    padding: "14px 16px",
    textAlign: "center",
    color: "#334155",
    fontWeight: 700,
    fontSize: "13px",
    borderBottom: "1px solid #e2e8f0",
    whiteSpace: "nowrap",
  };

  const tdStyle = {
    padding: "14px 16px",
    textAlign: "center",
    color: "#475569",
    borderBottom: "1px solid #f1f5f9",
    verticalAlign: "middle",
  };

  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        padding: "16px",
        boxShadow: "0 8px 24px rgba(15, 23, 42, 0.04)",
      }}
    >
      <div style={{ overflowX: "auto" }}>
        <table
          style={{
            width: "100%",
            minWidth: "960px",
            borderCollapse: "collapse",
            fontSize: "14px",
            border: "1px solid #e2e8f0",
            borderRadius: "10px",
            overflow: "hidden",
          }}
        >
          <thead>
            <tr style={{ background: "#f8fafc" }}>
              {["Order ID", "Items", "Quantity", counterpartyLabel, "Shipping Status", "Last Updated", "Expected Delivery"].map(label => (
                <th key={label} style={thStyle}>
                  {label}
                </th>
              ))}
            </tr>
          </thead>

          <tbody>
            {orders.map(order => {
              const orderItems = Array.isArray(order.items) ? order.items : [];
              const itemNames =
                Array.isArray(order.itemNames) && order.itemNames.length > 0
                  ? order.itemNames.filter(Boolean)
                  : orderItems.map(getItemName).filter(Boolean);
              const totalQuantity =
                Number(order.quantity ?? order.totalQuantity ?? 0) ||
                orderItems.reduce((sum, item) => sum + getItemQuantity(item), 0);

              const lastUpdated = order.statusUpdatedAt || order.resolvedAt || order.updatedAt || order.createdAt;

              return (
                <tr key={order.id || order.orderId}>
                  <td style={{ ...tdStyle, color: accentColor, fontWeight: 700, whiteSpace: "nowrap" }}>
                    {order.orderId || order.id}
                  </td>

                  <td style={{ ...tdStyle, color: "#0f172a", maxWidth: "320px" }}>
                    {itemNames.length > 0 ? itemNames.join(", ") : "-"}
                  </td>

                  <td style={{ ...tdStyle, fontWeight: 700 }}>{totalQuantity}</td>

                  <td style={tdStyle}>
                    {order.counterpartyName ||
                      (counterpartyLabel === "Supplier" ? "System 2 Vendor" : "System 2 Procurement")}
                  </td>

                  <td style={tdStyle}>
                    <StatusBadge status={resolveStatus(order)} />
                  </td>

                  <td style={{ ...tdStyle, whiteSpace: "nowrap" }}>
                    {lastUpdated ? new Date(lastUpdated).toLocaleString("en-IN") : "-"}
                  </td>

                  <td style={{ ...tdStyle, whiteSpace: "nowrap" }}>
                    {order.expectedDeliveryDate || order.estimatedDelivery || "-"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function CreateShipmentModal({ onClose, onCreated, role }) {
  const [form, setForm] = useState({
    orderId: "",
    counterpartyId: "",
    counterpartyName: "",
    carrier: "",
    trackingNumber: "",
    origin: "",
    destination: "",
    estimatedDelivery: "",
    notes: "",
  });

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const token = localStorage.getItem("token");

  const inputStyle = {
    width: "100%",
    padding: "9px 11px",
    borderRadius: "8px",
    border: "1px solid #e2e8f0",
    fontSize: "14px",
    background: "#fff",
    color: "#0f172a",
    boxSizing: "border-box",
  };

  const submit = () => {
    if (!form.counterpartyName.trim()) {
      setError("Counterparty name is required.");
      return;
    }

    if (!form.origin.trim() || !form.destination.trim()) {
      setError("Origin and destination are required.");
      return;
    }

    setSaving(true);
    setError("");

    axios
      .post(`${API}/shipments`, form, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(res => {
        onCreated(res.data);
        onClose();
      })
      .catch(err => {
        setError(err.response?.data?.message || "Failed to create shipment.");
        setSaving(false);
      });
  };

  const fields = [
    { key: "orderId", label: "Linked Order ID", placeholder: "Optional" },
    {
      key: "counterpartyName",
      label: role === "PROCUREMENT" ? "Supplier Name *" : "Receiver Name *",
      placeholder: "Counterparty name",
    },
    { key: "counterpartyId", label: "Counterparty ID", placeholder: "Optional" },
    { key: "carrier", label: "Carrier", placeholder: "e.g. FedEx, DHL" },
    { key: "trackingNumber", label: "Tracking Number", placeholder: "Optional" },
    { key: "origin", label: "Origin *", placeholder: "e.g. Mumbai, IN" },
    { key: "destination", label: "Destination *", placeholder: "e.g. Berlin, DE" },
    { key: "estimatedDelivery", label: "Estimated Delivery", placeholder: "", type: "date" },
  ];

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.4)",
        zIndex: 1000,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        style={{
          background: "#fff",
          borderRadius: "14px",
          padding: "28px",
          width: "580px",
          maxHeight: "90vh",
          overflowY: "auto",
          boxShadow: "0 20px 60px rgba(0,0,0,0.15)",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
          <h2 style={{ fontSize: "18px", fontWeight: 800, color: "#0f172a", margin: 0 }}>
            {role === "PROCUREMENT" ? "Log Inbound Shipment" : "Create Outbound Shipment"}
          </h2>

          <button onClick={onClose} style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}>
            ×
          </button>
        </div>

        {error && (
          <div
            style={{
              background: "#fef2f2",
              border: "1px solid #fecaca",
              borderRadius: "8px",
              padding: "10px 14px",
              color: "#b91c1c",
              fontSize: "13px",
              marginBottom: "16px",
            }}
          >
            {error}
          </div>
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px", marginBottom: "16px" }}>
          {fields.map(f => (
            <div key={f.key}>
              <label style={{ fontSize: "12px", fontWeight: 700, color: "#475569", display: "block", marginBottom: "5px" }}>
                {f.label}
              </label>

              <input
                type={f.type || "text"}
                style={inputStyle}
                placeholder={f.placeholder}
                value={form[f.key]}
                onChange={e => setForm(prev => ({ ...prev, [f.key]: e.target.value }))}
              />
            </div>
          ))}
        </div>

        <div style={{ marginBottom: "20px" }}>
          <label style={{ fontSize: "12px", fontWeight: 700, color: "#475569", display: "block", marginBottom: "5px" }}>
            Notes
          </label>

          <textarea
            style={{ ...inputStyle, height: "72px", resize: "vertical" }}
            placeholder="Optional notes"
            value={form.notes}
            onChange={e => setForm(prev => ({ ...prev, notes: e.target.value }))}
          />
        </div>

        <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end" }}>
          <button
            onClick={onClose}
            style={{
              padding: "9px 18px",
              border: "1px solid #e2e8f0",
              borderRadius: "8px",
              background: "#fff",
              color: "#475569",
              fontSize: "14px",
              cursor: "pointer",
            }}
          >
            Cancel
          </button>

          <button
            onClick={submit}
            disabled={saving}
            style={{
              padding: "9px 18px",
              border: "none",
              borderRadius: "8px",
              background: "#4f46e5",
              color: "#fff",
              fontSize: "14px",
              fontWeight: 700,
              cursor: saving ? "default" : "pointer",
              opacity: saving ? 0.7 : 1,
            }}
          >
            {saving ? "Creating..." : "Create Shipment"}
          </button>
        </div>
      </div>
    </div>
  );
}

function UpdateStatusPanel({ shipment, onDone, onClose }) {
  const [status, setStatus] = useState(shipment.status || "PENDING");
  const [delayReason, setDelayReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const token = localStorage.getItem("token");

  const NEXT_STATUSES = {
    PENDING: ["IN_TRANSIT", "CANCELLED"],
    IN_TRANSIT: ["DELIVERED", "CANCELLED"],
    DELIVERED: [],
    CANCELLED: [],
  };

  const options = NEXT_STATUSES[shipment.status] || [];

  const submit = () => {
    if (!status) {
      setError("Select a status.");
      return;
    }

    setSaving(true);

    axios
      .put(
        `${API}/shipments/${shipment.id}/status`,
        { status, delayReason },
        { headers: { Authorization: `Bearer ${token}` } }
      )
      .then(res => {
        onDone(res.data);
        onClose();
      })
      .catch(err => {
        setError(err.response?.data?.message || "Failed to update.");
        setSaving(false);
      });
  };

  if (options.length === 0) {
    return (
      <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "10px", padding: "14px 16px", marginTop: "10px" }}>
        <p style={{ fontSize: "13px", color: "#64748b", margin: 0 }}>No further status transitions available.</p>

        <button
          onClick={onClose}
          style={{
            marginTop: "8px",
            padding: "5px 12px",
            border: "1px solid #e2e8f0",
            borderRadius: "6px",
            background: "#fff",
            color: "#475569",
            fontSize: "12px",
            cursor: "pointer",
          }}
        >
          Close
        </button>
      </div>
    );
  }

  return (
    <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "10px", padding: "14px 16px", marginTop: "10px" }}>
      {error && <div style={{ color: "#b91c1c", fontSize: "13px", marginBottom: "8px" }}>{error}</div>}

      <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
        <select
          value={status}
          onChange={e => setStatus(e.target.value)}
          style={{ padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: "8px", fontSize: "14px", background: "#fff", color: "#0f172a" }}
        >
          {options.map(s => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>

        <input
          placeholder="Delay reason (optional)"
          value={delayReason}
          onChange={e => setDelayReason(e.target.value)}
          style={{ flex: 1, padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: "8px", fontSize: "14px", background: "#fff", color: "#0f172a" }}
        />

        <button
          onClick={submit}
          disabled={saving}
          style={{ padding: "8px 14px", background: "#4f46e5", color: "#fff", border: "none", borderRadius: "8px", fontSize: "13px", fontWeight: 700, cursor: "pointer" }}
        >
          {saving ? "Updating..." : "Update"}
        </button>

        <button
          onClick={onClose}
          style={{ padding: "8px 14px", background: "#fff", color: "#475569", border: "1px solid #e2e8f0", borderRadius: "8px", fontSize: "13px", cursor: "pointer" }}
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

export default function Shipments() {
  const [shipments, setShipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState("all");
  const [showCreate, setShowCreate] = useState(false);
  const [expandedId, setExpandedId] = useState(null);
  const [updateStatusId, setUpdateStatusId] = useState(null);
  const [confirmCancel, setConfirmCancel] = useState({});
  const [search, setSearch] = useState("");
  const [actionLoading, setActionLoading] = useState({});
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [pageMeta, setPageMeta] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });

  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role");
  const headers = { Authorization: `Bearer ${token}` };

  const accentColor = "#4f46e5";

  const TABS = [
    { key: "all", label: "All Shipments" },
    { key: "active", label: "Active" },
    { key: "past", label: "Past" },
  ];

  const fetchShipments = useCallback(() => {
    setLoading(true);
    setError("");

    const url =
      role === "VENDOR"
        ? `${API}/vendor/shipments${tab === "all" ? "" : `/${tab}`}`
        : role === "PROCUREMENT"
          ? `${API}/procurement/shipments${tab === "all" ? "" : `/${tab}`}`
          : tab === "active"
            ? `${API}/shipments/active`
            : tab === "past"
              ? `${API}/shipments/past`
              : `${API}/shipments`;

    axios
      .get(url, {
        headers: { Authorization: `Bearer ${token}` },
        params: { page, size: pageSize, sort: "createdAt,desc" },
      })
      .then(res => {
        setShipments(res.data.content || []);
        setPageMeta(res.data);
        setLoading(false);
      })
      .catch(() => {
        setError("Failed to load shipments.");
        setLoading(false);
      });
  }, [tab, role, token, page, pageSize]);

  useEffect(() => {
    const initialLoad = window.setTimeout(fetchShipments, 0);
    return () => window.clearTimeout(initialLoad);
  }, [fetchShipments]);

  const cancelShipment = shipment => {
    const key = shipment.id;

    if (!confirmCancel[key]) {
      setConfirmCancel(prev => ({ ...prev, [key]: true }));
      return;
    }

    setActionLoading(prev => ({ ...prev, [key]: true }));

    axios
      .put(`${API}/shipments/${shipment.id}/cancel`, {}, { headers })
      .then(res => {
        setShipments(prev => prev.map(s => (s.id === shipment.id ? res.data : s)));

        setConfirmCancel(prev => {
          const n = { ...prev };
          delete n[key];
          return n;
        });

        setActionLoading(prev => ({ ...prev, [key]: false }));
      })
      .catch(() => {
        setConfirmCancel(prev => {
          const n = { ...prev };
          delete n[key];
          return n;
        });

        setActionLoading(prev => ({ ...prev, [key]: false }));
      });
  };

  const filtered = shipments.filter(s => {
    if (role === "VENDOR") {
      const shippingStatus = getVendorShippingStatus(s);

      if (tab === "active" && shippingStatus === "DELIVERED") return false;
      if (tab === "past" && shippingStatus !== "DELIVERED") return false;
      if (["CANCELLED", "REJECTED", "VENDOR_REJECTED", "BUYER_REJECTED"].includes(s.status)) return false;

      if (!search.trim()) return true;

      const q = search.toLowerCase();

      return (
        (s.orderId || "").toLowerCase().includes(q) ||
        (s.counterpartyName || "").toLowerCase().includes(q) ||
        (shippingStatus || "").toLowerCase().includes(q) ||
        (s.items || []).some(item => getItemName(item).toLowerCase().includes(q))
      );
    }

    if (role === "PROCUREMENT") {
      // Server already scopes the active/past buckets; here we only apply the search box.
      if (!search.trim()) return true;

      const q = search.toLowerCase();
      const names = Array.isArray(s.itemNames) ? s.itemNames.join(", ") : "";

      return (
        (s.orderId || "").toLowerCase().includes(q) ||
        (s.counterpartyName || "").toLowerCase().includes(q) ||
        (getVendorShippingStatus(s) || "").toLowerCase().includes(q) ||
        names.toLowerCase().includes(q)
      );
    }

    if (!search.trim()) return true;

    const q = search.toLowerCase();

    return (
      (s.shipmentId || "").toLowerCase().includes(q) ||
      (s.counterpartyName || "").toLowerCase().includes(q) ||
      (s.trackingNumber || "").toLowerCase().includes(q) ||
      (s.status || "").toLowerCase().includes(q)
    );
  });

  const pagedShipments = filtered;

  const thStyle = {
    padding: "14px 16px",
    textAlign: "center",
    color: "#334155",
    fontWeight: 700,
    fontSize: "13px",
    borderBottom: "1px solid #e2e8f0",
    whiteSpace: "nowrap",
  };

  const tdStyle = {
    padding: "14px 16px",
    textAlign: "center",
    color: "#475569",
    borderBottom: "1px solid #f1f5f9",
    verticalAlign: "middle",
  };

  return (
    <div style={{ padding: "32px", background: "#f8fafc", minHeight: "100vh" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "28px" }}>
        <div style={{ textAlign: "center", width: "100%" }}>
          <h1 style={{ fontSize: "26px", fontWeight: 800, color: "#0f172a", margin: 0, letterSpacing: "1px" }}>Shipments</h1>

          <p style={{ fontSize: "14px", color: "#64748b", margin: "6px 0 0" }}>
            {role === "VENDOR"
              ? "Outbound shipments — goods dispatched"
              : role === "PROCUREMENT"
                ? "Inbound shipments — goods incoming"
                : "All shipments — inbound and outbound"}
          </p>
        </div>

        {(role === "PROCUREMENT" || role === "MANAGER") && (
          <button
            onClick={() => setShowCreate(true)}
            style={{
              background: accentColor,
              color: "#fff",
              border: "none",
              borderRadius: "8px",
              padding: "10px 18px",
              fontSize: "13px",
              fontWeight: 700,
              cursor: "pointer",
              boxShadow: "0 8px 18px rgba(79, 70, 229, 0.22)",
            }}
          >
            {role === "PROCUREMENT" ? "Log Inbound Shipment" : "Create Shipment"}
          </button>
        )}
      </div>

      <div style={{ display: "flex", gap: "28px", borderBottom: "1px solid #e2e8f0", marginBottom: "22px" }}>
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => {
              setTab(t.key);
              setExpandedId(null);
              setPage(0);
            }}
            style={{
              padding: "10px 0",
              border: "none",
              background: "transparent",
              borderBottom: tab === t.key ? `3px solid ${accentColor}` : "3px solid transparent",
              color: tab === t.key ? accentColor : "#64748b",
              fontWeight: tab === t.key ? 800 : 500,
              fontSize: "15px",
              cursor: "pointer",
              marginBottom: "-1px",
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "20px" }}>
        <input
          placeholder="Search shipments..."
          value={search}
          onChange={e => {
            setSearch(e.target.value);
            setPage(0);
          }}
          style={{
            padding: "11px 14px",
            border: "1px solid #cbd5e1",
            borderRadius: "8px",
            fontSize: "14px",
            width: "280px",
            background: "#fff",
            color: "#0f172a",
            outline: "none",
          }}
        />
      </div>

      {loading ? (
        <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8" }}>Loading shipments...</div>
      ) : error ? (
        <div style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", padding: "16px", color: "#b91c1c" }}>
          {error}
        </div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8", fontSize: "15px" }}>
          {role === "VENDOR" ? "No confirmed shipments yet." : "No shipments found."}
        </div>
      ) : role === "VENDOR" ? (
        <VendorShipmentTable orders={pagedShipments} accentColor="#1d4ed8" />
      ) : role === "PROCUREMENT" ? (
        <VendorShipmentTable
          orders={pagedShipments}
          accentColor="#6d28d9"
          counterpartyLabel="Supplier"
          resolveStatus={o => o.shippingStatus || o.poStatus || o.status}
        />
      ) : (
        <div
          style={{
            background: "#fff",
            border: "1px solid #e2e8f0",
            borderRadius: "12px",
            padding: "16px",
            boxShadow: "0 8px 24px rgba(15, 23, 42, 0.04)",
          }}
        >
          <div style={{ overflowX: "auto" }}>
            <table
              style={{
                width: "100%",
                minWidth: "980px",
                borderCollapse: "collapse",
                fontSize: "14px",
                border: "1px solid #e2e8f0",
                borderRadius: "10px",
                overflow: "hidden",
              }}
            >
              <thead>
                <tr style={{ background: "#f8fafc" }}>
                  {[
                    "Shipment ID",
                    ...(role === "ADMIN" || role === "MANAGER" ? ["Direction"] : []),
                    role === "PROCUREMENT" ? "Supplier" : "Receiver",
                    "Carrier",
                    "Tracking",
                    "Route",
                    "Est. Delivery",
                    "Status",
                    "Actions",
                  ].map(h => (
                    <th key={h} style={thStyle}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>

              <tbody>
                {pagedShipments.map(shipment => {
                  const isExpanded = expandedId === shipment.id;
                  const canCancel = !["DELIVERED", "CANCELLED"].includes(shipment.status);
                  const canUpdateStatus = role !== "PROCUREMENT" && !["DELIVERED", "CANCELLED"].includes(shipment.status);
                  const isCancelConfirm = confirmCancel[shipment.id];

                  return (
                    <>
                      <tr
                        key={shipment.id}
                        style={{
                          background: isExpanded ? "#f8fafc" : "transparent",
                          cursor: "pointer",
                        }}
                        onClick={() => setExpandedId(isExpanded ? null : shipment.id)}
                      >
                        <td style={{ ...tdStyle, color: accentColor, fontWeight: 700, whiteSpace: "nowrap" }}>
                          {shipment.shipmentId || shipment.id}
                        </td>

                        {(role === "ADMIN" || role === "MANAGER") && (
                          <td style={tdStyle}>
                            <DirectionBadge direction={shipment.direction} />
                          </td>
                        )}

                        <td style={{ ...tdStyle, color: "#0f172a" }}>{shipment.counterpartyName || "-"}</td>
                        <td style={tdStyle}>{shipment.carrier || "-"}</td>

                        <td style={{ ...tdStyle, fontFamily: "monospace", fontSize: "12px" }}>
                          {shipment.trackingNumber || "-"}
                        </td>

                        <td style={{ ...tdStyle, whiteSpace: "nowrap" }}>
                          {shipment.origin && shipment.destination ? `${shipment.origin} → ${shipment.destination}` : "-"}
                        </td>

                        <td style={{ ...tdStyle, whiteSpace: "nowrap" }}>
                          {shipment.estimatedDelivery
                            ? new Date(shipment.estimatedDelivery).toLocaleDateString("en-IN")
                            : "-"}
                        </td>

                        <td style={tdStyle}>
                          <StatusBadge status={shipment.status} />
                        </td>

                        <td style={tdStyle} onClick={e => e.stopPropagation()}>
                          <div style={{ display: "flex", gap: "6px", alignItems: "center", justifyContent: "center", flexWrap: "nowrap" }}>
                            {canUpdateStatus && (
                              <button
                                onClick={() => setUpdateStatusId(updateStatusId === shipment.id ? null : shipment.id)}
                                style={{
                                  padding: "6px 10px",
                                  background: "#4f46e5",
                                  color: "#fff",
                                  border: "none",
                                  borderRadius: "7px",
                                  fontSize: "12px",
                                  fontWeight: 700,
                                  cursor: "pointer",
                                  whiteSpace: "nowrap",
                                }}
                              >
                                Update
                              </button>
                            )}

                            {canCancel &&
                              (isCancelConfirm ? (
                                <>
                                  <span style={{ fontSize: "12px", color: "#475569", whiteSpace: "nowrap" }}>Sure?</span>

                                  <button
                                    onClick={() => cancelShipment(shipment)}
                                    disabled={actionLoading[shipment.id]}
                                    style={{
                                      padding: "6px 10px",
                                      background: "#b91c1c",
                                      color: "#fff",
                                      border: "none",
                                      borderRadius: "7px",
                                      fontSize: "12px",
                                      fontWeight: 700,
                                      cursor: "pointer",
                                    }}
                                  >
                                    Yes
                                  </button>

                                  <button
                                    onClick={() =>
                                      setConfirmCancel(prev => {
                                        const n = { ...prev };
                                        delete n[shipment.id];
                                        return n;
                                      })
                                    }
                                    style={{
                                      padding: "6px 10px",
                                      background: "#fff",
                                      color: "#475569",
                                      border: "1px solid #e2e8f0",
                                      borderRadius: "7px",
                                      fontSize: "12px",
                                      cursor: "pointer",
                                    }}
                                  >
                                    No
                                  </button>
                                </>
                              ) : (
                                <button
                                  onClick={() => cancelShipment(shipment)}
                                  style={{
                                    padding: "6px 10px",
                                    background: "#fff",
                                    color: "#b91c1c",
                                    border: "1px solid #fecaca",
                                    borderRadius: "7px",
                                    fontSize: "12px",
                                    fontWeight: 700,
                                    cursor: "pointer",
                                    whiteSpace: "nowrap",
                                  }}
                                >
                                  Cancel
                                </button>
                              ))}
                          </div>
                        </td>
                      </tr>

                      {updateStatusId === shipment.id && (
                        <tr key={`status-${shipment.id}`}>
                          <td colSpan={99} style={{ padding: "0 16px 12px" }}>
                            <UpdateStatusPanel
                              shipment={shipment}
                              onDone={updated => setShipments(prev => prev.map(s => (s.id === updated.id ? updated : s)))}
                              onClose={() => setUpdateStatusId(null)}
                            />
                          </td>
                        </tr>
                      )}

                      {isExpanded && (
                        <tr key={`detail-${shipment.id}`} style={{ background: "#f8fafc" }}>
                          <td colSpan={99} style={{ padding: "16px 24px" }}>
                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "20px", fontSize: "13px" }}>
                              <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                <p style={{ fontSize: "11px", fontWeight: 800, color: "#94a3b8", margin: "0 0 4px" }}>SHIPMENT INFO</p>
                                <span><strong style={{ color: "#0f172a" }}>Linked Order:</strong> {shipment.orderId || "—"}</span>
                                <span><strong style={{ color: "#0f172a" }}>Origin:</strong> {shipment.origin || "—"}</span>
                                <span><strong style={{ color: "#0f172a" }}>Destination:</strong> {shipment.destination || "—"}</span>
                              </div>

                              <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                <p style={{ fontSize: "11px", fontWeight: 800, color: "#94a3b8", margin: "0 0 4px" }}>DELIVERY</p>
                                <span><strong style={{ color: "#0f172a" }}>Est. Delivery:</strong> {shipment.estimatedDelivery || "—"}</span>
                                <span><strong style={{ color: "#0f172a" }}>Actual Delivery:</strong> {shipment.actualDelivery || "—"}</span>
                                <span><strong style={{ color: "#0f172a" }}>Delay Reason:</strong> {shipment.delayReason || "—"}</span>
                              </div>

                              <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                <p style={{ fontSize: "11px", fontWeight: 800, color: "#94a3b8", margin: "0 0 4px" }}>NOTES</p>
                                <span style={{ color: "#475569" }}>{shipment.notes || "No notes."}</span>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <Pagination
          page={page}
          pageSize={pageSize}
          totalRecords={pageMeta.totalElements}
          totalPages={pageMeta.totalPages}
          first={pageMeta.first}
          last={pageMeta.last}
          onPageChange={setPage}
          onPageSizeChange={size => {
            setPageSize(size);
            setPage(0);
          }}
        />
      )}

      {showCreate && (
        <CreateShipmentModal
          role={role}
          onClose={() => setShowCreate(false)}
          onCreated={s => {
            setShipments(prev => [s, ...prev]);
            setTab("all");
          }}
        />
      )}
    </div>
  );
}