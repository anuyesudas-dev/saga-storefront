// Talks only to the gateway (docs/edge-design.md) - never to an internal
// service directly. Base URL is build-time configurable so the same bundle
// works against a local gateway, compose, or a port-forwarded k8s cluster.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

const TOKEN_KEY = "saga-storefront.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export class ApiError extends Error {
  constructor(status, title, detail) {
    super(detail || title || `Request failed with status ${status}`);
    this.status = status;
    this.title = title;
  }
}

async function request(path, { method = "GET", body, auth = false } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (auth) {
    const token = getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    // gateway/services return RFC 7807 ProblemDetail: { title, detail, ... }
    throw new ApiError(response.status, data?.title, data?.detail);
  }
  return data;
}

export const api = {
  register: (email, password, displayName) =>
    request("/api/auth/register", { method: "POST", body: { email, password, displayName } }),

  login: (email, password) =>
    request("/api/auth/login", { method: "POST", body: { email, password } }),

  listCatalog: () => request("/api/catalog"),

  getProduct: (productId) => request(`/api/catalog/${encodeURIComponent(productId)}`),

  createOrder: (currency, items) =>
    request("/api/orders", {
      method: "POST",
      auth: true,
      // customerId is set by the gateway from the JWT subject - any value
      // sent here is overridden, so a placeholder is fine.
      body: { customerId: "me", currency, items },
    }),

  listMyOrders: () => request("/api/orders", { auth: true }),

  getOrder: (orderId) => request(`/api/orders/${encodeURIComponent(orderId)}`, { auth: true }),

  cancelOrder: (orderId, reason) =>
    request(`/api/orders/${encodeURIComponent(orderId)}/cancel`, {
      method: "POST",
      auth: true,
      body: reason ? { reason } : undefined,
    }),
};
