import api from "./axios";

// Auth
export const loginUser = (data) => api.post("/auth/login", data);
export const registerUser = (data) => api.post("/auth/register", data);
export const loginSupplier = (data) => api.post("/auth/supplier/login", data);
export const registerSupplier = (data) => api.post("/auth/supplier/register", data);

// Orders
export const getOrders = () => api.get("/orders");
export const getOrder = (orderId) => api.get(`/orders/${orderId}`);
export const createOrder = (data) => api.post("/orders", data);
export const sendOrderToCpi = (orderId) => api.post(`/orders/${orderId}/send`);
export const sendStockOffer = (orderId) => api.put(`/orders/${orderId}/send-offer`);
export const rejectOrder = (orderId) => api.patch(`/vendor/orders/${encodeURIComponent(orderId)}/reject`, {
  rejectionReason: "Rejected by vendor"
});
export const cancelVendorOrder = (orderId) =>
  api.patch(`/vendor/orders/${encodeURIComponent(orderId)}/cancel`);
export const confirmSupply = (orderId) => api.put(`/orders/${orderId}/confirm-supply`);
export const updateOrderStatus = (orderId, status) => api.patch(`/orders/${orderId}/status?status=${status}`);
export const getOrdersBySupplier = (supplierId) => api.get(`/orders/supplier/${supplierId}`);
export const getOrdersByRisk = (riskLevel) => api.get(`/orders/risk/${riskLevel}`);

// Shipments
export const getShipments = () => api.get("/shipments");
export const getShipment = (shipmentId) => api.get(`/shipments/${shipmentId}`);
export const createShipment = (data) => api.post("/shipments", data);
export const getShipmentsByOrder = (orderId) => api.get(`/shipments/order/${orderId}`);
export const getDelayedShipments = () => api.get("/shipments/delayed");
export const updateShipmentStatus = (shipmentId, status, currentLocation) =>
  api.patch(`/shipments/${shipmentId}/status?status=${status}${currentLocation ? `&currentLocation=${currentLocation}` : ""}`);

// Inventory
export const getInventory = () => api.get("/inventory");
export const receiveInventoryStock = (data) => api.post("/inventory/receive", data);
export const sellInventoryStock = (data) => api.post("/inventory/sell", data);
export const getInventoryAlerts = () => api.get("/inventory/alerts");
export const resolveInventoryAlert = (alertId) => api.patch(`/inventory/alerts/${alertId}/resolve`);
export const getInventoryItem = (inventoryId) => api.get(`/inventory/${inventoryId}`);
export const createInventory = (data) => api.post("/inventory", data);
export const getLowStock = (threshold) => api.get(`/inventory/low-stock?threshold=${threshold}`);
export const restockInventory = (inventoryId, quantity) =>
  api.patch(`/inventory/${inventoryId}/restock?quantity=${quantity}`);

// Procurement — read-only System2 Vendor inventory (for PO creation dropdown)
export const getSystem2VendorInventory = () =>
  api.get("/procurement/system2-vendor-inventory");

// Alerts
export const getAlerts = () => api.get("/alerts");
export const getUnresolvedAlerts = () => api.get("/alerts/unresolved");
export const getCriticalAlerts = () => api.get("/alerts/critical/unresolved");
export const createAlert = (data) => api.post("/alerts", data);
export const acknowledgeAlert = (alertId, acknowledgedBy) =>
  api.patch(`/alerts/${alertId}/acknowledge?acknowledgedBy=${acknowledgedBy}`);
export const resolveAlert = (alertId, resolvedBy, resolutionNote) =>
  api.patch(`/alerts/${alertId}/resolve?resolvedBy=${resolvedBy}&resolutionNote=${resolutionNote}`);
export const escalateAlert = (alertId, escalatedTo) =>
  api.patch(`/alerts/${alertId}/escalate?escalatedTo=${escalatedTo}`);

// Suppliers
export const getSuppliers = () => api.get("/suppliers");
export const getSupplier = (supplierId) => api.get(`/suppliers/${supplierId}`);
export const updateSupplierStatus = (supplierId, integrationStatus) =>
  api.patch(`/suppliers/${supplierId}/status?integrationStatus=${integrationStatus}`);
export const updateSupplierRating = (supplierId, rating) =>
  api.patch(`/suppliers/${supplierId}/rating?rating=${rating}`);

// Users
export const getUsers = () => api.get("/users");
export const getUser = (userId) => api.get(`/users/${userId}`);
export const lockUser = (userId) => api.patch(`/users/${userId}/lock`);
export const unlockUser = (userId) => api.patch(`/users/${userId}/unlock`);

// Integration Logs
export const getLogs = () => api.get("/integration-logs");
export const getFailedLogs = () => api.get("/integration-logs/failed");
