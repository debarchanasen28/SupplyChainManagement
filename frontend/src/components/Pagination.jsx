export const pageContent = data => Array.isArray(data) ? data : data?.content || [];

export default function Pagination({ page, pageSize = 10, totalRecords, totalPages, first, last, onPageChange, onPageSizeChange }) {
  const pages = totalPages ?? Math.ceil((totalRecords || 0) / pageSize);
  const isFirst = first ?? page <= 0;
  const isLast = last ?? page >= pages - 1;
  if (!pages || pages <= 1) return null;

  const buttonStyle = disabled => ({
    padding: "6px 14px",
    border: "1px solid #e2e8f0",
    borderRadius: "6px",
    background: disabled ? "#f1f5f9" : "#fff",
    color: disabled ? "#cbd5e1" : "#475569",
    fontSize: "13px",
    fontWeight: 600,
    cursor: disabled ? "default" : "pointer"
  });

  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: "10px", marginTop: "16px" }}>
      {onPageSizeChange && <select value={pageSize} onChange={event => onPageSizeChange(Number(event.target.value))} style={{ padding: "6px 8px", border: "1px solid #e2e8f0", borderRadius: "6px", background: "#fff", color: "#475569" }}><option value={10}>10</option><option value={20}>20</option><option value={50}>50</option></select>}
      <button type="button" disabled={isFirst} onClick={() => onPageChange(Math.max(0, page - 1))} style={buttonStyle(isFirst)}>Previous</button>
      <span style={{ fontSize: "13px", color: "#64748b" }}>Page {page + 1} of {pages}</span>
      <button type="button" disabled={isLast} onClick={() => onPageChange(Math.min(pages - 1, page + 1))} style={buttonStyle(isLast)}>Next</button>
    </div>
  );
}
