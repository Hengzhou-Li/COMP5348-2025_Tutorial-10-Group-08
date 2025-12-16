import type { CartItem } from "../types";
import type { CreateOrderResponse } from "../types";

export type OrderConfirmViewProps = {
  order: CreateOrderResponse;
  items: CartItem[];
  total: number;
  status: "idle" | "loading" | "success" | "error";
  message: string | null;
  onBack: () => void;
  onCancel: () => void;
};

export function OrderConfirmView({
  order,
  items,
  total,
  status,
  message,
  onBack,
  onCancel
}: OrderConfirmViewProps) {
  const isProcessing = status === "loading";
  const isSuccess = status === "success";
  const isError = status === "error";

  return (
    <section>
      <header className="view-header">
        <div>
          <h2>Confirm Order</h2>
          <p>Payment has already been submitted. Review the details or cancel the order if needed.</p>
        </div>
        <div className="inline-stats">
          <div>
            <strong>#{order.orderId}</strong>
            <span>Order ID</span>
          </div>
          <div>
            <strong>{order.status}</strong>
            <span>Status</span>
          </div>
        </div>
      </header>

      <article className="order-confirm-card">
        <h3>Items in this order</h3>
        {items.length === 0 ? (
          <p>No line items found. Return to the cart to add products.</p>
        ) : (
          <table className="cart-table">
            <thead>
              <tr>
                <th scope="col">Product</th>
                <th scope="col">Unit price</th>
                <th scope="col">Quantity</th>
                <th scope="col">Line total</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.productId}>
                  <td>
                    <div className="cart-product-name">{item.name}</div>
                    <div className="cart-product-sku">{item.sku}</div>
                  </td>
                  <td>${item.unitPrice.toFixed(2)}</td>
                  <td>{item.quantity}</td>
                  <td>${item.lineTotal.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div className="order-confirm-total">
          <span>Total due</span>
          <strong>${total.toFixed(2)}</strong>
        </div>
      </article>

      <div className="order-confirm-actions">
        <button type="button" onClick={onBack} disabled={isProcessing}>
          Back to cart
        </button>
        <button type="button" onClick={onCancel} disabled={isProcessing || items.length === 0}>
          Cancel order
        </button>
      </div>

      {message && (
        <p
          className={`feedback ${
            isError ? "feedback-error" : isSuccess ? "feedback-success" : ""
          }`}
        >
          {message}
        </p>
      )}

      <p className="order-confirm-hint">
        Cart submission invoked <code>POST /api/orders/{order.orderId}/reserve</code> followed by{" "}
        <code>POST /api/orders/{order.orderId}/payment</code>. Cancelling still calls{" "}
        <code>POST /api/orders/{order.orderId}/cancel</code>.
      </p>
    </section>
  );
}
