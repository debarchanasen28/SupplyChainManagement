import { useEffect, useState } from "react";
import axios from "../api/axios";
import { useAuth } from "../context/AuthContext";
import Pagination from "../components/Pagination";

const API = "http://localhost:8080/api";
const LOW_STOCK_LIMIT = 1000;

export default function Inventory() {
  const [items, setItems] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [tab, setTab] = useState("inventory");
  const [selectedItemId, setSelectedItemId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [resolvingId, setResolvingId] = useState(null);
  const [message, setMessage] = useState({ type: "", text: "" });
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [inventoryMeta, setInventoryMeta] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true
  });
  const [alertMeta, setAlertMeta] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true
  });

  const { token, role, systemId } = useAuth();
  const headers = { Authorization: `Bearer ${token}` };
  const isProcurement = role === "PROCUREMENT";

  const refresh = async (showLoading = false) => {
    if (showLoading) setLoading(true);

    try {
      const params = { page, size: pageSize, sort: "createdAt,desc" };
      const requests = [axios.get(`${API}/inventory`, { headers, params })];

      if (isProcurement) {
        requests.push(axios.get(`${API}/inventory/alerts`, { headers, params }));
      }

      const [inventoryResponse, alertsResponse] = await Promise.all(requests);

      const nextItems = inventoryResponse.data.content || [];

      setItems(nextItems);
      setInventoryMeta(inventoryResponse.data);
      setAlerts(alertsResponse?.data?.content || []);

      if (alertsResponse) setAlertMeta(alertsResponse.data);

      setSelectedItemId(current => current || nextItems[0]?.id || "");
    } catch (err) {
      console.error("Failed to load System1 inventory:", err);
      setMessage({
        type: "error",
        text: err.response?.data?.error || "Failed to load inventory."
      });
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    refresh(true);
  }, [page, pageSize, tab]);

  // Auto-refresh: quantities move as orders are delivered (iFlow4 inventory sync),
  // so silently re-poll the current view every 15s without a loading spinner.
  useEffect(() => {
    const id = setInterval(() => refresh(false), 15000);
    return () => clearInterval(id);
  }, [page, pageSize, tab]);

  const submitStock = async e => {
    e.preventDefault();

    const amount = Number(quantity);

    if (!selectedItemId || !Number.isInteger(amount) || amount <= 0) {
      setMessage({
        type: "error",
        text: "Select an item and enter a positive whole quantity."
      });
      return;
    }

    const action = isProcurement ? "receive" : "sell";
    const selectedItem = items.find(item => item.id === selectedItemId);

    setSaving(true);
    setMessage({ type: "", text: "" });

    try {
      await axios.post(
        `${API}/inventory/${action}`,
        {
          itemName: selectedItem?.itemName,
          sku: selectedItem?.sku,
          quantity: amount,
          reason: isProcurement
            ? "Received by System1 Procurement"
            : "Supplied by System1 Vendor"
        },
        { headers }
      );

      setQuantity("");

      setMessage({
        type: "success",
        text: isProcurement
          ? "Stock received successfully."
          : "Stock supplied successfully."
      });

      await refresh();
    } catch (err) {
      console.error(`Failed to ${action} stock:`, err);

      setMessage({
        type: "error",
        text:
          err.response?.data?.error ||
          err.response?.data?.message ||
          `Failed to ${action} stock.`
      });
    } finally {
      setSaving(false);
    }
  };

  const resolveAlert = async alertId => {
    setResolvingId(alertId);
    setMessage({ type: "", text: "" });

    try {
      await axios.patch(`${API}/inventory/alerts/${alertId}/resolve`, {}, { headers });

      setMessage({
        type: "success",
        text: "Low-stock alert resolved."
      });

      await refresh();
    } catch (err) {
      console.error("Failed to resolve inventory alert:", err);

      setMessage({
        type: "error",
        text: err.response?.data?.error || "Failed to resolve alert."
      });
    } finally {
      setResolvingId(null);
    }
  };

  if (systemId !== "SYSTEM1" || !["PROCUREMENT", "VENDOR"].includes(role)) return null;

  const query = search.trim().toLowerCase();

  const filteredItems = items.filter(
    item =>
      !query ||
      (item.itemName || "").toLowerCase().includes(query) ||
      (item.sku || "").toLowerCase().includes(query)
  );

  const activeAlerts = alerts.filter(alert => alert.status !== "RESOLVED");
  const pagedItems = filteredItems;
  const pagedAlerts = activeAlerts;

  const currentMeta = tab === "inventory" ? inventoryMeta : alertMeta;
  const currentTotal = currentMeta.totalElements || 0;

  const thStyle = {
    padding: "14px 16px",
    textAlign: "center",
    color: "#334155",
    fontWeight: 700,
    fontSize: "13px",
    borderBottom: "1px solid #e2e8f0",
    borderRight: "1px solid #e2e8f0"
  };

  const tdStyle = {
    padding: "14px 16px",
    textAlign: "center",
    color: "#0f172a",
    borderBottom: "1px solid #f1f5f9",
    borderRight: "1px solid #e2e8f0"
  };

  return (
    <div style={{ padding: "32px", background: "#f8fafc", minHeight: "100vh" }}>
      <div style={{ marginBottom: "28px" }}>
        <h1
          style={{
            fontSize: "26px",
            fontWeight: 800,
            color: "#0f172a",
            margin: "0",
            letterSpacing: "1px"
          }}
        >
          Inventory
        </h1>

        <p
          style={{
            fontSize: "14px",
            color: "#64748b",
            margin: "6px 0 0"
          }}
        >
          {isProcurement
            ? "Receive stock and manage low-stock alerts"
            : "View and supply available stock"}
        </p>
      </div>

      {message.text && (
        <div
          style={{
            background: message.type === "success" ? "#f0fdf4" : "#fef2f2",
            border: `1px solid ${
              message.type === "success" ? "#bbf7d0" : "#fecaca"
            }`,
            borderRadius: "10px",
            padding: "12px 14px",
            color: message.type === "success" ? "#15803d" : "#b91c1c",
            fontSize: "13px",
            marginBottom: "18px"
          }}
        >
          {message.text}
        </div>
      )}

      {isProcurement && (
        <div
          style={{
            display: "flex",
            gap: "28px",
            borderBottom: "1px solid #e2e8f0",
            marginBottom: "26px"
          }}
        >
          {[
            { key: "inventory", label: "Inventory" },
            { key: "alerts", label: "Low-Stock Alerts" }
          ].map(item => (
            <button
              key={item.key}
              onClick={() => {
                setTab(item.key);
                setPage(0);
              }}
              style={{
                padding: "10px 0",
                border: "none",
                background: "transparent",
                borderBottom:
                  tab === item.key ? "3px solid #4f46e5" : "3px solid transparent",
                color: tab === item.key ? "#4f46e5" : "#64748b",
                fontWeight: tab === item.key ? 700 : 500,
                fontSize: "15px",
                cursor: "pointer",
                marginBottom: "-1px"
              }}
            >
              {item.label}
              {item.key === "alerts" && activeAlerts.length > 0
                ? ` (${activeAlerts.length})`
                : ""}
            </button>
          ))}
        </div>
      )}

      {tab === "inventory" ? (
        <>
          <form
            onSubmit={submitStock}
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 280px auto",
              gap: "18px",
              alignItems: "end",
              background: "#fff",
              border: "1px solid #e2e8f0",
              borderRadius: "12px",
              padding: "18px 20px",
              marginBottom: "24px"
            }}
          >
            <div>
              <label
                style={{
                  display: "block",
                  fontSize: "13px",
                  fontWeight: 700,
                  color: "#0f172a",
                  marginBottom: "8px"
                }}
              >
                Item
              </label>

              <select
                value={selectedItemId}
                onChange={e => setSelectedItemId(e.target.value)}
                style={{
                  width: "100%",
                  padding: "12px 14px",
                  border: "1px solid #cbd5e1",
                  borderRadius: "8px",
                  background: "#fff",
                  color: "#0f172a",
                  fontSize: "14px",
                  outline: "none"
                }}
              >
                <option value="">Select item</option>
                {items.map(item => (
                  <option key={item.id} value={item.id}>
                    {item.itemName} ({item.quantity ?? 0})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label
                style={{
                  display: "block",
                  fontSize: "13px",
                  fontWeight: 700,
                  color: "#0f172a",
                  marginBottom: "8px"
                }}
              >
                Quantity
              </label>

              <input
                type="number"
                min="1"
                step="1"
                placeholder="Enter quantity"
                value={quantity}
                onChange={e => setQuantity(e.target.value)}
                style={{
                  width: "100%",
                  boxSizing: "border-box",
                  padding: "12px 14px",
                  border: "1px solid #cbd5e1",
                  borderRadius: "8px",
                  backgroundColor: "#ffffff",
                  background: "#ffffff",
                  color: "#0f172a",
                  WebkitTextFillColor: "#0f172a",
                  caretColor: "#0f172a",
                  fontSize: "14px",
                  outline: "none"
                }}
              />
            </div>

            <button
              type="submit"
              disabled={saving || items.length === 0}
              style={{
                padding: "13px 28px",
                border: "none",
                borderRadius: "8px",
                background: isProcurement ? "#4f46e5" : "#1d4ed8",
                color: "#fff",
                fontSize: "14px",
                fontWeight: 700,
                cursor: saving ? "default" : "pointer",
                opacity: saving ? 0.7 : 1,
                whiteSpace: "nowrap"
              }}
            >
              {saving ? "Saving..." : isProcurement ? "Receive Stock" : "Sell Stock"}
            </button>
          </form>

          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              marginBottom: "14px"
            }}
          >
            <input
              placeholder="Search inventory..."
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
                outline: "none"
              }}
            />
          </div>

          {loading ? (
            <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8" }}>
              Loading inventory...
            </div>
          ) : filteredItems.length === 0 ? (
            <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8" }}>
              No inventory items found.
            </div>
          ) : (
            <div
              style={{
                background: "#fff",
                border: "1px solid #e2e8f0",
                borderRadius: "10px",
                overflow: "hidden"
              }}
            >
              <div style={{ overflowX: "auto" }}>
                <table
                  style={{
                    width: "100%",
                    minWidth: "760px",
                    borderCollapse: "collapse",
                    fontSize: "14px"
                  }}
                >
                  <thead>
                    <tr style={{ background: "#f8fafc" }}>
                      {[
                        "Item Name",
                        "SKU",
                        "Quantity",
                        "Unit",
                        "Threshold Quantity",
                        "Status"
                      ].map((label, index, arr) => (
                        <th
                          key={label}
                          style={{
                            ...thStyle,
                            borderRight:
                              index === arr.length - 1 ? "none" : "1px solid #e2e8f0"
                          }}
                        >
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>

                  <tbody>
                    {pagedItems.map(item => {
                      const lowStock = Number(item.quantity ?? 0) <= LOW_STOCK_LIMIT;

                      return (
                        <tr key={item.id}>
                          <td style={tdStyle}>{item.itemName || "-"}</td>

                          <td
                            style={{
                              ...tdStyle,
                              color: "#334155",
                              fontFamily: "monospace",
                              fontSize: "13px"
                            }}
                          >
                            {item.sku || "-"}
                          </td>

                          <td
                            style={{
                              ...tdStyle,
                              color: lowStock ? "#b45309" : "#0f172a",
                              fontWeight: 700
                            }}
                          >
                            {item.quantity ?? 0}
                          </td>

                          <td style={tdStyle}>{item.unit || item.unitOfMeasure || "-"}</td>

                          <td style={tdStyle}>
                            {item.thresholdQuantity ?? item.reorderLevel ?? LOW_STOCK_LIMIT}
                          </td>

                          <td
                            style={{
                              ...tdStyle,
                              borderRight: "none"
                            }}
                          >
                            <span
                              style={{
                                display: "inline-block",
                                background: lowStock ? "#fff7ed" : "#dcfce7",
                                color: lowStock ? "#c2410c" : "#15803d",
                                border: `1px solid ${
                                  lowStock ? "#fed7aa" : "#bbf7d0"
                                }`,
                                borderRadius: "999px",
                                padding: "4px 10px",
                                fontSize: "11px",
                                fontWeight: 800
                              }}
                            >
                              {lowStock ? "LOW STOCK" : item.status || "IN STOCK"}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      ) : activeAlerts.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8" }}>
          No active low-stock alerts.
        </div>
      ) : (
        <div
          style={{
            background: "#fff",
            border: "1px solid #e2e8f0",
            borderRadius: "10px",
            overflow: "hidden"
          }}
        >
          {pagedAlerts.map(alert => (
            <div
              key={alert.id}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                gap: "16px",
                padding: "14px 16px",
                borderBottom: "1px solid #f1f5f9"
              }}
            >
              <div>
                <div
                  style={{
                    color: "#0f172a",
                    fontSize: "14px",
                    fontWeight: 600
                  }}
                >
                  {alert.message}
                </div>

                <div
                  style={{
                    color: "#94a3b8",
                    fontSize: "12px",
                    marginTop: "3px"
                  }}
                >
                  {alert.createdAt
                    ? new Date(alert.createdAt).toLocaleString("en-IN")
                    : ""}
                </div>
              </div>

              <button
                onClick={() => resolveAlert(alert.id)}
                disabled={resolvingId === alert.id}
                style={{
                  padding: "7px 12px",
                  border: "1px solid #bbf7d0",
                  borderRadius: "6px",
                  background: "#f0fdf4",
                  color: "#15803d",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: resolvingId === alert.id ? "default" : "pointer",
                  whiteSpace: "nowrap"
                }}
              >
                {resolvingId === alert.id ? "Resolving..." : "Resolve"}
              </button>
            </div>
          ))}
        </div>
      )}

      {!loading && currentTotal > 0 && (
        <Pagination
          page={page}
          pageSize={pageSize}
          totalRecords={currentTotal}
          totalPages={currentMeta.totalPages}
          first={currentMeta.first}
          last={currentMeta.last}
          onPageChange={setPage}
          onPageSizeChange={size => {
            setPageSize(size);
            setPage(0);
          }}
        />
      )}
    </div>
  );
}