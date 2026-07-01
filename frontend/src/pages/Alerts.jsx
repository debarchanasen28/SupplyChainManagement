import { useState, useEffect, useCallback } from "react";
import axios from "../api/axios";
import Pagination from "../components/Pagination";

const API = "http://localhost:8080/api";

const TYPE_STYLES = {
  LOW_STOCK: { bg: "#fff7ed", text: "#c2410c", border: "#fed7aa" },
  ORDER_RECEIVED: { bg: "#eff6ff", text: "#1d4ed8", border: "#bfdbfe" },
  ORDER_CONFIRMED: { bg: "#f0fdf4", text: "#15803d", border: "#bbf7d0" },
  ORDER_CANCELLED: { bg: "#fef2f2", text: "#b91c1c", border: "#fecaca" },
  SHIPMENT_IN_TRANSIT: { bg: "#f5f3ff", text: "#6d28d9", border: "#ddd6fe" },
  SHIPMENT_DELIVERED: { bg: "#f0fdf4", text: "#15803d", border: "#bbf7d0" },
  STOCK_CHECK_SENT: { bg: "#fff7ed", text: "#c2410c", border: "#fed7aa" },
  STOCK_CHECK_ACCEPTED: { bg: "#f0fdf4", text: "#15803d", border: "#bbf7d0" },
  STOCK_CHECK_REJECTED: { bg: "#fef2f2", text: "#b91c1c", border: "#fecaca" },
  SYSTEM: { bg: "#f8fafc", text: "#475569", border: "#e2e8f0" },
};

function typeMeta(type) {
  return TYPE_STYLES[type] || { bg: "#f8fafc", text: "#475569", border: "#e2e8f0" };
}

function TypeBadge({ type }) {
  const m = typeMeta(type);
  return (
    <span style={{
      background: m.bg,
      color: m.text,
      border: `1px solid ${m.border}`,
      borderRadius: "999px",
      padding: "4px 10px",
      fontSize: "11px",
      fontWeight: 800,
      whiteSpace: "nowrap"
    }}>
      {(type || "SYSTEM").replace(/_/g, " ")}
    </span>
  );
}

function StatusBadge({ status }) {
  const active = status === "ACTIVE";
  return (
    <span style={{
      display: "inline-block",
      background: active ? "#fff7ed" : "#dcfce7",
      color: active ? "#c2410c" : "#15803d",
      border: `1px solid ${active ? "#fed7aa" : "#bbf7d0"}`,
      borderRadius: "8px",
      padding: "5px 10px",
      fontSize: "11px",
      fontWeight: 800
    }}>
      {status || "-"}
    </span>
  );
}

export default function Alerts() {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState("active");
  const [search, setSearch] = useState("");
  const [resolveAllConfirm, setResolveAllConfirm] = useState(false);
  const [resolveLoading, setResolveLoading] = useState({});
  const [resolveAllLoading, setResolveAllLoading] = useState(false);
  const [generateLoading, setGenerateLoading] = useState(false);
  const [generateMsg, setGenerateMsg] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [pageMeta, setPageMeta] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true
  });

  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role");
  const headers = { Authorization: `Bearer ${token}` };
  const isAdmin = role === "ADMIN" || role === "MANAGER";

  const TABS = [
    { key: "active", label: "Active" },
    { key: "all", label: "All Alerts" },
  ];

  const fetchAlerts = useCallback(() => {
    setLoading(true);
    setError("");

    const url = tab === "active" ? `${API}/alerts/active` : `${API}/alerts`;

    axios.get(url, {
      headers,
      params: { page, size: pageSize, sort: "createdAt,desc" }
    })
      .then(res => {
        setAlerts(res.data.content || []);
        setPageMeta(res.data);
        setLoading(false);
      })
      .catch(() => {
        setError("Failed to load alerts.");
        setLoading(false);
      });
  }, [tab, page, pageSize]);

  useEffect(() => {
    fetchAlerts();
  }, [fetchAlerts]);

  const resolveAlert = alert => {
    const key = alert.id;
    setResolveLoading(prev => ({ ...prev, [key]: true }));

    axios.put(`${API}/alerts/${alert.id}/resolve`, {}, { headers })
      .then(res => {
        setAlerts(prev => prev.map(a => a.id === alert.id ? res.data : a));
        setResolveLoading(prev => ({ ...prev, [key]: false }));
      })
      .catch(() => setResolveLoading(prev => ({ ...prev, [key]: false })));
  };

  const resolveAll = () => {
    if (!resolveAllConfirm) {
      setResolveAllConfirm(true);
      return;
    }

    setResolveAllLoading(true);

    axios.put(`${API}/alerts/resolve-all`, {}, { headers })
      .then(() => {
        fetchAlerts();
        setResolveAllConfirm(false);
        setResolveAllLoading(false);
      })
      .catch(() => {
        setResolveAllConfirm(false);
        setResolveAllLoading(false);
      });
  };

  const generateAlerts = () => {
    setGenerateLoading(true);
    setGenerateMsg("");

    axios.post(`${API}/alerts/generate`, {}, { headers })
      .then(() => {
        setGenerateMsg("System alerts generated.");
        setGenerateLoading(false);
        fetchAlerts();
      })
      .catch(() => {
        setGenerateMsg("Failed to generate alerts.");
        setGenerateLoading(false);
      });
  };

  const activeAlerts = alerts.filter(a => a.status === "ACTIVE");

  const filtered = alerts.filter(a => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();

    return (a.type || "").toLowerCase().includes(q)
      || (a.message || "").toLowerCase().includes(q)
      || (a.targetRole || "").toLowerCase().includes(q)
      || (a.referenceId || "").toLowerCase().includes(q);
  });

  const pagedAlerts = filtered;

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
    borderRight: "1px solid #e2e8f0",
    verticalAlign: "middle"
  };

  return (
    <div style={{ padding: "32px", background: "#f8fafc", minHeight: "100vh"}}>
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        marginBottom: "28px"
      }}>
        <div style={{ textAlign: "center", width: "100%" }}>
          <h1 style={{
            fontSize: "26px",
            fontWeight: 800,
            color: "#0f172a",
            margin: 0,
            letterSpacing: "1px"
          }}>
            Alerts
          </h1>

          <p style={{
            fontSize: "14px",
            color: "#64748b",
            margin: "6px 0 0"
          }}>
            Monitor and resolve system alerts
          </p>
        </div>

        <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
          {isAdmin && (
            <button
              onClick={generateAlerts}
              disabled={generateLoading}
              style={{
                padding: "10px 16px",
                background: "#fff",
                color: "#475569",
                border: "1px solid #e2e8f0",
                borderRadius: "8px",
                fontSize: "13px",
                fontWeight: 700,
                cursor: generateLoading ? "default" : "pointer"
              }}
            >
              {generateLoading ? "Generating..." : "Generate Alerts"}
            </button>
          )}

          {activeAlerts.length > 0 && (
            resolveAllConfirm ? (
              <>
                <span style={{ fontSize: "13px", color: "#475569" }}>Resolve all?</span>

                <button
                  onClick={resolveAll}
                  disabled={resolveAllLoading}
                  style={{
                    padding: "10px 16px",
                    background: "#15803d",
                    color: "#fff",
                    border: "none",
                    borderRadius: "8px",
                    fontSize: "13px",
                    fontWeight: 700,
                    cursor: "pointer"
                  }}
                >
                  Yes
                </button>

                <button
                  onClick={() => setResolveAllConfirm(false)}
                  style={{
                    padding: "10px 16px",
                    background: "#fff",
                    color: "#475569",
                    border: "1px solid #e2e8f0",
                    borderRadius: "8px",
                    fontSize: "13px",
                    fontWeight: 700,
                    cursor: "pointer"
                  }}
                >
                  No
                </button>
              </>
            ) : (
              <button
                onClick={resolveAll}
                style={{
                  padding: "10px 16px",
                  background: "#f0fdf4",
                  color: "#15803d",
                  border: "1px solid #bbf7d0",
                  borderRadius: "8px",
                  fontSize: "13px",
                  fontWeight: 700,
                  cursor: "pointer"
                }}
              >
                Resolve All
              </button>
            )
          )}
        </div>
      </div>

      {generateMsg && (
        <div style={{
          background: generateMsg.includes("Failed") ? "#fef2f2" : "#f0fdf4",
          border: `1px solid ${generateMsg.includes("Failed") ? "#fecaca" : "#bbf7d0"}`,
          borderRadius: "10px",
          padding: "12px 14px",
          marginBottom: "18px",
          color: generateMsg.includes("Failed") ? "#b91c1c" : "#15803d",
          fontSize: "13px"
        }}>
          {generateMsg}
        </div>
      )}

      <div style={{
        display: "flex",
        gap: "28px",
        borderBottom: "1px solid #e2e8f0",
        marginBottom: "26px"
      }}>
        {TABS.map(t => (
          <button
            key={t.key}
            onClick={() => {
              setTab(t.key);
              setPage(0);
            }}
            style={{
              padding: "10px 0",
              border: "none",
              background: "transparent",
              borderBottom: tab === t.key ? "3px solid #4f46e5" : "3px solid transparent",
              color: tab === t.key ? "#4f46e5" : "#64748b",
              fontWeight: tab === t.key ? 700 : 500,
              fontSize: "15px",
              cursor: "pointer",
              marginBottom: "-1px"
            }}
          >
            {t.label}
            {t.key === "active" && activeAlerts.length > 0 ? ` (${activeAlerts.length})` : ""}
          </button>
        ))}
      </div>

      <div style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        padding: "20px",
        marginBottom: "24px"
      }}>
        <div style={{
          display: "flex",
          justifyContent: "flex-end",
          marginBottom: "20px"
        }}>
          <input
            placeholder="Search alerts..."
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
            Loading alerts...
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
          <div style={{ textAlign: "center", padding: "60px", color: "#94a3b8" }}>
            {tab === "active" ? "No active alerts." : "No alerts found."}
          </div>
        ) : (
          <div style={{
            border: "1px solid #e2e8f0",
            borderRadius: "10px",
            overflow: "hidden"
          }}>
            <div style={{ overflowX: "auto" }}>
              <table style={{
                width: "100%",
                minWidth: "980px",
                borderCollapse: "collapse",
                fontSize: "14px"
              }}>
                <thead>
                  <tr style={{ background: "#f8fafc" }}>
                    {["Type", "Message", "Reference", "Target Role", "Date", "Status", "Action"].map((label, index, arr) => (
                      <th
                        key={label}
                        style={{
                          ...thStyle,
                          borderRight: index === arr.length - 1 ? "none" : "1px solid #e2e8f0"
                        }}
                      >
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>

                <tbody>
                  {pagedAlerts.map(alert => {
                    const isResolved = alert.status === "RESOLVED";
                    const isLoading = resolveLoading[alert.id];

                    return (
                      <tr key={alert.id}>
                        <td style={tdStyle}>
                          <TypeBadge type={alert.type} />
                        </td>

                        <td style={{
                          ...tdStyle,
                          textAlign: "center",
                          color: isResolved ? "#94a3b8" : "#0f172a",
                          lineHeight: 1.5,
                          minWidth: "280px"
                        }}>
                          {alert.message || "-"}
                        </td>

                        <td style={{
                          ...tdStyle,
                          color: "#64748b",
                          fontFamily: "monospace",
                          fontSize: "12px"
                        }}>
                          {alert.referenceId || "-"}
                        </td>

                        <td style={tdStyle}>
                          {alert.targetRole || "-"}
                        </td>

                        <td style={{ ...tdStyle, color: "#475569" }}>
                          {alert.createdAt
                            ? new Date(alert.createdAt).toLocaleString("en-IN", {
                                day: "2-digit",
                                month: "short",
                                year: "numeric",
                                hour: "2-digit",
                                minute: "2-digit"
                              })
                            : "-"}
                        </td>

                        <td style={tdStyle}>
                          <StatusBadge status={alert.status} />
                        </td>

                        <td style={{ ...tdStyle, borderRight: "none" }}>
                          {!isResolved ? (
                            <button
                              onClick={() => resolveAlert(alert)}
                              disabled={isLoading}
                              style={{
                                padding: "8px 14px",
                                background: "#f0fdf4",
                                color: "#15803d",
                                border: "1px solid #bbf7d0",
                                borderRadius: "8px",
                                fontSize: "12px",
                                fontWeight: 700,
                                cursor: isLoading ? "default" : "pointer",
                                opacity: isLoading ? 0.6 : 1
                              }}
                            >
                              {isLoading ? "Resolving..." : "Resolve"}
                            </button>
                          ) : (
                            <span style={{ color: "#94a3b8", fontSize: "12px", fontWeight: 600 }}>
                              Resolved
                            </span>
                          )}
                        </td>
                      </tr>
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
      </div>

      <div style={{
        display: "flex",
        alignItems: "center",
        gap: "14px",
        background: "#f8f7ff",
        border: "1px solid #ddd6fe",
        borderRadius: "12px",
        padding: "18px 20px"
      }}>
        <div style={{
          width: "42px",
          height: "42px",
          borderRadius: "50%",
          background: "#4f46e5",
          color: "#fff",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: "24px",
          fontWeight: 800
        }}>
          i
        </div>

        <div>
          <div style={{ color: "#4f46e5", fontSize: "15px", fontWeight: 800 }}>
            About Alerts
          </div>
          <div style={{ color: "#64748b", fontSize: "13px", marginTop: "4px" }}>
            Alerts are generated automatically for system events, stock changes, shipment updates, and order lifecycle notifications.
          </div>
        </div>
      </div>
    </div>
  );
}