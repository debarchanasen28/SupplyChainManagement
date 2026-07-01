import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App";

// App provides its own <AuthProvider>; do not wrap it again here (avoids a duplicate context).
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);
