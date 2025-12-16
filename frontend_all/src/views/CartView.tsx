import type { CartSnapshot } from "../types";

type CartViewProps = {
  cart: CartSnapshot | null;
  status: "idle" | "loading" | "success" | "error";
  message: string | null;
  onUpdateQuantity: (productId: number, quantity: number) => void;
  onRemoveItem: (productId: number) => void;
  onClear: () => void;
  onConfirmPayment: () => void;
  checkoutDisabled?: boolean;
};

export function CartView({
  cart,
  status,
  message,
  onUpdateQuantity,
  onRemoveItem,
  onClear,
  onConfirmPayment,
  checkoutDisabled = false
}: CartViewProps) {
  const items = cart?.items ?? [];
  const total = cart?.cartTotal ?? 0;
  const isLoading = status === "loading";
  const isSuccess = status === "success";
  const isError = status === "error";

  return (
    <section>
      <header className="view-header">
        <div>
          <h2>Shopping Cart</h2>
          <p>Review all items currently queued for checkout before sending them to the store.</p>
        </div>
        <div className="inline-stats">
          <div>
            <strong>{items.length}</strong>
            <span>Line items</span>
          </div>
          <div>
            <strong>${total.toFixed(2)}</strong>
            <span>Total</span>
          </div>
        </div>
      </header>

      <div className="cart-controls">
        <button type="button" onClick={onClear} disabled={items.length === 0 || isLoading}>
          Clear cart
        </button>
        <button
          type="button"
          onClick={onConfirmPayment}
          disabled={items.length === 0 || isLoading || checkoutDisabled}
        >
          {isLoading || checkoutDisabled ? "Processing…" : "Confirm payment"}
        </button>
      </div>

      {items.length === 0 ? (
        <div className="cart-empty">
          <p>The cart is empty. Add products from the catalogue to assemble a new order.</p>
        </div>
      ) : (
        <table className="cart-table">
          <thead>
            <tr>
              <th scope="col">Product</th>
              <th scope="col">Unit price</th>
              <th scope="col">Quantity</th>
              <th scope="col">Line total</th>
              <th scope="col">Actions</th>
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
                <td>
                  <input
                    type="number"
                    min={1}
                    value={item.quantity}
                    onChange={(event) => {
                      const next = Number.parseInt(event.target.value, 10);
                      onUpdateQuantity(
                        item.productId,
                        Number.isNaN(next) || next < 1 ? 1 : next
                      );
                    }}
                    disabled={isLoading}
                  />
                </td>
                <td>${item.lineTotal.toFixed(2)}</td>
                <td>
                  <button
                    type="button"
                    onClick={() => onRemoveItem(item.productId)}
                    disabled={isLoading}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {message && (
        <p
          className={`feedback ${
            isError ? "feedback-error" : isSuccess ? "feedback-success" : ""
          }`}
        >
          {message}
        </p>
      )}
    </section>
  );
}
