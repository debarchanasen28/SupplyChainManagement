import { useEffect, useState } from "react";
import api from "../api/axios";
import Pagination from "../components/Pagination";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";

const inputStyle = {
  width: "100%", padding: "9px 11px",
  border: "1px solid #d1d5db", borderRadius: 7,
  fontSize: 13, outline: "none",
  background: "#fff", color: "#0f172a",
  boxSizing: "border-box", fontFamily: font,
};

const ROLES = ["VENDOR", "PROCUREMENT", "ADMIN", "MANAGER"];

const ROLE_COLORS = {
  VENDOR:      { bg: "#dbeafe", color: "#1d4ed8" },
  PROCUREMENT: { bg: "#ede9fe", color: "#6d28d9" },
  ADMIN:       { bg: "#ccfbf1", color: "#0f766e" },
  MANAGER:     { bg: "#fef9c3", color: "#854d0e" },
};

function CreateUserModal({ onClose, onSave }) {
  const [form,   setForm]   = useState({ name: "", email: "", password: "", role: "VENDOR" });
  const [saving, setSaving] = useState(false);
  const [err,    setErr]    = useState("");

  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }));

  const submit = async e => {
    e.preventDefault();
    setSaving(true); setErr("");
    try {
      await api.post("/auth/register", form);
      onSave();
    } catch (ex) {
      setErr(ex.response?.data?.message || "Failed to create user.");
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
        background: "#fff", borderRadius: 14, width: "100%", maxWidth: 440,
        padding: "28px 28px 32px", boxShadow: "0 24px 64px rgba(0,0,0,0.25)",
      }}>
        <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}>Create User</div>

        {err && (
          <div style={{
            background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
            borderRadius: 7, padding: "8px 12px", marginBottom: 14, fontSize: 13,
          }}>{err}</div>
        )}

        <form onSubmit={submit}>
          {[
            ["name",     "Full name",     "text"],
            ["email",    "Email address", "email"],
            ["password", "Password",      "password"],
          ].map(([k, label, type]) => (
            <div key={k} style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>{label}</label>
              <input type={type} required value={form[k]} onChange={set(k)} style={inputStyle} />
            </div>
          ))}

          <div style={{ marginBottom: 20 }}>
            <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Role</label>
            <select value={form.role} onChange={set("role")} style={{ ...inputStyle, appearance: "auto" }}>
              {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>

          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
            <button type="button" onClick={onClose} style={{
              padding: "9px 20px", borderRadius: 7, border: "1px solid #d1d5db",
              background: "#fff", color: "#374151", fontSize: 13,
              cursor: "pointer", fontFamily: font, fontWeight: 500,
            }}>Cancel</button>
            <button type="submit" disabled={saving} style={{
              padding: "9px 22px", borderRadius: 7, border: "none",
              background: "#0f766e", color: "#fff", fontSize: 13,
              cursor: saving ? "default" : "pointer", fontFamily: font, fontWeight: 600,
            }}>
              {saving ? "Creating..." : "Create User"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function UserManagement() {
  const [users,   setUsers]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState("");
  const [modal,   setModal]   = useState(false);
  const [confirm, setConfirm] = useState(null);  // { id }
  const [editing, setEditing] = useState({});    // id → role string
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 0, first: true, last: true });
  const pagedUsers = users;

  const load = async () => {
    try {
      const res = await api.get("/users", { params: { page, size: pageSize, sort: "createdAt,desc" } });
      setUsers(res.data.content || []);
      setPageMeta(res.data);
    } catch {
      setError("Failed to load users.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [page, pageSize]);

  const toggleActive = async id => {
    try {
      await api.put(`/users/${id}/status`);
      load();
    } catch {
      setError("Status update failed.");
    }
  };

  const saveRole = async (id, role) => {
    try {
      await api.put(`/users/${id}/role`, { role });
      setEditing(e => { const n = { ...e }; delete n[id]; return n; });
      load();
    } catch {
      setError("Role update failed.");
    }
  };

  const deleteUser = async id => {
    try {
      await api.delete(`/users/${id}`);
      setConfirm(null);
      load();
    } catch {
      setError("Delete failed.");
    }
  };

  return (
    <div style={{ padding: "28px 32px", fontFamily: font, color: "#0f172a" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 24 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700 }}>User Management</div>
          <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>{users.length} users</div>
        </div>
        <button onClick={() => setModal(true)} style={{
          padding: "9px 20px", background: "#0f766e", color: "#fff",
          border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600,
          cursor: "pointer", fontFamily: font,
        }}>
          + Create User
        </button>
      </div>

      {error && (
        <div style={{
          background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
          borderRadius: 8, padding: "10px 14px", marginBottom: 16, fontSize: 13,
        }}>{error}</div>
      )}

      {loading ? (
        <div style={{ color: "#94a3b8", padding: 32, textAlign: "center" }}>Loading...</div>
      ) : users.length === 0 ? (
        <div style={{
          background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0",
          padding: "48px 32px", textAlign: "center", color: "#94a3b8",
        }}>
          No users found.
        </div>
      ) : (
        <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0" }}>
                {["Name", "Email", "Role", "Status", "Actions"].map(h => (
                  <th key={h} style={{ textAlign: "left", padding: "10px 14px", fontSize: 11, color: "#94a3b8", fontWeight: 600 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pagedUsers.map((u, i) => {
                const roleEdit = editing[u._id];
                const rc = ROLE_COLORS[u.role] || { bg: "#f1f5f9", color: "#64748b" };
                return (
                  <tr key={u._id || i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <td style={{ padding: "12px 14px", fontWeight: 600 }}>
                      {u.name || "-"}
                      {u.isLocked && (
                        <span style={{ marginLeft: 6, fontSize: 10, background: "#fee2e2", color: "#b91c1c", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>LOCKED</span>
                      )}
                    </td>
                    <td style={{ padding: "12px 14px", color: "#64748b" }}>{u.email}</td>
                    <td style={{ padding: "12px 14px" }}>
                      {roleEdit !== undefined ? (
                        <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                          <select value={roleEdit}
                            onChange={e => setEditing(ed => ({ ...ed, [u._id]: e.target.value }))}
                            style={{
                              fontSize: 12, padding: "3px 7px", borderRadius: 6,
                              border: "1px solid #d1d5db", background: "#fff", color: "#0f172a", fontFamily: font,
                            }}>
                            {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                          </select>
                          <button onClick={() => saveRole(u._id, roleEdit)}
                            style={{ fontSize: 11, padding: "3px 10px", borderRadius: 5, border: "none", background: "#0f766e", color: "#fff", cursor: "pointer", fontFamily: font }}>
                            Save
                          </button>
                          <button onClick={() => setEditing(ed => { const n = { ...ed }; delete n[u._id]; return n; })}
                            style={{ fontSize: 11, padding: "3px 10px", borderRadius: 5, border: "1px solid #d1d5db", background: "#fff", color: "#374151", cursor: "pointer", fontFamily: font }}>
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <span onClick={() => setEditing(ed => ({ ...ed, [u._id]: u.role }))}
                          title="Click to change role" style={{ cursor: "pointer" }}>
                          <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 8px", borderRadius: 6, background: rc.bg, color: rc.color }}>
                            {u.role}
                          </span>
                        </span>
                      )}
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      <span style={{
                        fontSize: 11, fontWeight: 600, padding: "3px 8px", borderRadius: 6,
                        background: u.isActive ? "#dcfce7" : "#f1f5f9",
                        color:      u.isActive ? "#15803d" : "#64748b",
                      }}>
                        {u.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                        <button onClick={() => toggleActive(u._id)} style={{
                          padding: "5px 12px", fontSize: 12,
                          border: `1px solid ${u.isActive ? "#fecaca" : "#bbf7d0"}`,
                          borderRadius: 6, background: "#fff",
                          color: u.isActive ? "#b91c1c" : "#15803d",
                          cursor: "pointer", fontFamily: font,
                        }}>
                          {u.isActive ? "Deactivate" : "Activate"}
                        </button>
                        {confirm?.id === u._id ? (
                          <span style={{ fontSize: 12, color: "#374151" }}>
                            Sure?&nbsp;
                            <button onClick={() => deleteUser(u._id)}
                              style={{ color: "#b91c1c", background: "none", border: "none", cursor: "pointer", fontWeight: 600, fontSize: 12 }}>Yes</button>
                            &nbsp;/&nbsp;
                            <button onClick={() => setConfirm(null)}
                              style={{ color: "#64748b", background: "none", border: "none", cursor: "pointer", fontSize: 12 }}>No</button>
                          </span>
                        ) : (
                          <button onClick={() => setConfirm({ id: u._id })} style={{
                            padding: "5px 12px", fontSize: 12, border: "1px solid #fecaca",
                            borderRadius: 6, background: "#fff", color: "#b91c1c",
                            cursor: "pointer", fontFamily: font,
                          }}>Delete</button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {!loading && <Pagination page={page} pageSize={pageSize} totalRecords={pageMeta.totalElements} totalPages={pageMeta.totalPages} first={pageMeta.first} last={pageMeta.last} onPageChange={setPage} onPageSizeChange={size => { setPageSize(size); setPage(0); }} />}

      {modal && (
        <CreateUserModal
          onClose={() => setModal(false)}
          onSave={() => { setModal(false); load(); }}
        />
      )}
    </div>
  );
}
