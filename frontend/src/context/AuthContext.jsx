import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

export const useAuth = () => useContext(AuthContext);

const getInitialSystemId = () => {
  const stored = localStorage.getItem("systemId");
  if (stored) return stored;

  try {
    const token = localStorage.getItem("token");
    const encoded = token?.split(".")[1];
    if (!encoded) return "";
    const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const systemId = JSON.parse(atob(padded)).systemId || "";
    if (systemId) localStorage.setItem("systemId", systemId);
    return systemId;
  } catch {
    return "";
  }
};

export function AuthProvider({ children }) {
  const [token, setToken]       = useState(localStorage.getItem("token"));
  const [role, setRole]         = useState(localStorage.getItem("role"));
  const [name, setName]         = useState(localStorage.getItem("name") || "");
  const [entityId, setEntityId] = useState(localStorage.getItem("entityId") || "");
  const [systemId, setSystemId] = useState(getInitialSystemId);

  const login = (data) => {
    localStorage.setItem("token",    data.token);
    localStorage.setItem("role",     data.role);
    localStorage.setItem("name",     data.name || "");
    localStorage.setItem("entityId", data.entityId || "");
    localStorage.setItem("systemId", data.systemId || "");
    setToken(data.token);
    setRole(data.role);
    setName(data.name || "");
    setEntityId(data.entityId || "");
    setSystemId(data.systemId || "");
  };

  const logout = () => {
    localStorage.clear();
    setToken(null);
    setRole(null);
    setName("");
    setEntityId("");
    setSystemId("");
  };

  return (
    <AuthContext.Provider value={{ token, role, name, entityId, systemId, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
