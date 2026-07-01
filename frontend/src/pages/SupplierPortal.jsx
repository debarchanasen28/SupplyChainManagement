import { useState, useEffect, useRef } from "react";
import api from "../api/axios";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";

const inputStyle = {
  padding: "8px 10px",
  border: "1px solid #d1d5db",
  borderRadius: 7,
  fontSize: 13,
  outline: "none",
  background: "#fff",
  color: "#0f172a",
  boxSizing: "border-box",
  width: "100%",
};

const carriers = ["FedEx", "BlueDart", "DHL", "DTDC", "Delhivery", "India Post", "Other"];

const CSV_TEMPLATE =
  "itemName,sku,category,quantity,unitOfMeasure,reorderLevel,unitPrice,warehouseLocation,notes\n" +
  "Steel Rod 10mm,SKU-001,Raw Material,500,kg,50,45.50,Warehouse A,Grade A Quality\n" +
  "Packing Box,SKU-002,Packaging,1000,units,100,2.50,Warehouse B,";

function downloadTemplate() {
  const blob = new Blob([CSV_TEMPLATE], { type: "text/csv" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "inventory_upload_template.csv";
  a.click();
  URL.revokeObjectURL(url);
}

const orderStatusColors = {
  PENDING:    { bg: "#fefce8", color: "#a16207" },
  CONFIRMED:  { bg: "#eff6ff", color: "#1d4ed8" },
  PROCESSING: { bg: "#f5f3ff", color: "#6d28d9" },
  SHIPPED:    { bg: "#ecfeff", color: "#0e7490" },
  IN_TRANSIT: { bg: "#eff6ff", color: "#1d4ed8" },
  DELIVERED:  { bg: "#f0fdf4", color: "#15803d" },
  CANCELLED:  { bg: "#fef2f2", color: "#b91c1c" },
};

function StatusBadge({ label }) {
  const c = orderStatusColors[label] || { bg: "#f1f5f9", color: "#475569" };
  return (
    <span style={{
      fontSize: 11, fontWeight: 600, padding: "2px 8px",
      borderRadius: 20, background: c.bg, color: c.color,
      whiteSpace: "nowrap", letterSpacing: "0.02em",
    }}>
      {label?.replace(/_/g, " ")}
    </span>
  );
}

const emptyOrderForm = {
  expectedDeliveryDate: "",
  notes: "",
  items: [{ description: "", quantity: 1, unitPrice: 0 }],
};

const emptyShipmentForm = {
  orderId: "", carrier: "", trackingNumber: "",
  origin: "", destination: "", estimatedDelivery: "",
  delayReason: "", notes: "",
};

export default function SupplierPortal() {
  const role = localStorage.getItem("role");

  const [profile, setProfile] = useState(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [profileError, setProfileError] = useState("");

  const [activeTab, setActiveTab] = useState("submit");
  const [supplierType, setSupplierType] = useState("A");

  const [orderForm, setOrderForm] = useState(emptyOrderForm);
  const [orderError, setOrderError] = useState("");
  const [orderSuccess, setOrderSuccess] = useState("");
  const [submittingOrder, setSubmittingOrder] = useState(false);

  const [shipmentForm, setShipmentForm] = useState(emptyShipmentForm);
  const [shipmentError, setShipmentError] = useState("");
  const [shipmentSuccess, setShipmentSuccess] = useState("");
  const [submittingShipment, setSubmittingShipment] = useState(false);

  const [csvFile, setCsvFile] = useState(null);
  const [csvPreview, setCsvPreview] = useState([]);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploadError, setUploadError] = useState("");
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef();

  const [historyTab, setHistoryTab] = useState("orders");
  const [orders, setOrders] = useState([]);
  const [shipments, setShipments] = useState([]);
  const [inventory, setInventory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  useEffect(() => {
    if (role !== "SUPPLIER") return;
    api.get("/portal/me")
      .then(res => {
        setProfile(res.data);
        const t = res.data.supplierType?.toUpperCase();
        if (["A", "B", "C"].includes(t)) setSupplierType(t);
      })
      .catch(() => setProfileError("Failed to load your supplier profile."))
      .finally(() => setLoadingProfile(false));
  }, []);

  const fetchHistory = async () => {
    if (!profile) return;
    setLoadingHistory(true);
    try {
      const [oRes, sRes, iRes] = await Promise.all([
        api.get("/orders"),
        api.get("/shipments"),
        api.get("/inventory"),
      ]);
      const sid = profile.supplierId;
      setOrders(oRes.data.filter(o => o.supplierId === sid));
      setShipments(sRes.data.filter(s => s.supplierId === sid));
      setInventory(iRes.data.filter(i => i.supplierId === sid));
    } catch {
      // show empty silently
    } finally {
      setLoadingHistory(false);
    }
  };

  useEffect(() => {
    if (activeTab === "history" && profile) fetchHistory();
  }, [activeTab, profile]);

  const orderTotal = orderForm.items.reduce(
    (sum, it) => sum + Number(it.quantity) * Number(it.unitPrice), 0
  );

  const handleOrderItem = (idx, field, value) => {
    const updated = [...orderForm.items];
    updated[idx][field] = field === "description" ? value : Number(value);
    setOrderForm({ ...orderForm, items: updated });
  };

  const submitOrder = async () => {
    if (!orderForm.items.length || orderForm.items.some(it => !it.description)) {
      setOrderError("All items need a description."); return;
    }
    setOrderError(""); setSubmittingOrder(true);
    try {
      await api.post("/orders", {
        supplierId: profile.supplierId,
        supplierName: profile.companyName,
        expectedDeliveryDate: orderForm.expectedDeliveryDate,
        notes: orderForm.notes,
        items: orderForm.items.map(it => ({
          ...it,
          totalPrice: Number(it.quantity) * Number(it.unitPrice),
        })),
      });
      setOrderSuccess("Order submitted successfully.");
      setOrderForm(emptyOrderForm);
      setTimeout(() => setOrderSuccess(""), 5000);
    } catch {
      setOrderError("Failed to submit order.");
    } finally {
      setSubmittingOrder(false);
    }
  };

  const submitShipment = async () => {
    if (!shipmentForm.carrier || !shipmentForm.origin || !shipmentForm.destination) {
      setShipmentError("Carrier, origin and destination are required."); return;
    }
    setShipmentError(""); setSubmittingShipment(true);
    try {
      await api.post("/shipments", {
        ...shipmentForm,
        supplierId: profile.supplierId,
        supplierName: profile.companyName,
      });
      setShipmentSuccess("Shipment update submitted successfully.");
      setShipmentForm(emptyShipmentForm);
      setTimeout(() => setShipmentSuccess(""), 5000);
    } catch {
      setShipmentError("Failed to submit shipment.");
    } finally {
      setSubmittingShipment(false);
    }
  };

  const parseCsvPreview = (file) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const lines = e.target.result.split("\n").filter(l => l.trim());
      if (lines.length < 2) { setCsvPreview([]); return; }
      const headers = lines[0].split(",").map(h => h.trim().replace("\r", ""));
      const rows = lines.slice(1).map(line => {
        const cols = line.split(",");
        return headers.reduce((obj, h, i) => ({
          ...obj,
          [h]: (cols[i] || "").trim().replace("\r", ""),
        }), {});
      });
      setCsvPreview(rows);
    };
    reader.readAsText(file);
  };

  const handleFileChange = (e) => {
    const f = e.target.files[0];
    if (!f) return;
    setCsvFile(f);
    setUploadResult(null);
    setUploadError("");
    parseCsvPreview(f);
  };

  const clearFile = () => {
    setCsvFile(null);
    setCsvPreview([]);
    setUploadResult(null);
    if (fileRef.current) fileRef.current.value = "";
  };

  const submitCsv = async () => {
    if (!csvFile) { setUploadError("Please select a CSV file."); return; }
    setUploadError(""); setUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", csvFile);
      formData.append("supplierId", profile.supplierId);
      formData.append("supplierName", profile.companyName);
      const res = await api.post("/portal/inventory/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setUploadResult(res.data);
      clearFile();
    } catch {
      setUploadError("Failed to upload CSV. Check file format and try again.");
    } finally {
      setUploading(false);
    }
  };

  const switchType = (t) => {
    setSupplierType(t);
    setOrderError(""); setOrderSuccess("");
    setShipmentError(""); setShipmentSuccess("");
    setUploadError(""); setUploadResult(null);
  };

  const fmtDate = (d) => {
    if (!d) return "—";
    return new Date(d).toLocaleDateString("en-IN", {
      day: "2-digit", month: "short", year: "numeric",
    });
  };

  // ---- GUARDS ----

  if (role !== "SUPPLIER") {
    return (
      <div style={{ padding: "28px 32px", fontFamily: font }}>
        <div style={{
          background: "#eff6ff", border: "1px solid #bfdbfe",
          borderRadius: 12, padding: "48px 32px", textAlign: "center",
        }}>
          <div style={{ fontSize: 16, fontWeight: 700, color: "#1d4ed8", marginBottom: 8 }}>Supplier Portal</div>
          <div style={{ fontSize: 13, color: "#64748b" }}>
            This page is for suppliers only. Admins and managers can view supplier data from the main dashboard.
          </div>
        </div>
      </div>
    );
  }

  if (loadingProfile) {
    return (
      <div style={{ padding: "28px 32px", fontFamily: font }}>
        <div style={{ padding: 60, textAlign: "center", color: "#94a3b8", fontSize: 14 }}>
          Loading your profile…
        </div>
      </div>
    );
  }

  if (profileError || !profile) {
    return (
      <div style={{ padding: "28px 32px", fontFamily: font }}>
        <div style={{
          background: "#fef2f2", border: "1px solid #fecaca",
          borderRadius: 12, padding: "40px 32px", textAlign: "center",
        }}>
          <div style={{ fontSize: 14, color: "#b91c1c" }}>
            {profileError || "Could not load your profile."}
          </div>
        </div>
      </div>
    );
  }

  // ---- MAIN RENDER ----

  return (
    <div style={{ padding: "28px 32px", fontFamily: font, color: "#0f172a" }}>

      {/* Header */}
      <div style={{ marginBottom: 28, textAlign: "center", width: "100%" }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0, color: "#0f172a", letterSpacing: "1px" }}>Supplier Portal</h1>
        <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 8, flexWrap: "wrap" }}>
          <span style={{ fontSize: 13, color: "#64748b" }}>Welcome,</span>
          <span style={{ fontWeight: 700, fontSize: 13, color: "#0f172a" }}>{profile.companyName}</span>
          <span style={{
            fontSize: 11, padding: "2px 8px", borderRadius: 10,
            background: "#eff6ff", color: "#1d4ed8", fontWeight: 600, border: "1px solid #bfdbfe",
          }}>
            {profile.supplierId}
          </span>
          {profile.supplierType && (
            <span style={{
              fontSize: 11, padding: "2px 8px", borderRadius: 10,
              background: "#f5f3ff", color: "#6d28d9", fontWeight: 600, border: "1px solid #e9d5ff",
            }}>
              Type {profile.supplierType}
            </span>
          )}
          {profile.contactPersonName && (
            <span style={{ fontSize: 12, color: "#94a3b8" }}>| {profile.contactPersonName}</span>
          )}
        </div>
      </div>

      {/* Main tab bar */}
      <div style={{ display: "flex", gap: 4, marginBottom: 24, borderBottom: "1px solid #e2e8f0" }}>
        {[["submit", "New Submission"], ["history", "My Submissions"]].map(([key, label]) => (
          <button key={key} onClick={() => setActiveTab(key)} style={{
            background: "none", border: "none", padding: "10px 18px", fontSize: 14,
            cursor: "pointer", fontWeight: activeTab === key ? 700 : 500,
            color: activeTab === key ? "#2563eb" : "#64748b",
            borderBottom: activeTab === key ? "2px solid #2563eb" : "2px solid transparent",
            marginBottom: -1,
          }}>
            {label}
          </button>
        ))}
      </div>

      {/* ========== NEW SUBMISSION ========== */}
      {activeTab === "submit" && (
        <div>

          {/* Type selector */}
          <div style={{
            background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12,
            padding: "20px 24px", marginBottom: 20, boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
          }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 12 }}>
              Submission Type
            </div>
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              {[
                ["A", "Type A — Order",           "Submit a purchase order in JSON format"],
                ["B", "Type B — Shipment Update", "Submit a shipment update in XML format"],
                ["C", "Type C — Inventory CSV",   "Upload inventory stock data via CSV"],
              ].map(([type, label, desc]) => (
                <button key={type} onClick={() => switchType(type)} style={{
                  flex: 1, minWidth: 160, padding: "14px 16px",
                  border: supplierType === type ? "2px solid #2563eb" : "1px solid #e2e8f0",
                  borderRadius: 10,
                  background: supplierType === type ? "#eff6ff" : "#fafafa",
                  cursor: "pointer", textAlign: "left",
                }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: supplierType === type ? "#1d4ed8" : "#374151" }}>
                    {label}
                  </div>
                  <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 3 }}>{desc}</div>
                </button>
              ))}
            </div>
          </div>

          {/* Auto-filled supplier info bar */}
          <div style={{
            background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10,
            padding: "10px 20px", marginBottom: 20,
            display: "flex", gap: 24, flexWrap: "wrap",
          }}>
            {[
              ["Supplier ID", profile.supplierId],
              ["Company",     profile.companyName],
              ["Contact",     profile.contactPersonName || "—"],
              ["Email",       profile.email || "—"],
            ].map(([label, val]) => (
              <div key={label} style={{ fontSize: 12 }}>
                <span style={{ color: "#94a3b8", fontWeight: 500 }}>{label}: </span>
                <span style={{ color: "#0f172a", fontWeight: 600 }}>{val}</span>
              </div>
            ))}
          </div>

          {/* ---- TYPE A: ORDER ---- */}
          {supplierType === "A" && (
            <div style={{
              background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12,
              padding: "24px", boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
            }}>
              <h3 style={{ fontSize: 15, fontWeight: 700, margin: "0 0 20px", color: "#0f172a" }}>
                Order Submission
              </h3>

              {orderError && (
                <div style={{
                  background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
                  borderRadius: 8, padding: "8px 14px", marginBottom: 16, fontSize: 13,
                }}>
                  {orderError}
                </div>
              )}
              {orderSuccess && (
                <div style={{
                  background: "#f0fdf4", color: "#15803d", border: "1px solid #bbf7d0",
                  borderRadius: 8, padding: "10px 16px", marginBottom: 16, fontSize: 13, fontWeight: 600,
                }}>
                  {orderSuccess}
                </div>
              )}

              <div style={{ marginBottom: 18 }}>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                  Expected Delivery Date
                </label>
                <input
                  type="date"
                  value={orderForm.expectedDeliveryDate}
                  onChange={e => setOrderForm({ ...orderForm, expectedDeliveryDate: e.target.value })}
                  style={{ ...inputStyle, maxWidth: 220 }}
                />
              </div>

              <div style={{ marginBottom: 18 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "#374151" }}>Order Items</label>
                  <button
                    onClick={() => setOrderForm({
                      ...orderForm,
                      items: [...orderForm.items, { description: "", quantity: 1, unitPrice: 0 }],
                    })}
                    style={{ fontSize: 12, color: "#2563eb", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}
                  >
                    + Add Item
                  </button>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr auto", gap: 8, marginBottom: 6 }}>
                  {["Description", "Qty", "Unit Price (₹)", "Line Total (₹)", ""].map((h, i) => (
                    <span key={i} style={{ fontSize: 11, fontWeight: 600, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.04em" }}>
                      {h}
                    </span>
                  ))}
                </div>

                {orderForm.items.map((item, idx) => (
                  <div key={idx} style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr auto", gap: 8, marginBottom: 8, alignItems: "center" }}>
                    <input
                      placeholder="Item description"
                      value={item.description}
                      onChange={e => handleOrderItem(idx, "description", e.target.value)}
                      style={inputStyle}
                    />
                    <input
                      type="number" min={1}
                      value={item.quantity}
                      onChange={e => handleOrderItem(idx, "quantity", e.target.value)}
                      style={inputStyle}
                    />
                    <input
                      type="number" min={0} step="0.01"
                      value={item.unitPrice}
                      onChange={e => handleOrderItem(idx, "unitPrice", e.target.value)}
                      style={inputStyle}
                    />
                    <div style={{
                      padding: "8px 10px", background: "#f8fafc", borderRadius: 7,
                      fontSize: 13, color: "#374151", border: "1px solid #e2e8f0", textAlign: "right",
                    }}>
                      ₹{(Number(item.quantity) * Number(item.unitPrice)).toLocaleString("en-IN", { maximumFractionDigits: 2 })}
                    </div>
                    {orderForm.items.length > 1 ? (
                      <button
                        onClick={() => setOrderForm({ ...orderForm, items: orderForm.items.filter((_, i) => i !== idx) })}
                        style={{ background: "none", border: "none", color: "#b91c1c", cursor: "pointer", fontSize: 16 }}
                      >
                        ✕
                      </button>
                    ) : (
                      <span />
                    )}
                  </div>
                ))}

                <div style={{
                  textAlign: "right", fontSize: 15, fontWeight: 700, color: "#0f172a",
                  marginTop: 10, paddingTop: 10, borderTop: "2px solid #f1f5f9",
                }}>
                  Order Total: ₹{orderTotal.toLocaleString("en-IN", { maximumFractionDigits: 2 })}
                </div>
              </div>

              <div style={{ marginBottom: 20 }}>
                <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>Notes</label>
                <textarea
                  rows={2}
                  value={orderForm.notes}
                  onChange={e => setOrderForm({ ...orderForm, notes: e.target.value })}
                  placeholder="Optional notes..."
                  style={{ ...inputStyle, resize: "vertical", fontFamily: font }}
                />
              </div>

              <button onClick={submitOrder} disabled={submittingOrder} style={{
                padding: "10px 28px", border: "none", borderRadius: 8,
                background: submittingOrder ? "#93c5fd" : "#2563eb", color: "#fff",
                fontSize: 14, fontWeight: 600, cursor: submittingOrder ? "default" : "pointer",
              }}>
                {submittingOrder ? "Submitting…" : "Submit Order"}
              </button>
            </div>
          )}

          {/* ---- TYPE B: SHIPMENT ---- */}
          {supplierType === "B" && (
            <div style={{
              background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12,
              padding: "24px", boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
            }}>
              <h3 style={{ fontSize: 15, fontWeight: 700, margin: "0 0 20px", color: "#0f172a" }}>
                Shipment Update
              </h3>

              {shipmentError && (
                <div style={{
                  background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
                  borderRadius: 8, padding: "8px 14px", marginBottom: 16, fontSize: 13,
                }}>
                  {shipmentError}
                </div>
              )}
              {shipmentSuccess && (
                <div style={{
                  background: "#f0fdf4", color: "#15803d", border: "1px solid #bbf7d0",
                  borderRadius: 8, padding: "10px 16px", marginBottom: 16, fontSize: 13, fontWeight: 600,
                }}>
                  {shipmentSuccess}
                </div>
              )}

              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                      Linked Order ID
                    </label>
                    <input
                      value={shipmentForm.orderId}
                      onChange={e => setShipmentForm({ ...shipmentForm, orderId: e.target.value })}
                      placeholder="e.g. ORD-XXXXXXXX"
                      style={inputStyle}
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                      Carrier *
                    </label>
                    <select
                      value={shipmentForm.carrier}
                      onChange={e => setShipmentForm({ ...shipmentForm, carrier: e.target.value })}
                      style={inputStyle}
                    >
                      <option value="">Select carrier…</option>
                      {carriers.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                  </div>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                      Origin *
                    </label>
                    <input
                      value={shipmentForm.origin}
                      onChange={e => setShipmentForm({ ...shipmentForm, origin: e.target.value })}
                      placeholder="e.g. Mumbai"
                      style={inputStyle}
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                      Destination *
                    </label>
                    <input
                      value={shipmentForm.destination}
                      onChange={e => setShipmentForm({ ...shipmentForm, destination: e.target.value })}
                      placeholder="e.g. Delhi"
                      style={inputStyle}
                    />
                  </div>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                      Tracking Number
                    </label>
                    <input
                      value={shipmentForm.trackingNumber}
                      onChange={e => setShipmentForm({ ...shipmentForm, trackingNumber: e.target.value })}
                      placeholder="e.g. 1Z999AA10123456784"
                      style={inputStyle}
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                      Estimated Delivery
                    </label>
                    <input
                      type="date"
                      value={shipmentForm.estimatedDelivery}
                      onChange={e => setShipmentForm({ ...shipmentForm, estimatedDelivery: e.target.value })}
                      style={inputStyle}
                    />
                  </div>
                </div>

                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                    Delay Reason (if applicable)
                  </label>
                  <input
                    value={shipmentForm.delayReason}
                    onChange={e => setShipmentForm({ ...shipmentForm, delayReason: e.target.value })}
                    placeholder="e.g. Port congestion at Mumbai"
                    style={inputStyle}
                  />
                </div>

                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", display: "block", marginBottom: 4 }}>
                    Notes
                  </label>
                  <textarea
                    rows={2}
                    value={shipmentForm.notes}
                    onChange={e => setShipmentForm({ ...shipmentForm, notes: e.target.value })}
                    placeholder="Optional notes..."
                    style={{ ...inputStyle, resize: "vertical", fontFamily: font }}
                  />
                </div>

              </div>

              <button onClick={submitShipment} disabled={submittingShipment} style={{
                marginTop: 20, padding: "10px 28px", border: "none", borderRadius: 8,
                background: submittingShipment ? "#93c5fd" : "#2563eb", color: "#fff",
                fontSize: 14, fontWeight: 600, cursor: submittingShipment ? "default" : "pointer",
              }}>
                {submittingShipment ? "Submitting…" : "Submit Shipment Update"}
              </button>
            </div>
          )}

          {/* ---- TYPE C: CSV UPLOAD ---- */}
          {supplierType === "C" && (
            <div style={{
              background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12,
              padding: "24px", boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
                <h3 style={{ fontSize: 15, fontWeight: 700, margin: 0, color: "#0f172a" }}>
                  Inventory CSV Upload
                </h3>
                <button onClick={downloadTemplate} style={{
                  fontSize: 12, color: "#2563eb", background: "#eff6ff",
                  border: "1px solid #bfdbfe", borderRadius: 7,
                  padding: "6px 14px", cursor: "pointer", fontWeight: 600,
                }}>
                  Download Template
                </button>
              </div>

              {uploadError && (
                <div style={{
                  background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
                  borderRadius: 8, padding: "8px 14px", marginBottom: 16, fontSize: 13,
                }}>
                  {uploadError}
                </div>
              )}

              {uploadResult && (
                <div style={{
                  background: uploadResult.failed === 0 ? "#f0fdf4" : "#fffbeb",
                  border: `1px solid ${uploadResult.failed === 0 ? "#bbf7d0" : "#fde68a"}`,
                  borderRadius: 10, padding: "16px 20px", marginBottom: 20,
                }}>
                  <div style={{
                    fontSize: 14, fontWeight: 700,
                    color: uploadResult.failed === 0 ? "#15803d" : "#b45309",
                    marginBottom: uploadResult.errors?.length ? 8 : 0,
                  }}>
                    Upload complete — {uploadResult.successful} of {uploadResult.total} rows saved
                    {uploadResult.failed > 0 && `, ${uploadResult.failed} failed`}
                  </div>
                  {uploadResult.errors?.map((err, i) => (
                    <div key={i} style={{ fontSize: 12, color: "#92400e", marginTop: 2 }}>• {err}</div>
                  ))}
                </div>
              )}

              {/* Drop zone */}
              <div
                onClick={() => fileRef.current?.click()}
                style={{
                  border: `2px dashed ${csvFile ? "#86efac" : "#d1d5db"}`,
                  borderRadius: 10, padding: "36px 20px", textAlign: "center",
                  cursor: "pointer", marginBottom: 16,
                  background: csvFile ? "#f0fdf4" : "#fafafa",
                }}
              >
                <div style={{ fontSize: 13, color: csvFile ? "#15803d" : "#64748b", fontWeight: csvFile ? 600 : 400 }}>
                  {csvFile ? csvFile.name : "Click to select a CSV file"}
                </div>
                {!csvFile && (
                  <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
                    Accepts .csv files — download the template above to see the required format
                  </div>
                )}
                <input
                  ref={fileRef}
                  type="file"
                  accept=".csv"
                  onChange={handleFileChange}
                  style={{ display: "none" }}
                />
              </div>

              {/* Preview table */}
              {csvPreview.length > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 8 }}>
                    Preview — {csvPreview.length} data row{csvPreview.length !== 1 ? "s" : ""} detected
                  </div>
                  <div style={{ overflowX: "auto", border: "1px solid #e2e8f0", borderRadius: 8 }}>
                    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                      <thead>
                        <tr style={{ background: "#f8fafc" }}>
                          {Object.keys(csvPreview[0]).map(h => (
                            <th key={h} style={{
                              padding: "8px 10px", textAlign: "left", color: "#64748b",
                              fontWeight: 600, whiteSpace: "nowrap", borderBottom: "1px solid #e2e8f0",
                            }}>
                              {h}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {csvPreview.slice(0, 5).map((row, i) => (
                          <tr key={i} style={{ borderTop: "1px solid #f1f5f9" }}>
                            {Object.values(row).map((val, j) => (
                              <td key={j} style={{ padding: "7px 10px", color: "#374151" }}>
                                {val || "—"}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {csvPreview.length > 5 && (
                    <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4, textAlign: "right" }}>
                      Showing first 5 of {csvPreview.length} rows
                    </div>
                  )}
                </div>
              )}

              <div style={{ display: "flex", gap: 10 }}>
                <button onClick={submitCsv} disabled={uploading || !csvFile} style={{
                  padding: "10px 28px", border: "none", borderRadius: 8,
                  background: !csvFile ? "#e2e8f0" : uploading ? "#93c5fd" : "#2563eb",
                  color: !csvFile ? "#94a3b8" : "#fff",
                  fontSize: 14, fontWeight: 600,
                  cursor: !csvFile || uploading ? "default" : "pointer",
                }}>
                  {uploading ? "Uploading…" : "Upload CSV"}
                </button>
                {csvFile && (
                  <button onClick={clearFile} style={{
                    padding: "10px 18px", border: "1px solid #d1d5db", borderRadius: 8,
                    background: "#fff", color: "#374151", fontSize: 13, cursor: "pointer",
                  }}>
                    Clear
                  </button>
                )}
              </div>
            </div>
          )}

        </div>
      )}

      {/* ========== MY SUBMISSIONS ========== */}
      {activeTab === "history" && (
        <div>

          {/* Sub-tabs */}
          <div style={{ display: "flex", gap: 4, marginBottom: 20, borderBottom: "1px solid #e2e8f0" }}>
            {[
              ["orders",    `Orders (${orders.length})`],
              ["shipments", `Shipments (${shipments.length})`],
              ["inventory", `Inventory (${inventory.length})`],
            ].map(([key, label]) => (
              <button key={key} onClick={() => setHistoryTab(key)} style={{
                background: "none", border: "none", padding: "8px 14px", fontSize: 13,
                cursor: "pointer", fontWeight: historyTab === key ? 700 : 500,
                color: historyTab === key ? "#2563eb" : "#64748b",
                borderBottom: historyTab === key ? "2px solid #2563eb" : "2px solid transparent",
                marginBottom: -1,
              }}>
                {label}
              </button>
            ))}
          </div>

          {/* Table area */}
          <div style={{
            background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12,
            boxShadow: "0 1px 3px rgba(0,0,0,0.04)", overflow: "hidden",
          }}>
            {loadingHistory ? (
              <div style={{ padding: 40, textAlign: "center", color: "#94a3b8", fontSize: 14 }}>Loading…</div>

            ) : historyTab === "orders" ? (
              orders.length === 0 ? (
                <div style={{ padding: "48px 32px", textAlign: "center" }}>
                  <div style={{ fontSize: 14, color: "#94a3b8" }}>No orders submitted yet.</div>
                </div>
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, minWidth: 600 }}>
                    <thead>
                      <tr style={{ background: "#f8fafc" }}>
                        {["Order ID", "Items", "Total (₹)", "Status", "Expected Delivery", "Submitted"].map(h => (
                          <th key={h} style={{
                            padding: "10px 14px", textAlign: "left", color: "#64748b",
                            fontWeight: 600, fontSize: 11, textTransform: "uppercase",
                            letterSpacing: "0.04em", whiteSpace: "nowrap",
                          }}>
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {orders.map((o, i) => (
                        <tr key={o.id} style={{ borderTop: "1px solid #f1f5f9", background: i % 2 === 0 ? "#fff" : "#fafafa" }}>
                          <td style={{ padding: "11px 14px", fontWeight: 600, color: "#2563eb" }}>{o.orderId}</td>
                          <td style={{ padding: "11px 14px", color: "#64748b" }}>{o.items?.length ?? 0}</td>
                          <td style={{ padding: "11px 14px", fontWeight: 500 }}>
                            {o.totalAmount != null
                              ? "₹" + o.totalAmount.toLocaleString("en-IN", { maximumFractionDigits: 2 })
                              : "—"}
                          </td>
                          <td style={{ padding: "11px 14px" }}><StatusBadge label={o.status} /></td>
                          <td style={{ padding: "11px 14px", color: "#94a3b8" }}>{fmtDate(o.expectedDeliveryDate)}</td>
                          <td style={{ padding: "11px 14px", color: "#94a3b8" }}>{fmtDate(o.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )

            ) : historyTab === "shipments" ? (
              shipments.length === 0 ? (
                <div style={{ padding: "48px 32px", textAlign: "center" }}>
                  <div style={{ fontSize: 14, color: "#94a3b8" }}>No shipments submitted yet.</div>
                </div>
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, minWidth: 600 }}>
                    <thead>
                      <tr style={{ background: "#f8fafc" }}>
                        {["Shipment ID", "Route", "Carrier", "Status", "ETA", "Submitted"].map(h => (
                          <th key={h} style={{
                            padding: "10px 14px", textAlign: "left", color: "#64748b",
                            fontWeight: 600, fontSize: 11, textTransform: "uppercase",
                            letterSpacing: "0.04em", whiteSpace: "nowrap",
                          }}>
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {shipments.map((s, i) => (
                        <tr key={s.id} style={{ borderTop: "1px solid #f1f5f9", background: i % 2 === 0 ? "#fff" : "#fafafa" }}>
                          <td style={{ padding: "11px 14px", fontWeight: 600, color: "#2563eb" }}>{s.shipmentId}</td>
                          <td style={{ padding: "11px 14px", color: "#334155" }}>{s.origin} → {s.destination}</td>
                          <td style={{ padding: "11px 14px", color: "#64748b" }}>{s.carrier}</td>
                          <td style={{ padding: "11px 14px" }}><StatusBadge label={s.status} /></td>
                          <td style={{ padding: "11px 14px", color: "#94a3b8" }}>{fmtDate(s.estimatedDelivery)}</td>
                          <td style={{ padding: "11px 14px", color: "#94a3b8" }}>{fmtDate(s.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )

            ) : (
              inventory.length === 0 ? (
                <div style={{ padding: "48px 32px", textAlign: "center" }}>
                  <div style={{ fontSize: 14, color: "#94a3b8" }}>No inventory items uploaded yet.</div>
                </div>
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, minWidth: 700 }}>
                    <thead>
                      <tr style={{ background: "#f8fafc" }}>
                        {["Item ID", "Name", "SKU", "Category", "Quantity", "Warehouse", "Uploaded"].map(h => (
                          <th key={h} style={{
                            padding: "10px 14px", textAlign: "left", color: "#64748b",
                            fontWeight: 600, fontSize: 11, textTransform: "uppercase",
                            letterSpacing: "0.04em", whiteSpace: "nowrap",
                          }}>
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {inventory.map((item, i) => (
                        <tr key={item.id} style={{ borderTop: "1px solid #f1f5f9", background: i % 2 === 0 ? "#fff" : "#fafafa" }}>
                          <td style={{ padding: "11px 14px", color: "#64748b", fontFamily: "monospace", fontSize: 12 }}>{item.inventoryId}</td>
                          <td style={{ padding: "11px 14px", fontWeight: 600, color: "#0f172a" }}>{item.itemName}</td>
                          <td style={{ padding: "11px 14px", color: "#64748b", fontFamily: "monospace", fontSize: 12 }}>{item.sku || "—"}</td>
                          <td style={{ padding: "11px 14px", color: "#334155" }}>{item.category || "—"}</td>
                          <td style={{ padding: "11px 14px", color: "#0f172a", fontWeight: 500 }}>
                            {item.quantity}{" "}
                            <span style={{ fontSize: 11, color: "#94a3b8" }}>{item.unitOfMeasure}</span>
                          </td>
                          <td style={{ padding: "11px 14px", color: "#64748b" }}>{item.warehouseLocation || "—"}</td>
                          <td style={{ padding: "11px 14px", color: "#94a3b8" }}>{fmtDate(item.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            )}
          </div>

        </div>
      )}

    </div>
  );
}