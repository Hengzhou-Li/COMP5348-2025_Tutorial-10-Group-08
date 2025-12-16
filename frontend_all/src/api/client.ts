import type {
  CartSnapshot,
  CreateOrderResponse,
  CustomerOrderSummary,
  CustomerProfile,
  OrderItemInput,
  Product,
  ReserveStockResponse
} from "../types";

export type LoginRequest = {
  username: string;
  password: string;
};

export type SignupRequest = {
  username: string;
  password: string;
};

export type SessionUser = {
  username: string;
};

export type AuthSuccessPayload = {
  message: string;
  username: string;
};

const AUTH_API = import.meta.env.VITE_AUTH_API ?? "http://localhost:8083/api/auth";
const STORE_API = import.meta.env.VITE_STORE_API ?? "http://localhost:8084/api";

async function parseJsonSafe(res: Response): Promise<unknown> {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function extractErrorMessage(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }
  const record = payload as Record<string, unknown>;
  for (const key of ["message", "detail", "error", "title"]) {
    const value = record[key];
    if (typeof value === "string" && value.trim().length > 0) {
      return value;
    }
  }
  return null;
}

async function handleAuthResponse(res: Response, defaultMessage: string) {
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? defaultMessage);
  }
  return payload as AuthSuccessPayload;
}

export async function login(credentials: LoginRequest): Promise<AuthSuccessPayload> {
  const res = await fetch(`${AUTH_API}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(credentials)
  });
  return handleAuthResponse(res, "Login failed");
}

export async function signup(credentials: SignupRequest): Promise<AuthSuccessPayload> {
  const res = await fetch(`${AUTH_API}/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(credentials)
  });
  return handleAuthResponse(res, "Sign up failed");
}

export async function logout() {
  await fetch(`${AUTH_API}/logout`, {
    method: "POST",
    credentials: "include"
  });
}

export async function fetchMe(): Promise<SessionUser | null> {
  const res = await fetch(`${AUTH_API}/me`, { credentials: "include" });
  if (res.status === 401) {
    return null;
  }
  if (!res.ok) {
    throw new Error(`Failed to verify session. Status ${res.status}`);
  }
  return (await res.json()) as SessionUser;
}

export async function fetchProducts(): Promise<Product[]> {
  const res = await fetch(`${STORE_API}/products`, { credentials: "include" });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to load products");
  }
  return (payload ?? []) as Product[];
}

export async function fetchCustomerProfile(username: string): Promise<CustomerProfile> {
  const res = await fetch(
    `${STORE_API}/customers/by-username/${encodeURIComponent(username)}`,
    {
      credentials: "include"
    }
  );
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to resolve customer");
  }
  return payload as CustomerProfile;
}

export async function fetchCustomerOrders(customerId: number): Promise<CustomerOrderSummary[]> {
  const res = await fetch(`${STORE_API}/customers/${customerId}/orders`, {
    credentials: "include"
  });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to load orders");
  }
  return (payload ?? []) as CustomerOrderSummary[];
}

export async function getCart(customerId: number): Promise<CartSnapshot> {
  const res = await fetch(`${STORE_API}/customers/${customerId}/cart`, {
    credentials: "include"
  });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to load cart");
  }
  return payload as CartSnapshot;
}

export async function addCartItem(params: {
  customerId: number;
  productId: number;
  quantity: number;
}): Promise<CartSnapshot> {
  const res = await fetch(`${STORE_API}/customers/${params.customerId}/cart/items`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({
      productId: params.productId,
      quantity: params.quantity
    })
  });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to add item to cart");
  }
  return payload as CartSnapshot;
}

export async function updateCartItem(params: {
  customerId: number;
  productId: number;
  quantity: number;
}): Promise<CartSnapshot> {
  const res = await fetch(
    `${STORE_API}/customers/${params.customerId}/cart/items/${params.productId}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ quantity: params.quantity })
    }
  );
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to update cart item");
  }
  return payload as CartSnapshot;
}

export async function removeCartItem(params: {
  customerId: number;
  productId: number;
}): Promise<CartSnapshot> {
  const res = await fetch(
    `${STORE_API}/customers/${params.customerId}/cart/items/${params.productId}`,
    {
      method: "DELETE",
      credentials: "include"
    }
  );
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to remove cart item");
  }
  return payload as CartSnapshot;
}

export async function clearCart(customerId: number): Promise<CartSnapshot> {
  const res = await fetch(`${STORE_API}/customers/${customerId}/cart`, {
    method: "DELETE",
    credentials: "include"
  });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to clear cart");
  }
  return payload as CartSnapshot;
}

export async function reserveOrder(orderId: number): Promise<ReserveStockResponse> {
  const res = await fetch(`${STORE_API}/orders/${orderId}/reserve`, {
    method: "POST",
    credentials: "include"
  });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to reserve stock");
  }
  return payload as ReserveStockResponse;
}

export async function createOrder(params: {
  customerId: number;
  items: OrderItemInput[];
}): Promise<CreateOrderResponse> {
  const res = await fetch(`${STORE_API}/orders`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(params)
  });
  const payload = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(extractErrorMessage(payload) ?? "Failed to create order");
  }
  return payload as CreateOrderResponse;
}

export async function confirmPayment(orderId: number): Promise<void> {
  const res = await fetch(`${STORE_API}/orders/${orderId}/payment`, {
    method: "POST",
    credentials: "include"
  });
  if (!res.ok) {
    const payload = await parseJsonSafe(res);
    throw new Error(extractErrorMessage(payload) ?? "Failed to confirm payment");
  }
}

export async function cancelOrder(orderId: number): Promise<void> {
  const res = await fetch(`${STORE_API}/orders/${orderId}/cancel`, {
    method: "POST",
    credentials: "include"
  });
  if (!res.ok) {
    const payload = await parseJsonSafe(res);
    throw new Error(extractErrorMessage(payload) ?? "Failed to cancel order");
  }
}
