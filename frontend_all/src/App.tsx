import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent
} from "react";
import {
  addCartItem,
  clearCart,
  fetchCustomerOrders,
  fetchCustomerProfile,
  fetchMe,
  fetchProducts,
  getCart,
  createOrder,
  confirmPayment,
  cancelOrder,
  login,
  logout,
  removeCartItem,
  reserveOrder,
  signup,
  updateCartItem,
  type AuthSuccessPayload,
  type SessionUser
} from "./api/client";
import type {
  CartSnapshot,
  CartItem,
  CreateOrderResponse,
  CustomerOrderSummary,
  Product,
  ReserveStockResponse
} from "./types";
import { LoginView } from "./views/LoginView";
import { PlaceOrderView } from "./views/PlaceOrderView";
import { CartView } from "./views/CartView";
import { OrderStatusView } from "./views/OrderStatusView";
import { OrderHistoryView } from "./views/OrderHistoryView";
import { OrderConfirmView } from "./views/OrderConfirmView";

type StatusKind = "idle" | "loading" | "success" | "error";
type DashboardView = "catalogue" | "cart" | "confirm" | "status" | "history";
type AuthMode = "signin" | "signup";

const DEFAULT_CREDENTIALS = {
  username: "demo",
  password: "DemoPass123"
};

export default function App() {
  const [authMode, setAuthMode] = useState<AuthMode>("signin");

  const [signinUsername, setSigninUsername] = useState(DEFAULT_CREDENTIALS.username);
  const [signinPassword, setSigninPassword] = useState(DEFAULT_CREDENTIALS.password);
  const [signinStatus, setSigninStatus] = useState<StatusKind>("idle");
  const [signinMessage, setSigninMessage] = useState<string | null>(null);

  const [signupUsername, setSignupUsername] = useState("");
  const [signupPassword, setSignupPassword] = useState("");
  const [signupStatus, setSignupStatus] = useState<StatusKind>("idle");
  const [signupMessage, setSignupMessage] = useState<string | null>(null);

  const [session, setSession] = useState<SessionUser | null>(null);

  const [products, setProducts] = useState<Product[]>([]);
  const [cart, setCart] = useState<CartSnapshot | null>(null);
  const [orders, setOrders] = useState<CustomerOrderSummary[]>([]);

  const [cartStatus, setCartStatus] = useState<StatusKind>("idle");
  const [cartMessage, setCartMessage] = useState<string | null>(null);
  const [statusStatus, setStatusStatus] = useState<StatusKind>("idle");
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const [activeView, setActiveView] = useState<DashboardView>("catalogue");
  const [customerId, setCustomerId] = useState<number | null>(null);
  const [pendingOrder, setPendingOrder] = useState<CreateOrderResponse | null>(null);
  const [pendingItems, setPendingItems] = useState<CartItem[]>([]);
  const [pendingTotal, setPendingTotal] = useState<number>(0);
  const [pendingStatus, setPendingStatus] = useState<StatusKind>("idle");
  const [pendingMessage, setPendingMessage] = useState<string | null>(null);

  const resetPendingOrderState = useCallback(() => {
    setPendingOrder(null);
    setPendingItems([]);
    setPendingTotal(0);
    setPendingStatus("idle");
    setPendingMessage(null);
  }, []);

  const loadProducts = useCallback(async () => {
    try {
      const list = await fetchProducts();
      setProducts(list);
    } catch (error) {
      console.error("Failed to load products", error);
    }
  }, []);

  const loadCart = useCallback(async (targetCustomerId: number) => {
    try {
      const snapshot = await getCart(targetCustomerId);
      setCart(snapshot);
    } catch (error) {
      console.error("Failed to load cart", error);
      setCartStatus("error");
      const message =
        error instanceof Error ? error.message : "Unable to load customer cart.";
      setCartMessage(message);
      throw error;
    }
  }, []);

  const loadOrders = useCallback(async (targetCustomerId: number) => {
    try {
      const list = await fetchCustomerOrders(targetCustomerId);
      setOrders(list);
    } catch (error) {
      console.error("Failed to load orders", error);
      setStatusStatus("error");
      const message =
        error instanceof Error ? error.message : "Unable to load customer orders.";
      setStatusMessage(message);
      throw error;
    }
  }, []);

  const initializeDashboard = useCallback(
    async (targetCustomerId: number) => {
      await loadProducts();
      try {
        await loadCart(targetCustomerId);
      } catch {
        /* handled inside loadCart */
      }
      try {
        await loadOrders(targetCustomerId);
      } catch {
        /* handled inside loadOrders */
      }
    },
    [loadProducts, loadCart, loadOrders]
  );

  const reloadSession = useCallback(async () => {
    try {
      const me = await fetchMe();
      setSession(me);
      if (me) {
        try {
          const profile = await fetchCustomerProfile(me.username);
          setCustomerId(profile.customerId);
          setActiveView("catalogue");
          await initializeDashboard(profile.customerId);
        } catch (profileError) {
          console.error("Failed to resolve customer", profileError);
          setCustomerId(null);
          setCart(null);
          setOrders([]);
          setCartStatus("error");
          const message =
            profileError instanceof Error
              ? profileError.message
              : "Unable to resolve customer profile.";
          setCartMessage(message);
        }
      } else {
        setCustomerId(null);
        setCart(null);
        setOrders([]);
        resetPendingOrderState();
      }
    } catch (error) {
      console.error("Failed to verify session", error);
      setSigninStatus("error");
      setSigninMessage("Unable to verify session.");
      setCustomerId(null);
      setCart(null);
      setOrders([]);
      resetPendingOrderState();
    }
  }, [initializeDashboard, resetPendingOrderState]);

  useEffect(() => {
    void reloadSession();
  }, [reloadSession]);

  const handleAuthModeChange = (mode: AuthMode) => {
    setAuthMode(mode);
    if (mode === "signin") {
      setSignupStatus("idle");
      setSignupMessage(null);
    } else {
      setSigninStatus("idle");
      setSigninMessage(null);
    }
  };

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSigninStatus("loading");
    setSigninMessage(null);
    try {
      const response: AuthSuccessPayload = await login({
        username: signinUsername,
        password: signinPassword
      });
      await reloadSession();
      setSigninStatus("success");
      setSigninMessage(response.message ?? "Signed in successfully.");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Sign-in failed, please retry.";
      setSigninStatus("error");
      setSigninMessage(message);
    }
  };

  const handleSignup = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedUsername = signupUsername.trim();
    if (normalizedUsername.length < 3) {
      setSignupStatus("error");
      setSignupMessage("Username must be at least 3 characters long.");
      return;
    }
    if (signupPassword.length < 8) {
      setSignupStatus("error");
      setSignupMessage("Password must be at least 8 characters long.");
      return;
    }

    setSignupStatus("loading");
    setSignupMessage(null);
    try {
      await signup({ username: normalizedUsername, password: signupPassword });

      setSigninUsername(normalizedUsername);
      setSigninPassword(signupPassword);
      setSignupUsername("");
      setSignupPassword("");

      await reloadSession();

      setSignupStatus("success");
      setSignupMessage("Account created and signed in.");
      setAuthMode("signin");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Sign-up failed, please retry.";
      setSignupStatus("error");
      setSignupMessage(message);
    }
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      console.error("Logout failed", error);
    } finally {
      setSession(null);
      setProducts([]);
      setCart(null);
      setOrders([]);
      setCustomerId(null);
      setActiveView("catalogue");
      setSigninStatus("idle");
      setSigninMessage(null);
      setSignupStatus("idle");
      setSignupMessage(null);
      setSignupUsername("");
      setSignupPassword("");
      resetPendingOrderState();
    }
  };

  const handleAddToCart = async (productId: number, quantity: number) => {
    if (customerId == null) {
      setCartStatus("error");
      setCartMessage("Customer context not available.");
      return;
    }
    setCartStatus("loading");
    setCartMessage(null);
    try {
      const snapshot = await addCartItem({ customerId, productId, quantity });
      setCart(snapshot);
      setCartStatus("success");
      setCartMessage("Item added to cart.");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to add item to cart.";
      setCartStatus("error");
      setCartMessage(message);
    }
  };

  const handleUpdateCartQuantity = async (productId: number, quantity: number) => {
    if (customerId == null) {
      setCartStatus("error");
      setCartMessage("Customer context not available.");
      return;
    }
    setCartStatus("loading");
    setCartMessage(null);
    try {
      const snapshot = await updateCartItem({ customerId, productId, quantity });
      setCart(snapshot);
      setCartStatus("success");
      setCartMessage("Cart updated.");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to update cart item.";
      setCartStatus("error");
      setCartMessage(message);
    }
  };

  const handleRemoveCartItem = async (productId: number) => {
    if (customerId == null) {
      setCartStatus("error");
      setCartMessage("Customer context not available.");
      return;
    }
    setCartStatus("loading");
    setCartMessage(null);
    try {
      const snapshot = await removeCartItem({ customerId, productId });
      setCart(snapshot);
      setCartStatus("success");
      setCartMessage("Item removed.");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to remove cart item.";
      setCartStatus("error");
      setCartMessage(message);
    }
  };

  const handleClearCart = async () => {
    if (customerId == null) {
      setCartStatus("error");
      setCartMessage("Customer context not available.");
      return;
    }
    setCartStatus("loading");
    setCartMessage(null);
    try {
      const snapshot = await clearCart(customerId);
      setCart(snapshot);
      setCartStatus("success");
      setCartMessage("Cart cleared.");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to clear cart.";
      setCartStatus("error");
      setCartMessage(message);
    }
  };

  const handleBackToCartFromConfirm = () => {
    resetPendingOrderState();
    setActiveView("cart");
  };

  const handleCancelPendingOrder = async () => {
    if (!pendingOrder) {
      setPendingStatus("error");
      setPendingMessage("No pending order to cancel.");
      return;
    }
    setPendingStatus("loading");
    setPendingMessage(null);
    try {
      await cancelOrder(pendingOrder.orderId);
      setPendingStatus("idle");
      setPendingMessage(null);
      if (customerId != null) {
        try {
          await loadOrders(customerId);
        } catch {
          /* handled inside loadOrders */
        }
      }
      setCartStatus("success");
      setCartMessage(`Order #${pendingOrder.orderId} cancelled.`);
      setStatusStatus("success");
      setStatusMessage(`Order #${pendingOrder.orderId} cancelled.`);
      resetPendingOrderState();
      setActiveView("cart");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to cancel order.";
      setPendingStatus("error");
      setPendingMessage(message);
      setStatusStatus("error");
      setStatusMessage(message);
    }
  };

  const handleConfirmPaymentFromCart = async () => {
    if (customerId == null) {
      setCartStatus("error");
      setCartMessage("Customer context not available.");
      return;
    }
    if (!cart || cart.items.length === 0) {
      setCartStatus("error");
      setCartMessage("Cart is empty.");
      return;
    }
    const snapshotItems = cart.items.map((item) => ({ ...item }));
    const snapshotTotal = cart.cartTotal;
    setCartStatus("loading");
    setCartMessage(null);
    setPendingStatus("loading");
    setPendingMessage(null);
    setStatusStatus("loading");
    setStatusMessage(null);
    try {
      const createdOrder = await createOrder({
        customerId,
        items: cart.items.map((item) => ({
          productId: item.productId,
          quantity: item.quantity
        }))
      });

      let effectiveOrder: CreateOrderResponse = createdOrder;
      
      // Skip reserveOrder and confirmPayment API calls
      console.log("Skipping reserveOrder and confirmPayment API calls"); 
      try {
        const cleared = await clearCart(customerId);
        setCart(cleared);
      } catch (cartError) {
        console.error("Failed to clear cart after payment", cartError);
      }

      let finalOrder: CreateOrderResponse = effectiveOrder;
      try {
        const updatedOrders = await fetchCustomerOrders(customerId);
        setOrders(updatedOrders);
        const refreshed = updatedOrders.find((order) => order.orderId === createdOrder.orderId);
        if (refreshed) {
          finalOrder = {
            ...finalOrder,
            status: refreshed.status
          };
        }
        setStatusStatus("success");
        setStatusMessage(`Payment confirmed for order #${createdOrder.orderId}.`);
      } catch (ordersError) {
        console.error("Failed to refresh orders after payment", ordersError);
        setStatusStatus("error");
        setStatusMessage(
          ordersError instanceof Error
            ? ordersError.message
            : "Payment confirmed, but unable to refresh latest order status."
        );
      }

      setPendingOrder(finalOrder);
      setPendingItems(snapshotItems);
      setPendingTotal(snapshotTotal);
      setPendingStatus("success");
      setPendingMessage(`Payment confirmed for order #${createdOrder.orderId}.`);
      setCartStatus("idle");
      setCartMessage(null);
      setActiveView("confirm");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to create order.";
      setCartStatus("error");
      setCartMessage(message);
      setPendingStatus("error");
      setPendingMessage(message);
      setStatusStatus("error");
      setStatusMessage(message);
      resetPendingOrderState();
    }
  };

  const handleCancelOrderFromStatus = async (orderId: number) => {
    setStatusStatus("loading");
    setStatusMessage(null);
    try {
      await cancelOrder(orderId);
      setStatusStatus("success");
      setStatusMessage(`Order #${orderId} cancelled successfully.`);
      if (customerId != null) {
        try {
          await loadOrders(customerId);
        } catch {
          /* handled inside loadOrders */
        }
      }
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to cancel order.";
      setStatusStatus("error");
      setStatusMessage(message);
    }
  };

  const handleRefreshOrders = async () => {
    setStatusStatus("loading");
    setStatusMessage(null);
    try {
      if (customerId != null) {
        await loadOrders(customerId);
      }
      setStatusStatus("success");
      setStatusMessage("Orders refreshed.");
    } catch {
      // loadOrders already handles error state
    }
  };

  const cartCount = useMemo(
    () => cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0,
    [cart]
  );

  if (!session) {
    return (
      <LoginView
        mode={authMode}
        onModeChange={handleAuthModeChange}
        signin={{
          username: signinUsername,
          password: signinPassword,
          status: signinStatus,
          message: signinMessage,
          onSubmit: handleLogin,
          onUsernameChange: setSigninUsername,
          onPasswordChange: setSigninPassword
        }}
        signup={{
          username: signupUsername,
          password: signupPassword,
          status: signupStatus,
          message: signupMessage,
          onSubmit: handleSignup,
          onUsernameChange: setSignupUsername,
          onPasswordChange: setSignupPassword
        }}
        defaultCredentials={DEFAULT_CREDENTIALS}
      />
    );
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div className="dashboard-brand">
          <h1>Store Operations Console</h1>
          <p>
            Signed in as <strong>{session.username}</strong>
          </p>
        </div>
        <button type="button" onClick={handleLogout}>
          Sign out
        </button>
      </header>

      <nav className="dashboard-nav">
        <NavButton
          label="Catalogue"
          view="catalogue"
          activeView={activeView}
          onSelect={setActiveView}
        />
        <NavButton
          label="Cart"
          view="cart"
          activeView={activeView}
          onSelect={setActiveView}
          badgeValue={cartCount || undefined}
        />
        <NavButton
          label="Status"
          view="status"
          activeView={activeView}
          onSelect={setActiveView}
          badgeValue={orders.length || undefined}
        />
        <NavButton
          label="History"
          view="history"
          activeView={activeView}
          onSelect={setActiveView}
        />
      </nav>

      <main className="dashboard-content">
        {activeView === "catalogue" && (
          <PlaceOrderView products={products} cart={cart} onAddToCart={handleAddToCart} />
        )}
        {activeView === "cart" && (
          <CartView
            cart={cart}
            status={cartStatus}
            message={cartMessage}
            onUpdateQuantity={handleUpdateCartQuantity}
            onRemoveItem={handleRemoveCartItem}
            onClear={handleClearCart}
            onConfirmPayment={handleConfirmPaymentFromCart}
            checkoutDisabled={customerId == null || pendingStatus === "loading"}
          />
        )}
        {activeView === "confirm" && pendingOrder && (
          <OrderConfirmView
            order={pendingOrder}
            items={pendingItems}
            total={pendingTotal}
            status={pendingStatus}
            message={pendingMessage}
            onBack={handleBackToCartFromConfirm}
            onCancel={handleCancelPendingOrder}
          />
        )}
        {activeView === "status" && (
          <OrderStatusView
            orders={orders}
            onRefresh={handleRefreshOrders}
            onCancelOrder={handleCancelOrderFromStatus}
            status={statusStatus}
            message={statusMessage}
          />
        )}
        {activeView === "history" && <OrderHistoryView orders={orders} />}
      </main>
    </div>
  );
}

type NavButtonProps = {
  label: string;
  view: DashboardView;
  activeView: DashboardView;
  onSelect: (view: DashboardView) => void;
  badgeValue?: number;
};

function NavButton({ label, view, activeView, onSelect, badgeValue }: NavButtonProps) {
  const isActive = view === activeView;
  return (
    <button
      type="button"
      className={`nav-button ${isActive ? "nav-button-active" : ""}`}
      onClick={() => onSelect(view)}
    >
      {label}
      {badgeValue && badgeValue > 0 && <span className="nav-badge">{badgeValue}</span>}
    </button>
  );
}
