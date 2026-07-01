import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "../api/axios";
import "./IntegrationLogs.css";
import Pagination from "../components/Pagination";

const API = "http://localhost:8080/api";

const formatTime = value => {
  if (!value) return "-";
  const d = new Date(value);
  return isNaN(d.getTime()) ? "-" : d.toLocaleString("en-IN");
};

// Accept either a paginated payload ({ content: [...] }) or a raw array.
const toList = data => (Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : []);

// Never render a raw object/array in JSX — stringify it safely.
const asText = value => {
  if (value === null || value === undefined) return "";
  if (typeof value === "object") {
    try { return JSON.stringify(value, null, 2); } catch { return String(value); }
  }
  return String(value);
};

const statusStyle = status => ({
  SUCCESS: { background: "#f0fdf4", color: "#15803d" },
  FAILED: { background: "#fef2f2", color: "#b91c1c" },
  PENDING: { background: "#fff7ed", color: "#c2410c" },
  RETRY: { background: "#fff7ed", color: "#c2410c" }
}[status] || { background: "#f1f5f9", color: "#475569" });

const traceLabel = log => {
  const value = `${log.messageType || ""} ${log.iFlowName || ""} ${log.direction || ""}`.toUpperCase();
  if (value.includes("DELIVER")) return "Delivered";
  if (value.includes("SHIPMENT")) return "Shipment";
  if (value.includes("APPROVAL")) return "Approval";
  if (value.includes("STOCK")) return "Stock Offer";
  if (value.includes("PO") && value.includes("INBOUND")) return "PO Received";
  if (value.includes("PO")) return "PO Sent";
  return log.messageType || log.iFlowName || "Integration Event";
};

function StatusBadge({ status }) {
  return <span className="log-status" style={statusStyle(status)}>{status || "UNKNOWN"}</span>;
}

function TraceModal({ correlationId, logs, loading, error, onClose }) {
  return (
    <div className="trace-backdrop" onMouseDown={onClose}>
      <div className="trace-modal" onMouseDown={event => event.stopPropagation()}>
        <div className="trace-header">
          <div>
            <h2>Correlation Trace</h2>
            <p>{correlationId}</p>
          </div>
          <button type="button" className="trace-close" onClick={onClose} aria-label="Close">×</button>
        </div>

        {loading ? <div className="trace-empty">Loading trace...</div>
          : error ? <div className="trace-error">{error}</div>
            : logs.length === 0 ? <div className="trace-empty">No events found for this correlation ID.</div>
              : <div className="trace-timeline">
                  {logs.map((log, index) => (
                    <div className="trace-event" key={log.id || `${log.createdAt}-${index}`}>
                      <div className="trace-marker"><span />{index < logs.length - 1 && <i />}</div>
                      <div className="trace-content">
                        <div className="trace-title-row">
                          <strong>{traceLabel(log)}</strong>
                          <StatusBadge status={log.status} />
                        </div>
                        <div className="trace-meta">{formatTime(log.createdAt)} · {log.sourceSystem || "-"} → {log.targetSystem || "-"}</div>
                        {(log.errorMessage || log.responsePayload) && <p>{asText(log.errorMessage || log.responsePayload)}</p>}
                      </div>
                    </div>
                  ))}
                </div>}
      </div>
    </div>
  );
}

export default function IntegrationLogs() {
  const [logs, setLogs] = useState([]);
  const [failedLogs, setFailedLogs] = useState([]);
  const [stats, setStats] = useState({ totalMessages: 0, successful: 0, failed: 0, pending: 0, successRate: 0 });
  const [filters, setFilters] = useState({ eventType: "all", status: "all", source: "all", target: "all", correlationId: "" });
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [errorPage, setErrorPage] = useState(0);
  const [errorPageSize, setErrorPageSize] = useState(10);
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 0, first: true, last: true });
  const [failedMeta, setFailedMeta] = useState({ totalElements: 0, totalPages: 0, first: true, last: true });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [trace, setTrace] = useState(null);
  const token = localStorage.getItem("token");

  const loadData = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const [logsResponse, failedResponse, statsResponse] = await Promise.all([
        axios.get(`${API}/logs`, { headers, params: { page, size: pageSize, sort: "createdAt,desc" } }),
        axios.get(`${API}/logs/status/FAILED`, { headers, params: { page: errorPage, size: errorPageSize, sort: "createdAt,desc" } }),
        axios.get(`${API}/logs/stats`, { headers })
      ]);
      const safeMeta = d => ({
        totalElements: d?.totalElements ?? toList(d).length,
        totalPages: d?.totalPages ?? 0,
        first: d?.first ?? true,
        last: d?.last ?? true,
      });
      setLogs(toList(logsResponse.data));
      setPageMeta(safeMeta(logsResponse.data));
      setFailedLogs(toList(failedResponse.data));
      setFailedMeta(safeMeta(failedResponse.data));
      setStats(statsResponse.data || {});
      setError("");
    } catch (err) {
      console.error("Failed to load integration logs:", err.response?.data || err.message, err);
      setError(err.response?.data?.error || err.response?.data?.message || "Failed to load integration logs.");
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [token, page, pageSize, errorPage, errorPageSize]);

  useEffect(() => {
    const initialLoad = window.setTimeout(() => loadData(true), 0);
    const interval = window.setInterval(() => loadData(false), 10000);
    return () => {
      window.clearTimeout(initialLoad);
      window.clearInterval(interval);
    };
  }, [loadData]);

  const options = useMemo(() => ({
    eventTypes: [...new Set(logs.map(log => log.messageType).filter(Boolean))].sort(),
    statuses: [...new Set(logs.map(log => log.status).filter(Boolean))].sort(),
    sources: [...new Set(logs.map(log => log.sourceSystem).filter(Boolean))].sort(),
    targets: [...new Set(logs.map(log => log.targetSystem).filter(Boolean))].sort()
  }), [logs]);

  const filteredLogs = useMemo(() => {
    const query = filters.correlationId.trim().toLowerCase();
    return logs.filter(log =>
      (filters.eventType === "all" || log.messageType === filters.eventType)
      && (filters.status === "all" || log.status === filters.status)
      && (filters.source === "all" || log.sourceSystem === filters.source)
      && (filters.target === "all" || log.targetSystem === filters.target)
      && (!query || (log.messageId || "").toLowerCase().includes(query))
    );
  }, [logs, filters]);

  const pageLogs = filteredLogs;
  const pagedFailedLogs = failedLogs;

  const openTrace = async log => {
    const correlationId = log.messageId;
    if (!correlationId) return;
    setTrace({ correlationId, logs: [], loading: true, error: "" });
    try {
      const response = await axios.get(`${API}/logs/${encodeURIComponent(correlationId)}`, {
        headers: { Authorization: `Bearer ${token}` },
        params: { page: 0, size: 100, sort: "createdAt,asc" }
      });
      const sorted = [...toList(response.data)].sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0));
      setTrace({ correlationId, logs: sorted, loading: false, error: "" });
    } catch (err) {
      console.error("Failed to load correlation trace:", err.response?.data || err.message, err);
      setTrace({ correlationId, logs: [], loading: false, error: "Failed to load correlation trace." });
    }
  };

  const updateFilter = (key, value) => {
    setFilters(current => ({ ...current, [key]: value }));
    setPage(0);
  };
  const kpis = [
    ["Total Messages", stats.totalMessages ?? 0, "#0f172a"],
    ["Successful", stats.successful ?? 0, "#15803d"],
    ["Failed", stats.failed ?? 0, "#b91c1c"],
    ["Pending", stats.pending ?? 0, "#c2410c"],
    ["Success Rate", `${Number(stats.successRate || 0).toFixed(1)}%`, "#0f766e"]
  ];

  return (
    <div className="logs-page">
      <div className="logs-heading">
        <div style={{ textAlign: "center", width: "100%" }}><h1>Integration Logs</h1><p>Message monitoring and correlation traces</p></div>
        <span>Refreshes every 10 seconds</span>
      </div>

      <div className="logs-kpis">
        {kpis.map(([label, value, color]) => <div className="logs-kpi" key={label}><span>{label}</span><strong style={{ color }}>{value}</strong></div>)}
      </div>

      <section className="logs-section">
        <div className="logs-section-title"><h2>Message Log</h2><span>{filteredLogs.length} messages</span></div>
        <div className="logs-filters">
          <select value={filters.eventType} onChange={event => updateFilter("eventType", event.target.value)}><option value="all">All Event Types</option>{options.eventTypes.map(value => <option key={value}>{value}</option>)}</select>
          <select value={filters.status} onChange={event => updateFilter("status", event.target.value)}><option value="all">All Statuses</option>{options.statuses.map(value => <option key={value}>{value}</option>)}</select>
          <select value={filters.source} onChange={event => updateFilter("source", event.target.value)}><option value="all">All Sources</option>{options.sources.map(value => <option key={value}>{value}</option>)}</select>
          <select value={filters.target} onChange={event => updateFilter("target", event.target.value)}><option value="all">All Targets</option>{options.targets.map(value => <option key={value}>{value}</option>)}</select>
          <input value={filters.correlationId} onChange={event => updateFilter("correlationId", event.target.value)} placeholder="Search correlation ID..." />
        </div>

        {error && <div className="logs-error">{error}</div>}
        {loading ? <div className="logs-empty">Loading integration logs...</div>
          : pageLogs.length === 0 ? <div className="logs-empty">No matching messages found.</div>
            : <div className="logs-table-wrap"><table className="logs-table">
                <thead><tr>{["Timestamp", "Correlation ID", "Order ID", "Event Type", "Source", "Target", "Status", "Message"].map(value => <th key={value}>{value}</th>)}</tr></thead>
                <tbody>{pageLogs.map((log, index) => <tr key={log.id || index} onClick={() => openTrace(log)} className={log.messageId ? "clickable" : ""}>
                  <td>{formatTime(log.createdAt)}</td><td className="mono">{log.messageId || "-"}</td><td>{log.orderId || "-"}</td><td>{log.messageType || log.iFlowName || "-"}</td><td>{log.sourceSystem || "-"}</td><td>{log.targetSystem || "-"}</td><td><StatusBadge status={log.status} /></td><td className="log-message" title={asText(log.errorMessage || log.responsePayload || log.requestPayload)}>{asText(log.errorMessage || log.responsePayload || log.requestPayload) || "-"}</td>
                </tr>)}</tbody>
              </table></div>}

        {!loading && <Pagination page={page} pageSize={pageSize} totalRecords={pageMeta.totalElements} totalPages={pageMeta.totalPages} first={pageMeta.first} last={pageMeta.last} onPageChange={setPage} onPageSizeChange={size => { setPageSize(size); setPage(0); }} />}
      </section>

      <section className="logs-section error-section">
        <div className="logs-section-title"><h2>Failed Messages</h2><span>{failedLogs.length} errors</span></div>
        {failedLogs.length === 0 ? <div className="logs-empty">No failed messages.</div>
          : <div className="logs-table-wrap"><table className="logs-table error-table">
              <thead><tr>{["Time", "Correlation ID", "Error Message", "Retry Count", "MPL ID"].map(value => <th key={value}>{value}</th>)}</tr></thead>
              <tbody>{pagedFailedLogs.map((log, index) => <tr key={log.id || index} onClick={() => openTrace(log)} className={log.messageId ? "clickable" : ""}><td>{formatTime(log.createdAt)}</td><td className="mono">{log.messageId || "-"}</td><td>{asText(log.errorMessage) || "Unknown integration error"}</td><td>{log.retryCount ?? 0}</td><td className="mono">{log.mplId || log.logId || "-"}</td></tr>)}</tbody>
            </table></div>}
        <Pagination page={errorPage} pageSize={errorPageSize} totalRecords={failedMeta.totalElements} totalPages={failedMeta.totalPages} first={failedMeta.first} last={failedMeta.last} onPageChange={setErrorPage} onPageSizeChange={size => { setErrorPageSize(size); setErrorPage(0); }} />
      </section>

      {trace && <TraceModal {...trace} onClose={() => setTrace(null)} />}
    </div>
  );
}
