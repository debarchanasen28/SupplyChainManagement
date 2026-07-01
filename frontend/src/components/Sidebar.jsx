import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const navItems = [
  {
    label: "Dashboard", path: "/dashboard",
    roles: ["ADMIN","MANAGER","ANALYST","VIEWER"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
  },
  {
    label: "Orders", path: "/orders",
    roles: ["ADMIN","MANAGER","ANALYST","VIEWER"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="M9 12h6M9 16h4"/></svg>
  },
  {
    label: "Shipments", path: "/shipments",
    roles: ["ADMIN","MANAGER","ANALYST","VIEWER"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M5 17H3a2 2 0 01-2-2V5a2 2 0 012-2h11a2 2 0 012 2v3"/><rect x="9" y="11" width="14" height="10" rx="1"/><circle cx="12" cy="21" r="1"/><circle cx="20" cy="21" r="1"/></svg>
  },
  {
    label: "Inventory", path: "/inventory",
    roles: ["ADMIN","MANAGER","ANALYST","VIEWER"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
  },
  {
    label: "Alerts", path: "/alerts",
    roles: ["ADMIN","MANAGER","ANALYST"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/></svg>
  },
  {
    label: "Suppliers", path: "/suppliers",
    roles: ["ADMIN","MANAGER"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>
  },
  {
    label: "Users", path: "/users",
    roles: ["ADMIN"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/><line x1="19" y1="8" x2="23" y2="8"/><line x1="21" y1="6" x2="21" y2="10"/></svg>
  },
  {
    label: "Supplier Portal", path: "/portal",
    roles: ["SUPPLIER"],
    icon: <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>
  },
];

export default function Sidebar({ open, onToggle }) {
  const { role, logout } = useAuth();
  const navigate = useNavigate();
  const visible = navItems.filter(i => i.roles.includes(role));

  return (
    <aside style={{
      position: "fixed", top: 0, left: 0,
      width: "240px", height: "100vh",
      background: "#ffffff",
      boxShadow: "1px 0 0 #e2e8f0",
      display: "flex", flexDirection: "column",
      zIndex: 100,
      transform: open ? "translateX(0)" : "translateX(-240px)",
      transition: "transform 0.25s ease",
    }}>

      {/* Brand */}
      <div style={{
        padding: "0 16px 0 20px",
        height: "56px",
        borderBottom: "1px solid #f1f5f9",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <div style={{
            width: "32px", height: "32px", background: "#2563eb",
            borderRadius: "8px", display: "flex", alignItems: "center",
            justifyContent: "center", flexShrink: 0,
          }}>
            <svg width="17" height="17" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
              <path d="M13 10V3L4 14h7v7l9-11h-7z"/>
            </svg>
          </div>
          <div>
            <div style={{ fontSize: "13px", fontWeight: "700", color: "#0f172a", lineHeight: 1.2 }}>
              Supply Chain Hub
            </div>
          </div>
        </div>
        <button
          onClick={onToggle}
          style={{
            background: "none", border: "none", cursor: "pointer",
            color: "#94a3b8", padding: "4px",
            display: "flex", alignItems: "center", borderRadius: "6px", flexShrink: 0,
          }}
          onMouseOver={e => e.currentTarget.style.color = "#475569"}
          onMouseOut={e => e.currentTarget.style.color = "#94a3b8"}
          title="Collapse sidebar"
        >
          <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" viewBox="0 0 24 24">
            <line x1="3" y1="6" x2="21" y2="6"/>
            <line x1="3" y1="12" x2="21" y2="12"/>
            <line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
      </div>

      {/* Role badge */}
      <div style={{ padding: "12px 20px", borderBottom: "1px solid #f1f5f9" }}>
        <span style={{
          display: "inline-block", background: "#eff6ff", color: "#2563eb",
          fontSize: "11px", fontWeight: "700",
          padding: "3px 10px", borderRadius: "20px",
          letterSpacing: "0.5px", textTransform: "uppercase",
        }}>{role}</span>
      </div>

      {/* Nav */}
      <nav style={{ flex: 1, padding: "10px 0", overflowY: "auto" }}>
        <div style={{
          fontSize: "10px", fontWeight: "700", color: "#cbd5e1",
          padding: "8px 20px 4px", letterSpacing: "1px", textTransform: "uppercase",
        }}>
          Menu
        </div>
        {visible.map(item => (
          <NavLink
            key={item.path}
            to={item.path}
            style={({ isActive }) => ({
              display: "flex", alignItems: "center", gap: "10px",
              padding: "9px 20px", textDecoration: "none",
              fontSize: "13.5px", fontWeight: "500",
              color: isActive ? "#2563eb" : "#475569",
              background: isActive ? "#eff6ff" : "transparent",
              borderLeft: isActive ? "3px solid #2563eb" : "3px solid transparent",
              transition: "all 0.15s",
            })}
          >
            {item.icon}
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Sign out */}
      <div style={{ padding: "14px 16px", borderTop: "1px solid #f1f5f9" }}>
        <button
          onClick={() => { logout(); navigate("/login"); }}
          style={{
            width: "100%", display: "flex", alignItems: "center", gap: "10px",
            padding: "9px 12px", background: "transparent",
            border: "1px solid #e2e8f0", borderRadius: "8px",
            color: "#64748b", fontSize: "13px", fontWeight: "500",
            cursor: "pointer", transition: "all 0.15s",
          }}
          onMouseOver={e => { e.currentTarget.style.background = "#fef2f2"; e.currentTarget.style.color = "#dc2626"; e.currentTarget.style.borderColor = "#fecaca"; }}
          onMouseOut={e => { e.currentTarget.style.background = "transparent"; e.currentTarget.style.color = "#64748b"; e.currentTarget.style.borderColor = "#e2e8f0"; }}
        >
          <svg width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
            <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/>
          </svg>
          Sign out
        </button>
      </div>
    </aside>
  );
}