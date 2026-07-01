import { useState } from "react";
import Sidebar from "./Sidebar";

export default function Layout({ children }) {
  const [sidebarOpen, setSidebarOpen] = useState(true);

  return (
    <div style={{
      minHeight: "100vh",
      background: "#f8fafc",
      fontFamily: "'Inter', 'Segoe UI', system-ui, sans-serif",
      overflowX: "hidden",
    }}>
      <Sidebar open={sidebarOpen} onToggle={() => setSidebarOpen(o => !o)} />

      {/* Block element (NOT a flex child) → width auto = real visible width minus margin.
          No 100vw, so the scrollbar width can never push content off-screen. */}
      <div style={{
        marginLeft: sidebarOpen ? "240px" : "0px",
        minWidth: 0,
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        transition: "margin-left 0.25s ease",
        overflowX: "hidden",
      }}>
        <header style={{
          height: "56px",
          background: "#ffffff",
          boxShadow: "0 1px 0 #e2e8f0",
          display: "flex",
          alignItems: "center",
          padding: "0 20px",
          gap: "12px",
          position: "sticky",
          top: 0,
          zIndex: 50,
        }}>
          {/* Always rendered, always toggles — never disappears */}
          <button
            onClick={() => setSidebarOpen(o => !o)}
            style={{
              background: "none", border: "1px solid #e2e8f0",
              borderRadius: "8px", padding: "6px 8px",
              cursor: "pointer", color: "#475569",
              display: "flex", alignItems: "center", flexShrink: 0,
            }}
            title={sidebarOpen ? "Hide sidebar" : "Show sidebar"}
          >
            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" viewBox="0 0 24 24">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <div style={{ flex: 1, minWidth: 0 }} />
          <span style={{ fontSize: "13px", color: "#94a3b8", whiteSpace: "nowrap", flexShrink: 0 }}>
            Supply Chain Hub
          </span>
        </header>

        <main style={{
          flex: 1,
          minWidth: 0,
          maxWidth: "100%",
          padding: "28px 32px",
          color: "#0f172a",
          overflowX: "hidden",
          boxSizing: "border-box",
        }}>
          {children}
        </main>
      </div>
    </div>
  );
}
