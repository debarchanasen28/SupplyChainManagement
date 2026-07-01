import { useState, useEffect, useCallback } from "react";
import axios from "../api/axios";
import Pagination from "../components/Pagination";

const API = "http://localhost:8080/api";
const STATUS_COLORS = {
  REQUESTED: { bg: "#eff6ff", text: "#1d4ed8" },
  STOCK_NOTIFIED: { bg: "#fef9c3", text: "#854d0e" },
  BUYER_APPROVED: { bg: "#ecfdf5", text: "#047857" },
  BUYER_REJECTED: { bg: "#fef2f2", text: "#b91c1c" },
  VENDOR_REJECTED: { bg: "#fef2f2", text: "#b91c1c" },
  CONFIRMED: { bg: "#f0fdf4", text: "#15803d" },
  PROCESSING: { bg: "#fff7ed", text: "#c2410c" },
  IN_TRANSIT: { bg: "#f5f3ff", text: "#6d28d9" },
  DELIVERED: { bg: "#f0fdf4", text: "#15803d" },
  CANCELLED: { bg: "#fef2f2", text: "#b91c1c" },
  REJECTED: { bg: "#fef2f2", text: "#b91c1c" },
};

const TAB_STATUS = {
  pending: ["REQUESTED", "STOCK_NOTIFIED"],
  buyerDecision: ["BUYER_APPROVED"],
  active: ["CONFIRMED", "PROCESSING", "IN_TRANSIT", "SHIPPED", "DELIVERED"],
  past: ["DELIVERED"],
  cancelled: ["CANCELLED", "VENDOR_REJECTED", "BUYER_REJECTED", "REJECTED"],
};

const CANCELLED_BY = {
  VENDOR: "SYSTEM1_VENDOR",
  PROCUREMENT: "SYSTEM1_PROCUREMENT",
  SYSTEM2_PROCUREMENT: "SYSTEM2_PROCUREMENT",
  SYSTEM_AUTO: "SYSTEM_AUTO",
};

const getCancelActor = (role) => {
  if (role === "VENDOR") return CANCELLED_BY.VENDOR;
  if (role === "PROCUREMENT") return CANCELLED_BY.PROCUREMENT;
  return CANCELLED_BY.SYSTEM_AUTO;
};

function StatusBadge({ status }) {
  const c = STATUS_COLORS[status] || { bg: "#f1f5f9", text: "#475569" };
  return (
    <span style={{
      display: "inline-block",
      background: c.bg,
      color: c.text,
      border: `1px solid ${c.text}22`,
      borderRadius: "999px",
      padding: "5px 11px",
      fontSize: "11px",
      fontWeight: 800,
      whiteSpace: "nowrap"
    }}>
      {(status || "-").replace(/_/g, " ")}
    </span>
  );
}

function DirectionBadge({ direction }) {
  return (
    <span style={{
      background: direction === "OUTBOUND" ? "#eff6ff" : "#f5f3ff",
      color: direction === "OUTBOUND" ? "#1d4ed8" : "#6d28d9",
      borderRadius: "20px",
      padding: "2px 10px",
      fontSize: "12px",
      fontWeight: 600
    }}>
      {direction}
    </span>
  );
}

const loadAvailability = async (orderId) => {
  try {
    const res = await axios.get(
      `${API}/orders/${orderId}/availability`,
      { headers }
    );

    setAvailability(prev => ({
      ...prev,
      [orderId]: res.data
    }));
  } catch (err) {
    console.error(err);
  }
};

const emptyItem = { productName: "", quantity: 1, unitPrice: 0 };
// Procurement PO lines reference a System2 Vendor SKU; price/name/unit come from inventory.
const emptyProcItem = { sku: "", quantity: 1 };

function CreateOrderModal({ onClose, onCreated, role }) {
  const isProcurement = role === "PROCUREMENT";

  const [form, setForm] = useState({
    counterpartyId: "",
    counterpartyName: "",
    expectedDeliveryDate: "",
    notes: ""
  });

  const [items, setItems] = useState([
    isProcurement ? { ...emptyProcItem } : { ...emptyItem }
  ]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  // System2 Vendor inventory (read-only) for the procurement dropdown.
  const [vendorInventory, setVendorInventory] = useState([]);
  const [invLoading, setInvLoading] = useState(false);

  const token = localStorage.getItem("token");

  useEffect(() => {
    if (!isProcurement) return;
    setInvLoading(true);
    axios.get(`${API}/procurement/system2-vendor-inventory`, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(res => setVendorInventory(Array.isArray(res.data) ? res.data : []))
      .catch(() => setError("Could not load System2 Vendor inventory."))
      .finally(() => setInvLoading(false));
  }, [isProcurement, token]);

  const invBySku = (sku) => vendorInventory.find(v => v.sku === sku) || null;

  const updateItem = (i, field, val) => {
    setItems(prev => prev.map((it, idx) => idx === i ? { ...it, [field]: val } : it));
  };

  const addItem = () =>
    setItems(prev => [...prev, isProcurement ? { ...emptyProcItem } : { ...emptyItem }]);

  const removeItem = (i) => {
    setItems(prev => prev.filter((_, idx) => idx !== i));
  };

  // Line total: procurement uses the fixed inventory price; vendor uses the entered price.
  const lineTotalOf = (it) => {
    const price = isProcurement
      ? Number(invBySku(it.sku)?.unitPrice || 0)
      : Number(it.unitPrice || 0);
    return Number(it.quantity || 0) * price;
  };

  const totalAmount = items.reduce((sum, it) => sum + lineTotalOf(it), 0);

  const submit = () => {
    if (!form.counterpartyName.trim()) {
      setError("Counterparty name is required.");
      return;
    }

    if (isProcurement) {
      if (items.some(it => !it.sku)) {
        setError("Select an item for every line.");
        return;
      }
      if (items.some(it => Number(it.quantity) <= 0)) {
        setError("Quantity must be greater than 0 for every line.");
        return;
      }
    } else if (items.some(it => !it.productName.trim())) {
      setError("All items need a product name.");
      return;
    }

    setSaving(true);
    setError("");

    // For procurement, send only sku + quantity; the backend prices the line from System2
    // inventory (frontend price is never trusted). For vendor, keep the existing payload.
    const payloadItems = isProcurement
      ? items.map(it => ({ sku: it.sku, quantity: Number(it.quantity) }))
      : items.map(it => ({
          ...it,
          description: it.productName,
          quantity: Number(it.quantity),
          unitPrice: Number(it.unitPrice)
        }));

    axios.post(`${API}/orders`, { ...form, items: payloadItems }, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(res => {
        onCreated(res.data);
        onClose();
      })
      .catch(err => {
        setError(
          err.response?.data?.error ||
          err.response?.data?.message ||
          "Failed to create order."
        );
        setSaving(false);
      });
  };

  const inputStyle = {
    width: "100%",
    padding: "8px 10px",
    borderRadius: "6px",
    border: "1px solid #e2e8f0",
    fontSize: "14px",
    background: "#fff",
    color: "#0f172a",
    boxSizing: "border-box"
  };

  return (
    <div style={{
      position: "fixed",
      inset: 0,
      background: "rgba(0,0,0,0.4)",
      zIndex: 1000,
      display: "flex",
      alignItems: "center",
      justifyContent: "center"
    }}>
      <div style={{
        background: "#fff",
        borderRadius: "12px",
        padding: "28px",
        width: "620px",
        maxHeight: "90vh",
        overflowY: "auto",
        boxShadow: "0 20px 60px rgba(0,0,0,0.15)"
      }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
          <h2 style={{ fontSize: "17px", fontWeight: 700, color: "#0f172a", margin: 0 }}>
            {role === "PROCUREMENT" ? "Create Purchase Order" : "Create Outbound Order"}
          </h2>
          <button
            onClick={onClose}
            style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
          >
            x
          </button>
        </div>

        {error && (
          <div style={{
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "6px",
            padding: "10px 14px",
            color: "#b91c1c",
            fontSize: "13px",
            marginBottom: "16px"
          }}>
            {error}
          </div>
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px", marginBottom: "16px" }}>
          <div>
            <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569", display: "block", marginBottom: "4px" }}>
              {role === "PROCUREMENT" ? "Supplier Name" : "Buyer Name"} *
            </label>
            <input
              style={inputStyle}
              placeholder="Counterparty name"
              value={form.counterpartyName}
              onChange={e => setForm(f => ({ ...f, counterpartyName: e.target.value }))}
            />
          </div>

          <div>
            <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569", display: "block", marginBottom: "4px" }}>
              {role === "PROCUREMENT" ? "Supplier ID" : "Buyer ID"}
            </label>
            <input
              style={inputStyle}
              placeholder="Optional"
              value={form.counterpartyId}
              onChange={e => setForm(f => ({ ...f, counterpartyId: e.target.value }))}
            />
          </div>

          <div>
            <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569", display: "block", marginBottom: "4px" }}>
              Expected Delivery Date
            </label>
            <input
              type="date"
              style={inputStyle}
              value={form.expectedDeliveryDate}
              onChange={e => setForm(f => ({ ...f, expectedDeliveryDate: e.target.value }))}
            />
          </div>

          <div>
            <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569", display: "block", marginBottom: "4px" }}>
              Notes
            </label>
            <input
              style={inputStyle}
              placeholder="Optional"
              value={form.notes}
              onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
            />
          </div>
        </div>

        <div style={{ marginBottom: "16px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
            <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569" }}>Order Items *</label>
            <button
              onClick={addItem}
              style={{
                background: "none",
                border: "1px solid #e2e8f0",
                borderRadius: "6px",
                padding: "4px 10px",
                fontSize: "12px",
                color: "#475569",
                cursor: "pointer"
              }}
            >
              + Add Item
            </button>
          </div>

          <div style={{ display: "flex", gap: "8px", marginBottom: "6px" }}>
            {(isProcurement
              ? ["Item (System2 Vendor)", "Unit Price", "Qty", "Line Total"]
              : ["Product Name", "Qty", "Unit Price"]
            ).map(h => (
              <div
                key={h}
                style={{
                  flex: h.startsWith("Item") || h === "Product Name" ? 2 : 1,
                  fontSize: "11px",
                  fontWeight: 600,
                  color: "#94a3b8"
                }}
              >
                {h}
              </div>
            ))}
            <div style={{ width: "28px" }} />
          </div>

          {isProcurement && invLoading && (
            <div style={{ fontSize: "12px", color: "#94a3b8", marginBottom: "8px" }}>
              Loading System2 Vendor inventory…
            </div>
          )}

          {items.map((it, i) => {
            if (isProcurement) {
              const inv = invBySku(it.sku);
              const unitPrice = Number(inv?.unitPrice || 0);
              const lineTotal = unitPrice * Number(it.quantity || 0);
              return (
                <div key={i} style={{ display: "flex", gap: "8px", marginBottom: "8px", alignItems: "center" }}>
                  <select
                    style={{ ...inputStyle, flex: 2 }}
                    value={it.sku}
                    onChange={e => updateItem(i, "sku", e.target.value)}
                  >
                    <option value="">Select item…</option>
                    {vendorInventory.map(v => (
                      <option key={v.sku} value={v.sku}>
                        {v.itemName} · {v.sku} · {v.availableQuantity} {v.unit || ""} avail.
                      </option>
                    ))}
                  </select>
                  <input
                    readOnly
                    style={{ ...inputStyle, flex: 1, background: "#f8fafc", color: "#475569" }}
                    value={inv ? `₹${unitPrice.toFixed(2)}` : "—"}
                  />
                  <input
                    type="number"
                    style={{ ...inputStyle, flex: 1 }}
                    placeholder="1"
                    min="1"
                    value={it.quantity}
                    onChange={e => updateItem(i, "quantity", e.target.value)}
                  />
                  <input
                    readOnly
                    style={{ ...inputStyle, flex: 1, background: "#f8fafc", color: "#0f172a", fontWeight: 600 }}
                    value={inv ? `₹${lineTotal.toFixed(2)}` : "—"}
                  />
                  <button
                    onClick={() => removeItem(i)}
                    disabled={items.length === 1}
                    style={{
                      background: "none",
                      border: "none",
                      color: items.length === 1 ? "#cbd5e1" : "#b91c1c",
                      cursor: items.length === 1 ? "default" : "pointer",
                      fontSize: "16px",
                      padding: "0 4px"
                    }}
                  >
                    x
                  </button>
                </div>
              );
            }
            return (
              <div key={i} style={{ display: "flex", gap: "8px", marginBottom: "8px", alignItems: "center" }}>
                <input
                  style={{ ...inputStyle, flex: 2 }}
                  placeholder="Product name"
                  value={it.productName}
                  onChange={e => updateItem(i, "productName", e.target.value)}
                />
                <input
                  type="number"
                  style={{ ...inputStyle, flex: 1 }}
                  placeholder="1"
                  min="1"
                  value={it.quantity}
                  onChange={e => updateItem(i, "quantity", e.target.value)}
                />
                <input
                  type="number"
                  style={{ ...inputStyle, flex: 1 }}
                  placeholder="0.00"
                  min="0"
                  step="0.01"
                  value={it.unitPrice}
                  onChange={e => updateItem(i, "unitPrice", e.target.value)}
                />
                <button
                  onClick={() => removeItem(i)}
                  disabled={items.length === 1}
                  style={{
                    background: "none",
                    border: "none",
                    color: items.length === 1 ? "#cbd5e1" : "#b91c1c",
                    cursor: items.length === 1 ? "default" : "pointer",
                    fontSize: "16px",
                    padding: "0 4px"
                  }}
                >
                  x
                </button>
              </div>
            );
          })}

          <div style={{ textAlign: "right", fontSize: "14px", fontWeight: 600, color: "#0f172a", marginTop: "8px" }}>
            Total: ₹{totalAmount.toLocaleString("en-IN", {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2
            })}
          </div>
        </div>

        <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end" }}>
          <button
            onClick={onClose}
            style={{
              padding: "8px 18px",
              border: "1px solid #e2e8f0",
              borderRadius: "8px",
              background: "#fff",
              color: "#475569",
              fontSize: "14px",
              cursor: "pointer"
            }}
          >
            Cancel
          </button>

          <button
            onClick={submit}
            disabled={saving}
            style={{
              padding: "8px 18px",
              border: "none",
              borderRadius: "8px",
              background: "#0f172a",
              color: "#fff",
              fontSize: "14px",
              fontWeight: 600,
              cursor: saving ? "default" : "pointer",
              opacity: saving ? 0.7 : 1
            }}
          >
            {saving ? "Creating..." : "Create Order"}
          </button>
        </div>
      </div>
    </div>
  );
}

function StockNotifyPanel({ order, availabilityLines, onDone, onClose }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const token = localStorage.getItem("token");

  const totalRequired = availabilityLines?.reduce(
    (sum, line) => sum + Number(line.required || 0),
    0
  ) || 0;

  const totalAvailable = availabilityLines?.reduce(
    (sum, line) => sum + Number(line.available || 0),
    0
  ) || 0;

  const offerQty = Math.min(totalRequired, totalAvailable);
  const canOffer = offerQty > 0;
  const isFullSupply = canOffer && totalAvailable >= totalRequired;
  const system2Accepts = isFullSupply;

  const submitOffer = () => {
    setSaving(true);
    setError("");

    axios.put(`${API}/orders/${order.id}/notify-stock`, {
      availableQuantity: system2Accepts ? offerQty : 0
    }, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(res => {
        const notifiedOrder = res.data || order;
        const followUp = system2Accepts
          ? axios.patch(`${API}/orders/${order.id}/status?status=CONFIRMED`, {
              buyerResponse: "YES"
            }, {
              headers: { Authorization: `Bearer ${token}` }
            })
          : axios.put(`${API}/orders/${order.id}/cancel`, {
              buyerResponse: "NO",
              cancelledBy: CANCELLED_BY.SYSTEM2_PROCUREMENT
            }, {
              headers: { Authorization: `Bearer ${token}` }
            });

        return followUp.then(finalRes => finalRes.data || notifiedOrder);
      })
      .then(updatedOrder => {
        onDone(updatedOrder);
        onClose();
      })
      .catch(err => {
        setError(err.response?.data?.message || "Failed to send stock availability.");
        setSaving(false);
      });
  };

  return (
    <div style={{
      background: "#f8fafc",
      border: "1px solid #e2e8f0",
      borderRadius: "10px",
      padding: "16px",
      marginTop: "12px"
    }}>
      <p style={{ fontSize: "13px", color: "#475569", margin: "0 0 8px" }}>
        Send stock offer to buyer for <strong>{order.orderId}</strong>.
      </p>

      <div style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: "8px",
        padding: "10px 12px",
        marginBottom: "10px",
        fontSize: "13px",
        color: "#475569"
      }}>
        <div>
          Required Quantity: <strong>{totalRequired}</strong>
        </div>
        <div>
          Available in Inventory: <strong>{totalAvailable}</strong>
        </div>
        <div>
          Offer Quantity: <strong>{offerQty}</strong>
        </div>
        <div style={{ marginTop: "6px", fontWeight: 700, color: isFullSupply ? "#15803d" : canOffer ? "#d97706" : "#b91c1c" }}>
          {isFullSupply
            ? "System2 auto-response: YES"
            : canOffer
              ? "System2 auto-response: NO"
              : "System2 auto-response: NO"}
        </div>
      </div>

      {error && (
        <div style={{ color: "#b91c1c", fontSize: "13px", marginBottom: "8px" }}>
          {error}
        </div>
      )}

      <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
        <button
          onClick={submitOffer}
          disabled={saving}
          style={{
            padding: "8px 14px",
            background: isFullSupply ? "#15803d" : "#b91c1c",
            color: "#fff",
            border: "none",
            borderRadius: "6px",
            fontSize: "13px",
            fontWeight: 600,
            cursor: saving ? "default" : "pointer"
          }}
        >
          {saving
            ? "Sending..."
            : isFullSupply
              ? "Send Availability"
              : "Send No Stock"}
        </button>

        <button
          onClick={onClose}
          style={{
            padding: "8px 14px",
            background: "#fff",
            color: "#475569",
            border: "1px solid #e2e8f0",
            borderRadius: "6px",
            fontSize: "13px",
            cursor: "pointer"
          }}
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState("all");
  const [showCreate, setShowCreate] = useState(false);
  const [expandedId, setExpandedId] = useState(null);
  const [notifyOrderId, setNotifyOrderId] = useState(null);
  const [confirmState, setConfirmState] = useState({});
  const [search, setSearch] = useState("");
  const [actionLoading, setActionLoading] = useState({});
  const [availability, setAvailability] = useState({});
  const [inventoryContext, setInventoryContext] = useState({});
  const [sortBy, setSortBy] = useState("dateDesc");
  const [statusFilter, setStatusFilter] = useState("all");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalRecords, setTotalRecords] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [debouncedSearch, setDebouncedSearch] = useState("");

  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role");

  const headers = { Authorization: `Bearer ${token}` };

  const TABS = [
    { key: "all", label: "All Orders" },
    { key: "pending", label: "Pending Approval" },
    // Buyer Decision is a vendor-only view; procurement just creates POs and tracks status.
    ...(role === "PROCUREMENT" ? [] : [{ key: "buyerDecision", label: "Buyer Decision" }]),
    { key: "active", label: "Active" },
    { key: "past", label: "Past" },
    { key: "cancelled", label: "Cancelled" },
  ];

  // Debounce the search box so we don't hit the server on every keystroke.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 350);
    return () => clearTimeout(t);
  }, [search]);

  // Any tab/filter/sort/search change resets to the first page.
  useEffect(() => {
    setPage(0);
  }, [tab, statusFilter, sortBy, debouncedSearch]);

  const fetchOrders = useCallback(() => {
    setLoading(true);
    setError("");

    const apiTab = tab === "past"
      ? "delivered"
      : tab === "buyerDecision"
        ? "buyer-decision"
        : tab;
    const params = new URLSearchParams({
      tab: apiTab,
      status: statusFilter,
      q: debouncedSearch,
      sort: sortBy,
      page: String(page),
      size: String(pageSize),
    });

    axios.get(`${API}/orders/paged?${params.toString()}`, { headers })
      .then(res => {
        setOrders(res.data.content || []);
        setTotalPages(res.data.totalPages || 1);
        setTotalRecords(res.data.totalElements || 0);
        setLoading(false);
      })
      .catch(() => {
        setError("Failed to load orders.");
        setLoading(false);
      });
  }, [tab, statusFilter, debouncedSearch, sortBy, page, pageSize]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  const loadAvailability = (order) => {
    if (role !== "VENDOR") return;

    axios.get(`${API}/orders/${order.id}/availability`, { headers })
      .then(res => {
        setAvailability(prev => ({
          ...prev,
          [order.id]: res.data
        }));
      })
      .catch(() => {});
  };

  const handleExpand = (order, isExpanded) => {
    if (isExpanded) {
      setExpandedId(null);
      return;
    }

    setExpandedId(order.id);
    loadAvailability(order);
    loadInventoryContext(order);
  };

  // Read-only inventory context for the vendor's expanded view (no mutation, no flow triggered).
  const loadInventoryContext = (order) => {
    if (role !== "VENDOR") return;
    const first = Array.isArray(order.items) ? order.items[0] : null;
    const itemName = first?.description || first?.productName || first?.itemName;
    const sku = first?.sku;
    if (!itemName && !sku) return;
    axios.get(`${API}/inventory/context`, { headers, params: { itemName, sku } })
      .then(res => setInventoryContext(prev => ({ ...prev, [order.id]: res.data })))
      .catch(err => { console.warn("inventory-context lookup failed", err?.response?.status, err?.message); });
  };

  const cancelOrder = (order) => {
    const key = order.id;
    const state = confirmState[key];

    if (!state) {
      setConfirmState(prev => ({ ...prev, [key]: { type: "cancel", step: "confirm" } }));
      return;
    }

    if (state.step === "confirm") {
      setActionLoading(prev => ({ ...prev, [key]: true }));

      const request = role === "VENDOR"
        ? axios.patch(
            `${API}/vendor/orders/${encodeURIComponent(order.orderId || order.correlationId || order.id)}/cancel`,
            {},
            { headers }
          )
        : axios.put(`${API}/orders/${order.id}/cancel`, {
            cancelledBy: getCancelActor(role)
          }, { headers });

      request
        .then(() => {
          setConfirmState(prev => {
            const n = { ...prev };
            delete n[key];
            return n;
          });
          setActionLoading(prev => ({ ...prev, [key]: false }));
          fetchOrders();
        })
        .catch(err => {
          console.error("Vendor/order cancellation failed:", err);
          setError(err.response?.data?.error || err.response?.data?.message || "Failed to cancel order.");
          setConfirmState(prev => {
            const n = { ...prev };
            delete n[key];
            return n;
          });
          setActionLoading(prev => ({ ...prev, [key]: false }));
        });
    }
  };

  // System1 Procurement POs are auto-dispatched to the System2 Vendor via iFlow1 on creation —
  // there is no manual "Send to Vendor" step. The System2 Vendor's accept/reject decision is
  // applied automatically by the backend (System1ProcurementDecisionWatcher).

  // Vendor (Lane A) actions over CPI iFlow 3.
  const vendorAction = (order, path, errMsg) => {
    const key = `${path}-${order.id}`;
    setActionLoading(prev => ({ ...prev, [key]: true }));
    axios.put(`${API}/orders/${order.id}/${path}`, {}, { headers })
      .then(() => {
        setActionLoading(prev => ({ ...prev, [key]: false }));
        fetchOrders();
      })
      .catch(err => {
        console.error(`${errMsg} Order:`, order.orderId || order.correlationId || order.id, err);
        setError(errMsg);
        setActionLoading(prev => ({ ...prev, [key]: false }));
      });
  };

  const pendingVendorAction = (order, action, errMsg) => {
    const key = `vendor-${action}-${order.id}`;
    const orderReference = order.orderId || order.correlationId || order.id;

    setActionLoading(prev => ({ ...prev, [key]: true }));
    setError("");

    axios.patch(
      `${API}/vendor/orders/${encodeURIComponent(orderReference)}/${action}`,
      action === "reject" ? { rejectionReason: "Rejected by vendor" } : {},
      { headers }
    )
      .then(() => fetchOrders())
      .catch(err => {
        console.error(`${errMsg} Order:`, orderReference, err);
        setError(err.response?.data?.error || err.response?.data?.message || errMsg);
      })
      .finally(() => {
        setActionLoading(prev => ({ ...prev, [key]: false }));
      });
  };

  const sendStockOffer = (order) => vendorAction(order, "send-offer", "Failed to send stock offer.");
  const rejectVendorOrder = (order) => pendingVendorAction(order, "reject", "Failed to reject order.");
  const cancelVendorOrder = (order) => {
    const key = `cancel-vendor-${order.id}`;
    setActionLoading(prev => ({ ...prev, [key]: true }));
    axios.post(`${API}/orders/${order.id}/cancel-vendor`, {}, { headers })
      .then(() => fetchOrders())
      .catch(err => {
        console.error("Failed to cancel buyer-approved order:", err);
        setError(err.response?.data?.error || err.response?.data?.message || "Failed to cancel order.");
      })
      .finally(() => setActionLoading(prev => ({ ...prev, [key]: false })));
  };
  const confirmSupply = (order) => vendorAction(order, "confirm-supply", "Failed to confirm supply.");

  const getTotalQuantity = (order) => {
    return order.items?.reduce((sum, item) => sum + Number(item.quantity || 0), 0) || 0;
  };

  // Resolve a display name for an order line across the various shapes the API may return.
  const getItemLabel = (item) =>
    item.productName ||
    item.description ||
    item.itemName ||
    item.name ||
    item.sku ||
    "Item";

  // "Paracetamol 500mg × 50, Ibuprofen 400mg × 30" — falls back to plain name when qty is absent.
  const getItemSummary = (order) => {
    const list = Array.isArray(order.items) ? order.items : [];
    return list.map((it) => {
      const name = getItemLabel(it);
      const qty = Number(it.quantity || 0);
      return qty > 0 ? `${name} × ${qty}` : name;
    });
  };

  const classifyTab = (order) => {
    if (tab === "all") return true; // All Orders — no client-side status bucketing
    return (TAB_STATUS[tab] || []).includes(order.status);
  };

  // The server already applies tenant/role/tab/status/search/sort + pagination,
  // and this local pass keeps the tab buckets exact if an older API returns more.
  const filtered = orders.filter(classifyTab);

  const accentColor =
    role === "VENDOR"
      ? "#1d4ed8"
      : role === "PROCUREMENT"
        ? "#6d28d9"
        : "#0f766e";

  // Shared cell styles — aligned with the Inventory / Shipments pages.
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
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "24px"
      }}>
        <div style={{ textAlign: "center", width: "100%" }}>
          <h1 style={{ fontSize: "26px", fontWeight: 800, color: "#0f172a", margin: 0, letterSpacing: "1px" }}>
            Orders
          </h1>

          <p style={{ fontSize: "14px", color: "#64748b", margin: "6px 0 0" }}>
            {role === "VENDOR"
              ? "Outbound orders — goods you supply"
              : role === "PROCUREMENT"
                ? "Inbound orders — goods you procure"
                : "All orders — inbound and outbound"}
          </p>
        </div>

        {(role === "VENDOR" || role === "PROCUREMENT") && (
          <button
            onClick={() => setShowCreate(true)}
            style={{
              background: accentColor,
              color: "#fff",
              border: "none",
              borderRadius: "8px",
              padding: "9px 18px",
              fontSize: "13px",
              fontWeight: 600,
              cursor: "pointer"
            }}
          >
            {role === "PROCUREMENT" ? "New Purchase Order" : "New Order"}
          </button>
        )}
      </div>

      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "20px",
        gap: "12px"
      }}>
        <div style={{ display: "flex", gap: "4px", borderBottom: "1px solid #e2e8f0", flexWrap: "wrap" }}>
          {TABS.map(t => (
            <button
              key={t.key}
              onClick={() => {
                setTab(t.key);
                setExpandedId(null);
                setNotifyOrderId(null);
              }}
              style={{
                padding: "8px 16px",
                border: "none",
                background: "transparent",
                borderBottom: tab === t.key ? `2px solid ${accentColor}` : "2px solid transparent",
                color: tab === t.key ? accentColor : "#64748b",
                fontWeight: tab === t.key ? 600 : 500,
                fontSize: "14px",
                cursor: "pointer",
                marginBottom: "-1px"
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
          <input
            placeholder="Search orders..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{
              padding: "10px 14px",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
              fontSize: "14px",
              width: "240px",
              background: "#fff",
              color: "#0f172a",
              outline: "none"
            }}
          />

          <select
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}
            style={{
              padding: "10px 14px",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
              fontSize: "14px",
              background: "#fff",
              color: "#0f172a",
              outline: "none"
            }}
          >
            <option value="all">All Statuses</option>
            <option value="REQUESTED">Requested</option>
            <option value="STOCK_NOTIFIED">Pending Buyer Confirmation</option>
            {role !== "PROCUREMENT" && (
              <>
                <option value="BUYER_APPROVED">Buyer Approved</option>
                <option value="BUYER_REJECTED">Buyer Rejected</option>
              </>
            )}
            <option value="CONFIRMED">Confirmed</option>
            <option value="IN_TRANSIT">In Transit</option>
            <option value="DELIVERED">Delivered</option>
            <option value="VENDOR_REJECTED">Vendor Rejected</option>
            <option value="CANCELLED">Cancelled</option>
          </select>

          <select
            value={sortBy}
            onChange={e => setSortBy(e.target.value)}
            style={{
              padding: "10px 14px",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
              fontSize: "14px",
              background: "#fff",
              color: "#0f172a",
              outline: "none"
            }}
          >
            <option value="dateDesc">Newest First</option>
            <option value="dateAsc">Oldest First</option>
            <option value="alphaAsc">Order ID A-Z</option>
            <option value="alphaDesc">Order ID Z-A</option>
            <option value="qtyDesc">Quantity High-Low</option>
            <option value="qtyAsc">Quantity Low-High</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8" }}>
          Loading orders...
        </div>
      ) : error ? (
        <div style={{
          background: "#fef2f2",
          border: "1px solid #fecaca",
          borderRadius: "8px",
          padding: "16px",
          color: "#b91c1c"
        }}>
          {error}
        </div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8", fontSize: "15px" }}>
          No orders found.
        </div>
      ) : (
        <div style={{
          background: "#fff",
          border: "1px solid #e2e8f0",
          borderRadius: "12px",
          padding: "16px",
          boxShadow: "0 8px 24px rgba(15, 23, 42, 0.04)"
        }}>
          <div style={{ overflowX: "auto" }}>
            <table style={{
              width: "100%",
              minWidth: "980px",
              borderCollapse: "collapse",
              fontSize: "14px",
              border: "1px solid #e2e8f0",
              borderRadius: "10px",
              overflow: "hidden"
            }}>
              <thead>
                <tr style={{ background: "#f8fafc" }}>
                  {[
                    "Order ID",
                    ...(role === "ADMIN" || role === "MANAGER" ? ["Direction"] : []),
                    role === "PROCUREMENT" ? "Supplier" : "Buyer",
                    "Items",
                    "Quantity",
                    "Amount",
                    "Status",
                    ...(tab === "cancelled" ? ["Cancelled By"] : []),
                    "Created At",
                    "Resolved At",
                    "Actions"
                  ].map(h => (
                    <th key={h} style={thStyle}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>

              <tbody>
                {filtered.map(order => {
                  const cancelKey = order.id;
                  const cancelConfirm = confirmState[cancelKey];
                  const canCancel = ![
                    "DELIVERED",
                    "CANCELLED",
                    "VENDOR_REJECTED",
                    "BUYER_REJECTED",
                    "BUYER_APPROVED"
                  ].includes(order.status);
                  const vendorRequested = role === "VENDOR" && order.status === "REQUESTED";
                  const vendorAwaitingBuyer = role === "VENDOR" && order.status === "STOCK_NOTIFIED"
                    && order.buyerResponse !== "YES";
                  const vendorBuyerAccepted = role === "VENDOR" && order.status === "STOCK_NOTIFIED"
                    && order.buyerResponse === "YES";
                  const buyerApproved = role === "VENDOR" && order.status === "BUYER_APPROVED";
                  const buyerRejected = role === "VENDOR" && order.status === "BUYER_REJECTED";
                  // System1 Procurement: PO is auto-sent to the System2 Vendor on creation;
                  // while REQUESTED it is simply awaiting the System2 Vendor's decision.
                  const awaitingVendor = role === "PROCUREMENT" && order.status === "REQUESTED";
                  const isExpanded = expandedId === order.id;
                  const availabilityLines = availability[order.id] || [];

                  return (
                    <>
                      <tr
                        key={order.id}
                        style={{
                          borderBottom: "1px solid #f1f5f9",
                          background: isExpanded ? "#f8fafc" : "transparent",
                          cursor: "pointer"
                        }}
                        onClick={() => handleExpand(order, isExpanded)}
                      >
                        <td style={{ ...tdStyle, color: accentColor, fontWeight: 700, whiteSpace: "nowrap" }}>
                          {order.orderId || order.id}
                        </td>

                        {(role === "ADMIN" || role === "MANAGER") && (
                          <td style={tdStyle}>
                            <DirectionBadge direction={order.direction} />
                          </td>
                        )}

                        <td style={{ ...tdStyle, color: "#0f172a" }}>
                          {order.counterpartyName || "-"}
                        </td>

                        <td style={{ ...tdStyle, textAlign: "left", maxWidth: "280px" }}>
                          {(() => {
                            const count = order.items?.length ?? 0;
                            const summary = getItemSummary(order);
                            return (
                              <>
                                <div style={{ fontWeight: 700, color: "#0f172a" }}>
                                  {count} item{count !== 1 ? "s" : ""}
                                </div>
                                {summary.length > 0 && (
                                  <div style={{ fontSize: "12px", color: "#64748b", lineHeight: 1.5, marginTop: "4px" }}>
                                    {summary.join(", ")}
                                  </div>
                                )}
                              </>
                            );
                          })()}
                        </td>

                        <td style={{ ...tdStyle, fontWeight: 700 }}>
                          {getTotalQuantity(order)}
                        </td>

                        <td style={{ ...tdStyle, color: "#0f172a", fontWeight: 700, whiteSpace: "nowrap" }}>
                          ₹{(order.totalAmount ?? 0).toLocaleString("en-IN", {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2
                          })}
                        </td>

                        <td style={tdStyle}>
                          <StatusBadge status={order.status} />
                        </td>

                        {tab === "cancelled" && (
                          <td style={{ ...tdStyle, color: "#b91c1c", fontWeight: 700, whiteSpace: "nowrap" }}>
                            {order.cancelledBy || "Unknown"}
                          </td>
                        )}

                        <td style={{ ...tdStyle, color: "#64748b", whiteSpace: "nowrap" }}>
                          {order.createdAt ? new Date(order.createdAt).toLocaleString("en-IN"): "-"}
                        </td>

                        <td style={{ ...tdStyle, color: "#64748b", whiteSpace: "nowrap" }}>
                          {order.resolvedAt ? new Date(order.resolvedAt).toLocaleString("en-IN"): "-"}
                        </td>

                        <td style={tdStyle} onClick={e => e.stopPropagation()}>
                          <div style={{ display: "flex", gap: "6px", flexWrap: "nowrap", alignItems: "center" }}>
                            {awaitingVendor && (
                              <span style={{ fontSize: "12px", color: "#6d28d9", fontWeight: 600, whiteSpace: "nowrap" }}>
                                Awaiting System2 Vendor Response
                              </span>
                            )}

                            {vendorRequested && (
                              <>
                                <button
                                  onClick={() => sendStockOffer(order)}
                                  disabled={actionLoading[`send-offer-${order.id}`]}
                                  style={{
                                    padding: "4px 10px",
                                    background: "#1d4ed8",
                                    color: "#fff",
                                    border: "none",
                                    borderRadius: "6px",
                                    fontSize: "12px",
                                    fontWeight: 600,
                                    cursor: actionLoading[`send-offer-${order.id}`] ? "default" : "pointer",
                                    whiteSpace: "nowrap",
                                    opacity: actionLoading[`send-offer-${order.id}`] ? 0.7 : 1
                                  }}
                                >
                                  {actionLoading[`send-offer-${order.id}`] ? "Sending..." : "Send Stock Offer"}
                                </button>
                                <button
                                  onClick={() => rejectVendorOrder(order)}
                                  disabled={actionLoading[`vendor-reject-${order.id}`]}
                                  style={{
                                    padding: "4px 10px",
                                    background: "#fff",
                                    color: "#b91c1c",
                                    border: "1px solid #fecaca",
                                    borderRadius: "6px",
                                    fontSize: "12px",
                                    fontWeight: 600,
                                    cursor: actionLoading[`vendor-reject-${order.id}`] ? "default" : "pointer",
                                    whiteSpace: "nowrap"
                                  }}
                                >
                                  {actionLoading[`vendor-reject-${order.id}`] ? "Rejecting..." : "Reject"}
                                </button>
                              </>
                            )}

                            {vendorAwaitingBuyer && (
                              <span style={{ fontSize: "12px", color: "#854d0e", fontWeight: 600, whiteSpace: "nowrap" }}>
                                Offer sent · awaiting buyer
                              </span>
                            )}

                            {vendorBuyerAccepted && (
                              <button
                                onClick={() => confirmSupply(order)}
                                disabled={actionLoading[`confirm-supply-${order.id}`]}
                                style={{
                                  padding: "4px 10px",
                                  background: "#15803d",
                                  color: "#fff",
                                  border: "none",
                                  borderRadius: "6px",
                                  fontSize: "12px",
                                  fontWeight: 600,
                                  cursor: actionLoading[`confirm-supply-${order.id}`] ? "default" : "pointer",
                                  whiteSpace: "nowrap",
                                  opacity: actionLoading[`confirm-supply-${order.id}`] ? 0.7 : 1
                                }}
                              >
                                {actionLoading[`confirm-supply-${order.id}`] ? "Confirming..." : "Confirm & Supply"}
                              </button>
                            )}

                            {buyerApproved && (
                              <>
                                <span style={{ fontSize: "12px", color: "#047857", fontWeight: 600, whiteSpace: "nowrap" }}>
                                  Buyer approved this order. Confirm supply?
                                </span>
                                <button
                                  onClick={() => confirmSupply(order)}
                                  disabled={actionLoading[`confirm-supply-${order.id}`]}
                                  style={{
                                    padding: "4px 10px",
                                    background: "#15803d",
                                    color: "#fff",
                                    border: "none",
                                    borderRadius: "6px",
                                    fontSize: "12px",
                                    fontWeight: 600,
                                    cursor: actionLoading[`confirm-supply-${order.id}`] ? "default" : "pointer",
                                    whiteSpace: "nowrap"
                                  }}
                                >
                                  {actionLoading[`confirm-supply-${order.id}`] ? "Confirming..." : "Confirm"}
                                </button>
                                <button
                                  onClick={() => cancelVendorOrder(order)}
                                  disabled={actionLoading[`cancel-vendor-${order.id}`]}
                                  style={{
                                    padding: "4px 10px",
                                    background: "#fff",
                                    color: "#b91c1c",
                                    border: "1px solid #fecaca",
                                    borderRadius: "6px",
                                    fontSize: "12px",
                                    fontWeight: 600,
                                    cursor: actionLoading[`cancel-vendor-${order.id}`] ? "default" : "pointer",
                                    whiteSpace: "nowrap"
                                  }}
                                >
                                  {actionLoading[`cancel-vendor-${order.id}`] ? "Cancelling..." : "Cancel"}
                                </button>
                              </>
                            )}

                            {buyerRejected && (
                              <span style={{ fontSize: "12px", color: "#b91c1c", fontWeight: 600, whiteSpace: "nowrap" }}>
                                Buyer rejected this order.
                              </span>
                            )}

                            {canCancel && (
                              cancelConfirm ? (
                                <>
                                  <span style={{ fontSize: "12px", color: "#475569", whiteSpace: "nowrap" }}>
                                    Sure?
                                  </span>

                                  <button
                                    onClick={() => cancelOrder(order)}
                                    disabled={actionLoading[cancelKey]}
                                    style={{
                                      padding: "4px 10px",
                                      background: "#b91c1c",
                                      color: "#fff",
                                      border: "none",
                                      borderRadius: "6px",
                                      fontSize: "12px",
                                      fontWeight: 600,
                                      cursor: "pointer"
                                    }}
                                  >
                                    Yes
                                  </button>

                                  <button
                                    onClick={() => setConfirmState(prev => {
                                      const n = { ...prev };
                                      delete n[cancelKey];
                                      return n;
                                    })}
                                    style={{
                                      padding: "4px 10px",
                                      background: "#fff",
                                      color: "#475569",
                                      border: "1px solid #e2e8f0",
                                      borderRadius: "6px",
                                      fontSize: "12px",
                                      cursor: "pointer"
                                    }}
                                  >
                                    No
                                  </button>
                                </>
                              ) : (
                                <button
                                  onClick={() => cancelOrder(order)}
                                  style={{
                                    padding: "4px 10px",
                                    background: "#fff",
                                    color: "#b91c1c",
                                    border: "1px solid #fecaca",
                                    borderRadius: "6px",
                                    fontSize: "12px",
                                    fontWeight: 600,
                                    cursor: "pointer",
                                    whiteSpace: "nowrap"
                                  }}
                                >
                                  Cancel
                                </button>
                              )
                            )}
                          </div>
                        </td>
                      </tr>

                      {notifyOrderId === order.id && (
                        <tr key={`notify-${order.id}`} style={{ borderBottom: "1px solid #f1f5f9" }}>
                          <td colSpan={99} style={{ padding: "0 16px 12px" }}>
                            <StockNotifyPanel
                              order={order}
                              availabilityLines={availabilityLines}
                              onDone={() => {
                                setNotifyOrderId(null);
                                fetchOrders();
                              }}
                              onClose={() => setNotifyOrderId(null)}
                            />
                          </td>
                        </tr>
                      )}

                      {isExpanded && (
                        <tr key={`detail-${order.id}`} style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0" }}>
                          <td
                            colSpan={99}
                            style={{
                              padding: "22px 32px",
                              position: "sticky",
                              left: 0,
                              background: "#f8fafc",
                              boxSizing: "border-box"
                            }}
                          >
                            <p style={{ fontSize: "12px", fontWeight: 700, color: "#94a3b8", margin: "0 0 18px", letterSpacing: "0.04em", textAlign: "center" }}>
                              ORDER DETAILS
                            </p>

                            {order.items && order.items.length > 0 ? (
                              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px", tableLayout: "fixed" }}>
                                <colgroup>
                                  <col style={{ width: "22%" }} />
                                  <col style={{ width: "34%" }} />
                                  <col style={{ width: "12%" }} />
                                  <col style={{ width: "16%" }} />
                                  <col style={{ width: "16%" }} />
                                </colgroup>
                                <thead>
                                  <tr>
                                    {["Expected Date", "Product Item", "Quantity", "Unit Price", "Subtotal"].map(h => (
                                      <th
                                        key={h}
                                        style={{
                                          textAlign: h === "Product Item" ? "left" : "right",
                                          padding: "4px 8px 10px",
                                          color: "#94a3b8",
                                          fontWeight: 600,
                                          fontSize: "11px"
                                        }}
                                      >
                                        {h}
                                      </th>
                                    ))}
                                  </tr>
                                </thead>

                                <tbody>
                                  {order.items.map((item, i) => (
                                    <tr key={i}>
                                      <td style={{ padding: "4px 8px", color: "#475569", textAlign: "right", whiteSpace: "nowrap" }}>
                                        {order.expectedDeliveryDate || "Not set"}
                                      </td>

                                      <td style={{ padding: "4px 8px", color: "#0f172a", textAlign: "left" }}>
                                        {item.productName || item.description}
                                      </td>

                                      <td style={{ padding: "4px 8px", color: "#475569", textAlign: "right" }}>
                                        {item.quantity}
                                      </td>

                                      <td style={{ padding: "4px 8px", color: "#475569", textAlign: "right", whiteSpace: "nowrap" }}>
                                        ₹{(item.unitPrice ?? 0).toFixed(2)}
                                      </td>

                                      <td style={{ padding: "4px 8px", color: "#0f172a", fontWeight: 500, textAlign: "right", whiteSpace: "nowrap" }}>
                                        ₹{((item.quantity ?? 0) * (item.unitPrice ?? 0)).toFixed(2)}
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            ) : (
                              <div style={{ color: "#94a3b8", fontSize: "13px", textAlign: "center" }}>No items</div>
                            )}

                            {role === "VENDOR" && inventoryContext[order.id] && (() => {
                              const inv = inventoryContext[order.id];
                              if (!inv.found) {
                                return (
                                  <div style={{ marginTop: "24px" }}>
                                    <p style={{ fontSize: "12px", fontWeight: 700, color: "#94a3b8", margin: "0 0 12px", letterSpacing: "0.04em" }}>INVENTORY INFORMATION</p>
                                    <div style={{ color: "#94a3b8", fontSize: "13px" }}>No inventory record found for this item.</div>
                                  </div>
                                );
                              }
                              const palette = inv.statusColor === "GREEN"
                                ? { text: "#16a34a", bg: "#f0fdf4" }
                                : inv.statusColor === "YELLOW"
                                ? { text: "#d97706", bg: "#fffbeb" }
                                : { text: "#dc2626", bg: "#fef2f2" };
                              const statusLabel = (inv.status || "").replace(/_/g, " ");
                              const fmtTs = inv.lastUpdatedAt ? new Date(inv.lastUpdatedAt).toLocaleString() : "—";
                              const lastAction = inv.lastAction
                                ? `${inv.lastAction} ${inv.lastQuantityChanged > 0 ? "+" : ""}${inv.lastQuantityChanged ?? 0} units`
                                : "—";
                              const cell = (label, value, valueColor) => (
                                <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
                                  <div style={{ fontSize: "11px", color: "#94a3b8", fontWeight: 600, marginBottom: "4px" }}>{label}</div>
                                  <div style={{ fontSize: "15px", color: valueColor || "#0f172a", fontWeight: 600 }}>{value}</div>
                                </div>
                              );
                              return (
                                <div style={{ marginTop: "24px" }}>
                                  <p style={{ fontSize: "12px", fontWeight: 700, color: "#94a3b8", margin: "0 0 12px", letterSpacing: "0.04em" }}>INVENTORY INFORMATION</p>
                                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: "12px" }}>
                                    {cell("Current Inventory", `${inv.currentQuantity} ${inv.unit || "pcs"}`)}
                                    {cell("Reorder Threshold", `${inv.thresholdQuantity} ${inv.unit || "pcs"}`)}
                                    <div style={{ background: palette.bg, border: "1px solid #e2e8f0", borderRadius: "8px", padding: "12px 14px" }}>
                                      <div style={{ fontSize: "11px", color: "#94a3b8", fontWeight: 600, marginBottom: "4px" }}>Inventory Status</div>
                                      <div style={{ fontSize: "15px", color: palette.text, fontWeight: 700 }}>{statusLabel}</div>
                                    </div>
                                    {cell("Last Update", fmtTs)}
                                    {cell("Last Action", lastAction, inv.lastAction === "DECREASE" ? "#dc2626" : inv.lastAction === "INCREASE" ? "#16a34a" : "#0f172a")}
                                  </div>
                                </div>
                              );
                            })()}

                            {role === "VENDOR" && availabilityLines.length > 0 && (
                              <div style={{ marginTop: "24px" }}>
                                <p style={{
                                  fontSize: "12px",
                                  fontWeight: 700,
                                  color: "#94a3b8",
                                  margin: "0 0 12px",
                                  letterSpacing: "0.04em"
                                }}>
                                  INVENTORY AVAILABILITY
                                </p>

                                <table style={{
                                  width: "100%",
                                  borderCollapse: "collapse",
                                  fontSize: "13px",
                                  background: "#fff",
                                  border: "1px solid #e2e8f0",
                                  tableLayout: "fixed"
                                }}>
                                  <colgroup>
                                    <col style={{ width: "38%" }} />
                                    <col style={{ width: "16%" }} />
                                    <col style={{ width: "18%" }} />
                                    <col style={{ width: "28%" }} />
                                  </colgroup>
                                  <thead>
                                    <tr style={{ background: "#f8fafc" }}>
                                      {["Product", "Required", "Available", "System Suggestion"].map(h => (
                                        <th
                                          key={h}
                                          style={{
                                            textAlign: h === "Product" ? "left" : h === "System Suggestion" ? "center" : "right",
                                            padding: "8px",
                                            color: "#64748b",
                                            fontWeight: 600,
                                            fontSize: "12px"
                                          }}
                                        >
                                          {h}
                                        </th>
                                      ))}
                                    </tr>
                                  </thead>

                                  <tbody>
                                    {availabilityLines.map((line, idx) => {
                                      const required = Number(line.required || 0);
                                      const availableQty = line.available == null ? null : Number(line.available);
                                      const sufficient = availableQty != null && availableQty >= required;

                                      return (
                                        <tr key={idx}>
                                          <td style={{ padding: "8px", color: "#0f172a", textAlign: "left" }}>
                                            {line.description}
                                          </td>

                                          <td style={{ padding: "8px", color: "#475569", textAlign: "right" }}>
                                            {line.required}
                                          </td>

                                          <td style={{ padding: "8px", color: "#475569", textAlign: "right" }}>
                                            {line.available ?? "Not found"}
                                          </td>

                                          <td style={{ padding: "8px", textAlign: "center" }}>
                                            {sufficient ? (
                                              <span style={{ color: "#15803d", fontWeight: 600 }}>
                                                Full quantity can be supplied
                                              </span>
                                            ) : availableQty != null && availableQty > 0 ? (
                                              <span style={{ color: "#d97706", fontWeight: 600 }}>
                                                Partial supply possible
                                              </span>
                                            ) : (
                                              <span style={{ color: "#b91c1c", fontWeight: 600 }}>
                                                No stock available
                                              </span>
                                            )}
                                          </td>
                                        </tr>
                                      );
                                    })}
                                  </tbody>
                                </table>
                              </div>
                            )}
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

      {!loading && !error && (
        <Pagination
          page={page}
          pageSize={pageSize}
          totalRecords={totalRecords}
          onPageChange={nextPage => setPage(Math.max(0, Math.min(totalPages - 1, nextPage)))}
          onPageSizeChange={size => { setPageSize(size); setPage(0); }}
        />
      )}

      {showCreate && (
        <CreateOrderModal
          role={role}
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setTab("pending");
            fetchOrders();
          }}
        />
      )}
    </div>
  );
}
