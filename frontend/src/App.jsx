import { useState, Component } from "react";
import { BrowserRouter, Routes, Route, Navigate, NavLink, useNavigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";

import Login                from "./pages/Login";
import VendorDashboard      from "./pages/VendorDashboard";
import ProcurementDashboard from "./pages/ProcurementDashboard";
import Dashboard            from "./pages/Dashboard";
import Orders               from "./pages/Orders";
import Shipments            from "./pages/Shipments";
import Inventory            from "./pages/Inventory";
import Alerts               from "./pages/Alerts";
import Suppliers            from "./pages/Suppliers";
import UserManagement       from "./pages/UserManagement";
import AdminVendorView      from "./pages/AdminVendorView";
import AdminProcurementView from "./pages/AdminProcurementView";
import IntegrationLogs      from "./pages/IntegrationLogs";

const font = "'Inter', 'Segoe UI', system-ui, sans-serif";

const icons = {
  dashboard: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
      <rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
    </svg>
  ),
  orders: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2"/>
      <rect x="9" y="3" width="6" height="4" rx="1"/>
      <line x1="9" y1="12" x2="15" y2="12"/><line x1="9" y1="16" x2="13" y2="16"/>
    </svg>
  ),
  shipments: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <rect x="1" y="3" width="15" height="13" rx="1"/>
      <path d="M16 8h4l3 3v5h-7V8z"/>
      <circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>
    </svg>
  ),
  inventory: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/>
      <polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>
    </svg>
  ),
  suppliers: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/>
      <polyline points="9 22 9 12 15 12 15 22"/>
    </svg>
  ),
  alerts: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/>
      <path d="M13.73 21a2 2 0 01-3.46 0"/>
    </svg>
  ),
  users: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
      <circle cx="9" cy="7" r="4"/>
      <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
    </svg>
  ),
  logs: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/>
      <line x1="8" y1="18" x2="21" y2="18"/>
      <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/>
      <line x1="3" y1="18" x2="3.01" y2="18"/>
    </svg>
  ),
  vendor: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <rect x="2" y="7" width="20" height="14" rx="2"/>
      <path d="M16 7V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v2"/>
      <line x1="12" y1="12" x2="12" y2="16"/><line x1="10" y1="14" x2="14" y2="14"/>
    </svg>
  ),
  procurement: (
    <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
      <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
      <path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/>
    </svg>
  ),
};

const navConfig = {
  VENDOR: [
    { to: "/vendor",    label: "Dashboard",    icon: "dashboard" },
    { to: "/orders",    label: "Sales Orders", icon: "orders" },
    { to: "/shipments", label: "Shipments",    icon: "shipments" },
    { to: "/inventory", label: "Inventory",    icon: "inventory" },
    { to: "/alerts",    label: "Alerts",       icon: "alerts" },
  ],
  PROCUREMENT: [
    { to: "/procurement",      label: "Dashboard",       icon: "dashboard" },
    { to: "/orders",           label: "Purchase Orders", icon: "orders" },
    { to: "/shipments",        label: "Shipments",       icon: "shipments" },
    { to: "/inventory",        label: "Inventory",       icon: "inventory" },
    { to: "/suppliers",        label: "Suppliers",       icon: "suppliers" },
    { to: "/alerts",           label: "Alerts",          icon: "alerts" },
  ],
  ADMIN: [
    { to: "/admin",            label: "Dashboard",        icon: "dashboard" },
    { to: "/vendor-view",      label: "Vendor",           icon: "vendor" },
    { to: "/procurement-view", label: "Procurement",      icon: "procurement" },
    { to: "/alerts",           label: "Alerts",           icon: "alerts" },
    { to: "/users",            label: "User Management",  icon: "users" },
    { to: "/logs",             label: "Integration Logs", icon: "logs" },
  ],
  MANAGER: [
    { to: "/admin",     label: "Dashboard",        icon: "dashboard" },
    { to: "/orders",    label: "All Orders",       icon: "orders" },
    { to: "/shipments", label: "All Shipments",    icon: "shipments" },
    { to: "/suppliers", label: "Suppliers",        icon: "suppliers" },
    { to: "/alerts",    label: "Alerts",           icon: "alerts" },
    { to: "/logs",      label: "Integration Logs", icon: "logs" },
  ],
};

const roleColors = {
  VENDOR:      "#1d4ed8",
  PROCUREMENT: "#6d28d9",
  ADMIN:       "#0f766e",
  MANAGER:     "#0f766e",
};

function Layout({ children }) {
  const { role, name, systemId, logout } = useAuth();
  const navigate = useNavigate();
  const items = (navConfig[role] || []).filter(item =>
    item.to !== "/inventory" || systemId === "SYSTEM1"
  );
  const color = roleColors[role] || "#334155";
  const [open, setOpen] = useState(true);

  const roleLabel = { VENDOR: "Vendor", PROCUREMENT: "Procurement",
                      ADMIN: "Admin", MANAGER: "Manager" }[role] || role;

  return (
    <div style={{ fontFamily: font }}>

      <button
        onClick={() => setOpen(o => !o)}
        style={{
          position: "fixed", top: 14, left: open ? 185 : 12, zIndex: 200,
          background: "#1e293b", border: "none", borderRadius: 6,
          width: 32, height: 32, display: "flex", alignItems: "center",
          justifyContent: "center", cursor: "pointer", color: "#94a3b8",
          transition: "left 0.25s ease",
          boxShadow: open ? "none" : "0 2px 8px rgba(0,0,0,0.25)",
        }}
      >
        <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
          <line x1="3" y1="6" x2="21" y2="6"/>
          <line x1="3" y1="12" x2="21" y2="12"/>
          <line x1="3" y1="18" x2="21" y2="18"/>
        </svg>
      </button>

      <div style={{
        position: "fixed", top: 0, left: 0, bottom: 0, width: 220,
        background: "#0f172a", display: "flex", flexDirection: "column",
        zIndex: 100,
        transform: open ? "translateX(0)" : "translateX(-220px)",
        transition: "transform 0.25s ease",
      }}>
        <div style={{ padding: "20px 52px 16px 20px", borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: "#fff", letterSpacing: "0.06em", textTransform: "uppercase" }}>
            Supply Chain Hub
          </div>
          <div style={{ marginTop: 8, fontSize: 11, fontWeight: 600, padding: "3px 10px", borderRadius: 20, display: "inline-block", background: color, color: "#fff" }}>
            {roleLabel}
          </div>
        </div>

        <nav style={{ flex: 1, padding: "10px 0", overflowY: "auto" }}>
          {items.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              style={({ isActive }) => ({
                display: "flex", alignItems: "center", gap: 10,
                padding: "9px 20px", fontSize: 13, fontWeight: 500,
                color: isActive ? "#fff" : "#94a3b8",
                background: isActive ? "rgba(255,255,255,0.07)" : "transparent",
                textDecoration: "none",
                borderLeft: isActive ? `3px solid ${color}` : "3px solid transparent",
              })}
            >
              <span style={{ flexShrink: 0, opacity: 0.9 }}>{icons[item.icon]}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div style={{ padding: "14px 20px", borderTop: "1px solid rgba(255,255,255,0.08)" }}>
          <div style={{ fontSize: 12, color: "#64748b", marginBottom: 2, fontWeight: 500 }}>{name || "User"}</div>
          <div style={{ fontSize: 11, color: "#475569", marginBottom: 10 }}>{roleLabel}</div>
          <button
            onClick={() => { logout(); navigate("/login"); }}
            style={{ width: "100%", padding: "7px 0", background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)", borderRadius: 6, color: "#94a3b8", fontSize: 12, cursor: "pointer", fontFamily: font }}
          >
            Sign out
          </button>
        </div>
      </div>

      <div style={{
        position: "fixed", top: 0, left: open ? 220 : 0, right: 0, bottom: 0,
        overflowY: "auto", overflowX: "hidden",
        background: "#f8fafc", transition: "left 0.25s ease",
      }}>
        {children}
      </div>
    </div>
  );
}

function PrivateRoute({ children, allowedRoles, allowedSystems }) {
  const { token, role, systemId } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  if (allowedRoles && !allowedRoles.includes(role)) return <Navigate to="/" replace />;
  if (allowedSystems && !allowedSystems.includes(systemId)) return <Navigate to="/" replace />;
  return <Layout>{children}</Layout>;
}

function RootRedirect() {
  const { token, role } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  if (role === "VENDOR")      return <Navigate to="/vendor" replace />;
  if (role === "PROCUREMENT") return <Navigate to="/procurement" replace />;
  return <Navigate to="/admin" replace />;
}

// Catches render-time errors so a crash shows a usable message on a light background
// instead of the bare (dark) page background.
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }
  static getDerivedStateFromError(error) {
    return { error };
  }
  componentDidCatch(error, info) {
    console.error("Render error:", error, info);
  }
  render() {
    if (this.state.error) {
      return (
        <div style={{ minHeight: "100vh", background: "#f8fafc", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: font, padding: 24 }}>
          <div style={{ maxWidth: 480, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 28, textAlign: "center" }}>
            <div style={{ fontSize: 18, fontWeight: 700, color: "#0f172a", marginBottom: 8 }}>Something went wrong</div>
            <div style={{ fontSize: 13, color: "#64748b", marginBottom: 20 }}>
              This page hit an unexpected error and could not render. Try reloading.
            </div>
            <button
              onClick={() => { this.setState({ error: null }); window.location.assign("/"); }}
              style={{ background: "#0f766e", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: font }}
            >
              Reload
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <ErrorBoundary>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/"      element={<RootRedirect />} />

          <Route path="/vendor" element={
            <PrivateRoute allowedRoles={["VENDOR","ADMIN","MANAGER"]}>
              <VendorDashboard />
            </PrivateRoute>
          }/>
          <Route path="/procurement" element={
            <PrivateRoute allowedRoles={["PROCUREMENT","ADMIN","MANAGER"]}>
              <ProcurementDashboard />
            </PrivateRoute>
          }/>
          <Route path="/admin" element={
            <PrivateRoute allowedRoles={["ADMIN","MANAGER"]}>
              <Dashboard />
            </PrivateRoute>
          }/>
          <Route path="/vendor-view" element={
            <PrivateRoute allowedRoles={["ADMIN"]}>
              <AdminVendorView />
            </PrivateRoute>
          }/>
          <Route path="/procurement-view" element={
            <PrivateRoute allowedRoles={["ADMIN"]}>
              <AdminProcurementView />
            </PrivateRoute>
          }/>
          <Route path="/orders" element={
            <PrivateRoute allowedRoles={["ADMIN","MANAGER","VENDOR","PROCUREMENT"]}>
              <Orders />
            </PrivateRoute>
          }/>
          <Route path="/shipments" element={
            <PrivateRoute allowedRoles={["ADMIN","MANAGER","VENDOR","PROCUREMENT"]}>
              <Shipments />
            </PrivateRoute>
          }/>
          <Route path="/inventory" element={
            <PrivateRoute allowedRoles={["VENDOR","PROCUREMENT"]} allowedSystems={["SYSTEM1"]}>
              <Inventory />
            </PrivateRoute>
          }/>
          <Route path="/alerts" element={
            <PrivateRoute allowedRoles={["ADMIN","MANAGER","VENDOR","PROCUREMENT"]}>
              <Alerts />
            </PrivateRoute>
          }/>
          <Route path="/suppliers" element={
            <PrivateRoute allowedRoles={["ADMIN","MANAGER","PROCUREMENT"]}>
              <Suppliers />
            </PrivateRoute>
          }/>
          <Route path="/users" element={
            <PrivateRoute allowedRoles={["ADMIN"]}>
              <UserManagement />
            </PrivateRoute>
          }/>
          <Route path="/logs" element={
            <PrivateRoute allowedRoles={["ADMIN","MANAGER"]}>
              <IntegrationLogs />
            </PrivateRoute>
          }/>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </ErrorBoundary>
      </BrowserRouter>
    </AuthProvider>
  );
}
