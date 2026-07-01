import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import api from "../api/axios";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";

const inputStyle = {
  width: "100%", padding: "10px 12px",
  border: "1px solid #d1d5db", borderRadius: 8,
  fontSize: 14, outline: "none",
  background: "#fff", color: "#0f172a",
  boxSizing: "border-box", fontFamily: font,
};

const TABS = [
  { role: "VENDOR",      label: "Vendor",      desc: "Manage outbound sales orders and shipments", color: "#1d4ed8", bg: "#eff6ff", border: "#bfdbfe" },
  { role: "PROCUREMENT", label: "Procurement", desc: "Manage inbound purchase orders and suppliers", color: "#6d28d9", bg: "#f5f3ff", border: "#e9d5ff" },
  { role: "ADMIN",       label: "Admin",       desc: "Full system overview and user management",    color: "#0f766e", bg: "#f0fdfa", border: "#99f6e4" },
];

export default function Login() {
  const [activeTab, setActiveTab] = useState("VENDOR");
  const [mode,      setMode]      = useState("login");  // "login" | "register"
  const [name,      setName]      = useState("");
  const [email,     setEmail]     = useState("");
  const [password,  setPassword]  = useState("");
  const [confirm,   setConfirm]   = useState("");
  const [error,     setError]     = useState("");
  const [loading,   setLoading]   = useState(false);

  const { login } = useAuth();
  const navigate  = useNavigate();

  const tab = TABS.find(t => t.role === activeTab);

  const redirect = role => {
    if (role === "VENDOR")           navigate("/vendor");
    else if (role === "PROCUREMENT") navigate("/procurement");
    else                             navigate("/admin");
  };

  const reset = () => {
    setName(""); setEmail(""); setPassword(""); setConfirm(""); setError("");
  };

  const handleLogin = async e => {
    e.preventDefault();
    setError(""); setLoading(true);
    try {
      const res = await api.post("/auth/login", { email, password });
      // The Admin tab serves both ADMIN and MANAGER; other roles must match their tab.
      const expectedTab = res.data.role === "MANAGER" ? "ADMIN" : res.data.role;
      if (expectedTab !== activeTab) {
        setError(`Those credentials belong to the ${expectedTab} portal. Switch to that tab to sign in.`);
        return;
      }
      login(res.data);
      redirect(res.data.role);
    } catch (err) {
      setError(err.response?.data?.message || "Invalid email or password.");
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async e => {
    e.preventDefault();
    if (password !== confirm) { setError("Passwords do not match."); return; }
    setError(""); setLoading(true);
    try {
      const res = await api.post("/auth/register", { name, email, password, role: activeTab });
      login(res.data);
      redirect(res.data.role);
    } catch (err) {
      setError(err.response?.data?.message || "Registration failed.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: "100vh",
      background: "linear-gradient(135deg, #0f172a 0%, #1e293b 100%)",
      display: "flex", alignItems: "center", justifyContent: "center",
      fontFamily: font, padding: "24px 16px",
    }}>
      <div style={{ width: "100%", maxWidth: 420 }}>

        <div style={{ textAlign: "center", marginBottom: 28 }}>
          <div style={{ fontSize: 21, fontWeight: 700, color: "#fff", letterSpacing: "0.01em" }}>
            Supply Chain Integration Hub
          </div>
          <div style={{ fontSize: 13, color: "#475569", marginTop: 6 }}>
            {mode === "login" ? "Sign in to your workspace" : "Create a new account"}
          </div>
        </div>

        <div style={{
          background: "#fff", borderRadius: 16,
          boxShadow: "0 24px 64px rgba(0,0,0,0.35)",
          overflow: "hidden",
        }}>
          {/* Tab bar */}
          <div style={{ display: "flex", borderBottom: "1px solid #f1f5f9" }}>
            {TABS.map(t => (
              <button key={t.role}
                onClick={() => { setActiveTab(t.role); setError(""); }}
                style={{
                  flex: 1, padding: "14px 6px", border: "none", cursor: "pointer",
                  background: activeTab === t.role ? "#fff" : "#f8fafc",
                  borderBottom: activeTab === t.role ? `2px solid ${t.color}` : "2px solid transparent",
                  fontFamily: font,
                }}
              >
                <div style={{ fontSize: 13, fontWeight: 700, color: activeTab === t.role ? t.color : "#94a3b8" }}>
                  {t.label}
                </div>
              </button>
            ))}
          </div>

          {/* Role description */}
          <div style={{
            margin: "20px 24px 0",
            background: tab.bg, border: `1px solid ${tab.border}`,
            borderRadius: 8, padding: "8px 14px",
          }}>
            <div style={{ fontSize: 12, color: tab.color, fontWeight: 500 }}>{tab.desc}</div>
          </div>

          {/* Form */}
          <form onSubmit={mode === "login" ? handleLogin : handleRegister}
            style={{ padding: "20px 24px 28px" }}>

            {error && (
              <div style={{
                background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca",
                borderRadius: 8, padding: "8px 14px", marginBottom: 16, fontSize: 13,
              }}>
                {error}
              </div>
            )}

            {mode === "register" && (
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>
                  Full name
                </label>
                <input type="text" required value={name} onChange={e => setName(e.target.value)}
                  placeholder="Your name" style={inputStyle} />
              </div>
            )}

            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>
                Email address
              </label>
              <input type="email" required value={email} onChange={e => setEmail(e.target.value)}
                placeholder="you@company.com" style={inputStyle} autoComplete="email" />
            </div>

            <div style={{ marginBottom: mode === "register" ? 14 : 22 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>
                Password
              </label>
              <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
                placeholder="••••••••" style={inputStyle}
                autoComplete={mode === "login" ? "current-password" : "new-password"} />
            </div>

            {mode === "register" && (
              <div style={{ marginBottom: 22 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>
                  Confirm password
                </label>
                <input type="password" required value={confirm} onChange={e => setConfirm(e.target.value)}
                  placeholder="••••••••" style={inputStyle} autoComplete="new-password" />
              </div>
            )}

            <button type="submit" disabled={loading} style={{
              width: "100%", padding: "11px",
              background: loading ? "#93c5fd" : tab.color,
              color: "#fff", border: "none", borderRadius: 8,
              fontSize: 14, fontWeight: 600,
              cursor: loading ? "default" : "pointer",
              fontFamily: font,
            }}>
              {loading
                ? (mode === "login" ? "Signing in..." : "Creating account...")
                : (mode === "login" ? `Sign in as ${tab.label}` : `Create ${tab.label} account`)}
            </button>

            <div style={{ textAlign: "center", marginTop: 16 }}>
              <button type="button"
                onClick={() => { setMode(mode === "login" ? "register" : "login"); reset(); }}
                style={{
                  background: "none", border: "none", cursor: "pointer",
                  fontSize: 13, color: tab.color, fontFamily: font, fontWeight: 500,
                }}>
                {mode === "login"
                  ? "Don't have an account? Sign up"
                  : "Already have an account? Sign in"}
              </button>
            </div>
          </form>
        </div>

        <div style={{ textAlign: "center", marginTop: 20, fontSize: 12, color: "#334155" }}>
          Contact your administrator for access credentials.
        </div>
      </div>
    </div>
  );
}