import { useEffect, useState } from "react";
import api from "../api/axios";
import { useAuth } from "../context/AuthContext";
import Pagination from "../components/Pagination";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";

const inputStyle = {
  width: "100%", padding: "9px 11px",
  border: "1px solid #d1d5db", borderRadius: 7,
  fontSize: 13, outline: "none",
  background: "#fff", color: "#0f172a",
  boxSizing: "border-box", fontFamily: font,
};

const STATUS_COLORS = {
  ACTIVE:   { bg: "#dcfce7", color: "#15803d" },
  INACTIVE: { bg: "#f1f5f9", color: "#64748b" },
  PENDING:  { bg: "#fef3c7", color: "#b45309" },
};

const CATEGORIES = ["Electronics", "Raw Materials", "Packaging", "Logistics", "Chemicals", "Textiles", "Food & Beverage", "Other"];
const STATUSES   = ["ACTIVE", "INACTIVE", "PENDING"];

function SupplierModal({ initial, onClose, onSave }) {
  const editing = !!initial;
  const [form, setForm] = useState({
    name:          initial?.companyName        || "",
    contactPerson: initial?.contactPersonName  || "",
    email:         initial?.email              || "",
    phone:         initial?.phone              || "",
    address:       initial?.address            || "",
    category:      initial?.businessCategory   || "",
    status:        initial?.integrationStatus  || "ACTIVE",
    rating:        initial?.rating != null ? String(Math.round(initial.rating)) : "",
    notes:         initial?.notes              || "",
  });
  const [saving, setSaving] = useState(false);
  const [err,    setErr]    = useState("");

  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }));

  const submit = async e => {
    e.preventDefault();
    setSaving(true); setErr("");
    try {
      const body = {
        name:          form.name,
        contactPerson: form.contactPerson,
        email:         form.email,
        phone:         form.phone,
        address:       form.address,
        category:      form.category,
        status:        form.status,
        rating:        form.rating ? parseInt(form.rating) : null,
        notes:         form.notes,
      };
      if (editing) await api.put(`/suppliers/${initial._id}`, body);
      else         await api.post("/suppliers", body);
      onSave();
    } catch (ex) {
      setErr(ex.response?.data?.message || "Save failed.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{
      position: "fixed", inset: 0, background: "rgba(0,0,0,0.45)",
      display: "flex", alignItems: "center", justifyContent: "center",
      zIndex: 200, fontFamily: font, padding: 16,
    }}>
      <div style={{
        background: "#fff", borderRadius: 14, width: "100%", maxWidth: 520,
        maxHeight: "90vh", overflowY: "auto", padding: "28px 28px 32px",
        boxShadow: "0 24px 64px rgba(0,0,0,0.25)",
      }}>
        <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}>
          {editing ? "Edit Supplier" : "Add Supplier"}
        </div>

        {err && (
          <div style={{
            background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
            borderRadius: 7, padding: "8px 12px", marginBottom: 14, fontSize: 13,
          }}>{err}</div>
        )}

        <form onSubmit={submit}>
          {[
            ["name",          "Company name",   "text"],
            ["contactPerson", "Contact person", "text"],
            ["email",         "Email",          "email"],
            ["phone",         "Phone",          "text"],
            ["address",       "Address",        "text"],
          ].map(([k, label, type]) => (
            <div key={k} style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>{label}</label>
              <input type={type} value={form[k]} onChange={set(k)} style={inputStyle} />
            </div>
          ))}

          <div style={{ display: "flex", gap: 14, marginBottom: 14 }}>
            <div style={{ flex: 1 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Category</label>
              <select value={form.category} onChange={set("category")} style={{ ...inputStyle, appearance: "auto" }}>
                <option value="">Select</option>
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Status</label>
              <select value={form.status} onChange={set("status")} style={{ ...inputStyle, appearance: "auto" }}>
                {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
          </div>

          <div style={{ marginBottom: 14 }}>
            <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Rating (1–5)</label>
            <input type="number" min="1" max="5" value={form.rating} onChange={set("rating")}
              placeholder="Optional" style={inputStyle} />
          </div>

          <div style={{ marginBottom: 20 }}>
            <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Notes</label>
            <textarea value={form.notes} onChange={set("notes")} rows={3}
              style={{ ...inputStyle, resize: "vertical" }} />
          </div>

          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
            <button type="button" onClick={onClose} style={{
              padding: "9px 20px", borderRadius: 7, border: "1px solid #d1d5db",
              background: "#fff", color: "#374151", fontSize: 13,
              cursor: "pointer", fontFamily: font, fontWeight: 500,
            }}>Cancel</button>
            <button type="submit" disabled={saving} style={{
              padding: "9px 22px", borderRadius: 7, border: "none",
              background: "#6d28d9", color: "#fff", fontSize: 13,
              cursor: saving ? "default" : "pointer", fontFamily: font, fontWeight: 600,
            }}>
              {saving ? "Saving..." : "Save"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function Suppliers() {
  const { role }  = useAuth();
  const canEdit   = ["ADMIN", "MANAGER", "PROCUREMENT"].includes(role);
  const canDelete = ["ADMIN", "MANAGER"].includes(role);

  const [suppliers, setSuppliers] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState("");
  const [modal,     setModal]     = useState(null);
  const [confirm,   setConfirm]   = useState(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 0, first: true, last: true });
  const pagedSuppliers = suppliers;

  const load = async () => {
    try {
      const res = await api.get("/suppliers", { params: { page, size: pageSize, sort: "createdAt,desc" } });
      setSuppliers(res.data.content || []);
      setPageMeta(res.data);
    } catch {
      setError("Failed to load suppliers.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [page, pageSize]);

  const handleDelete = async id => {
    try {
      await api.delete(`/suppliers/${id}`);
      setConfirm(null);
      load();
    } catch {
      setError("Delete failed.");
    }
  };

  const handleStatus = async (id, status) => {
    try {
      await api.put(`/suppliers/${id}/status?status=${status}`);
      load();
    } catch {
      setError("Status update failed.");
    }
  };

  return (
    <div style={{ padding: "28px 32px", fontFamily: font, color: "#0f172a" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 24 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700 }}>Suppliers</div>
          <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>{suppliers.length} registered</div>
        </div>
        {canEdit && (
          <button onClick={() => setModal("create")} style={{
            padding: "9px 20px", background: "#6d28d9", color: "#fff",
            border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600,
            cursor: "pointer", fontFamily: font,
          }}>
            + Add Supplier
          </button>
        )}
      </div>

      {error && (
        <div style={{
          background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
          borderRadius: 8, padding: "10px 14px", marginBottom: 16, fontSize: 13,
        }}>{error}</div>
      )}

      {loading ? (
        <div style={{ color: "#94a3b8", padding: 32, textAlign: "center" }}>Loading...</div>
      ) : suppliers.length === 0 ? (
        <div style={{
          background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0",
          padding: "48px 32px", textAlign: "center", color: "#94a3b8",
        }}>
          No suppliers registered yet.
        </div>
      ) : (
        <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", overflow: "hidden" }}>
          {/* ↓ scroll wrapper — card scrolls, page does not */}
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", minWidth: "820px", borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0" }}>
                  {["Company", "Contact", "Category", "Email / Phone", "Status", "Rating", "Actions"].map(h => (
                    <th key={h} style={{ textAlign: "left", padding: "10px 14px", fontSize: 11, color: "#94a3b8", fontWeight: 600, whiteSpace: "nowrap" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {pagedSuppliers.map((s, i) => (
                  <tr key={s._id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <td style={{ padding: "12px 14px", fontWeight: 600 }}>
                      {s.companyName}
                      {s.isLocked && (
                        <span style={{ marginLeft: 6, fontSize: 10, background: "#fee2e2", color: "#b91c1c", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>LOCKED</span>
                      )}
                    </td>
                    <td style={{ padding: "12px 14px", color: "#334155" }}>{s.contactPersonName || "-"}</td>
                    <td style={{ padding: "12px 14px", color: "#64748b" }}>{s.businessCategory || "-"}</td>
                    <td style={{ padding: "12px 14px", color: "#64748b" }}>
                      <div>{s.email || "-"}</div>
                      {s.phone && <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>{s.phone}</div>}
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      {canEdit ? (
                        <select
                          value={s.integrationStatus || "ACTIVE"}
                          onChange={e => handleStatus(s._id, e.target.value)}
                          style={{
                            fontSize: 12, padding: "3px 7px", borderRadius: 6,
                            border: "1px solid #d1d5db", cursor: "pointer",
                            background: "#fff", color: "#0f172a", fontFamily: font,
                          }}
                        >
                          {STATUSES.map(st => <option key={st} value={st}>{st}</option>)}
                        </select>
                      ) : (
                        (() => {
                          const c = STATUS_COLORS[s.integrationStatus] || { bg: "#f1f5f9", color: "#64748b" };
                          return (
                            <span style={{ fontSize: 11, fontWeight: 600, padding: "3px 8px", borderRadius: 6, background: c.bg, color: c.color }}>
                              {s.integrationStatus || "-"}
                            </span>
                          );
                        })()
                      )}
                    </td>
                    <td style={{ padding: "12px 14px", color: "#64748b" }}>
                      {s.rating != null ? `${Number(s.rating).toFixed(1)} / 5` : "-"}
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      <div style={{ display: "flex", gap: 6, alignItems: "center", flexWrap: "nowrap" }}>
                        {canEdit && (
                          <button onClick={() => setModal(s)} style={{
                            padding: "5px 12px", fontSize: 12, border: "1px solid #e2e8f0",
                            borderRadius: 6, background: "#fff", color: "#334151",
                            cursor: "pointer", fontFamily: font, whiteSpace: "nowrap",
                          }}>Edit</button>
                        )}
                        {canDelete && (
                          confirm?.id === s._id ? (
                            <span style={{ fontSize: 12, color: "#374151", whiteSpace: "nowrap" }}>
                              Sure?&nbsp;
                              <button onClick={() => handleDelete(s._id)}
                                style={{ color: "#b91c1c", background: "none", border: "none", cursor: "pointer", fontWeight: 600, fontSize: 12 }}>Yes</button>
                              &nbsp;/&nbsp;
                              <button onClick={() => setConfirm(null)}
                                style={{ color: "#64748b", background: "none", border: "none", cursor: "pointer", fontSize: 12 }}>No</button>
                            </span>
                          ) : (
                            <button onClick={() => setConfirm({ id: s._id })} style={{
                              padding: "5px 12px", fontSize: 12, border: "1px solid #fecaca",
                              borderRadius: 6, background: "#fff", color: "#b91c1c",
                              cursor: "pointer", fontFamily: font, whiteSpace: "nowrap",
                            }}>Delete</button>
                          )
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!loading && <Pagination page={page} pageSize={pageSize} totalRecords={pageMeta.totalElements} totalPages={pageMeta.totalPages} first={pageMeta.first} last={pageMeta.last} onPageChange={setPage} onPageSizeChange={size => { setPageSize(size); setPage(0); }} />}

      {modal && (
        <SupplierModal
          initial={modal === "create" ? null : modal}
          onClose={() => setModal(null)}
          onSave={() => { setModal(null); load(); }}
        />
      )}
    </div>
  );
}
