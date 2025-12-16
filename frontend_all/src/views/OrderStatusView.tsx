import { useMemo, useState } from "react";
import type { CustomerOrderSummary } from "../types";

type OrderStatusViewProps = {
  orders: CustomerOrderSummary[];
  onRefresh: () => void;
  onCancelOrder: (orderId: number) => void;
  status: "idle" | "loading" | "success" | "error";
  message: string | null;
};

export function OrderStatusView({
  orders,
  onRefresh,
  onCancelOrder,
  status,
  message
}: OrderStatusViewProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const selectedOrder = useMemo(() => {
    if (!orders.length) return null;
    const byId = selectedId != null ? orders.find((order) => order.orderId === selectedId) : null;
    return byId ?? orders[0];
  }, [orders, selectedId]);

  const isLoading = status === "loading";
  const isError = status === "error";
  const isSuccess = status === "success";

  return (
    <section>
      <header className="view-header">
        <div>
          <h2>Order Status</h2>
          <p>Review the latest orders for this customer. Reservation and payment now happen via the cart.</p>
        </div>
        <button type="button" onClick={onRefresh} disabled={isLoading}>
          Refresh
        </button>
      </header>

      {!orders.length ? (
        <div className="status-empty">
          <p>No orders have been placed yet.</p>
        </div>
      ) : (
        <div className="status-layout">
          <div className="status-sidebar">
            <label>
              Order
              <select
                value={selectedOrder?.orderId ?? ""}
                onChange={(event) => {
                  const next = Number.parseInt(event.target.value, 10);
                  setSelectedId(Number.isNaN(next) ? null : next);
                }}
              >
                {orders.map((order) => (
                  <option key={order.orderId} value={order.orderId}>
                    #{order.orderId} • {order.status}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {selectedOrder && (
            <div className="status-details">
              <h3>Order details</h3>
              <dl>
                <div className="timeline-row">
                  <dt>Order ID</dt>
                  <dd>#{selectedOrder.orderId}</dd>
                </div>
                <div className="timeline-row">
                  <dt>Status</dt>
                  <dd>{selectedOrder.status}</dd>
                </div>
                <div className="timeline-row">
                  <dt>Total</dt>
                  <dd>${selectedOrder.orderTotal.toFixed(2)}</dd>
                </div>
                <div className="timeline-row">
                  <dt>Created</dt>
                  <dd>{formatIso(selectedOrder.createdAt)}</dd>
                </div>
                <div className="timeline-row">
                  <dt>Updated</dt>
                  <dd>{formatIso(selectedOrder.updatedAt)}</dd>
                </div>
              </dl>

              <div className="order-actions">
                <button 
                  type="button" 
                  onClick={() => onCancelOrder(selectedOrder.orderId)}
                  disabled={isLoading || selectedOrder.status === "CANCELLED"}
                  className="cancel-order-button"
                >
                  {selectedOrder.status === "CANCELLED" ? "Order Cancelled" : "Cancel Order"}
                </button>
              </div>

              <h4>Line items</h4>
              <table className="status-lines">
                <thead>
                  <tr>
                    <th scope="col">Product</th>
                    <th scope="col">Quantity</th>
                    <th scope="col">Unit price</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedOrder.items.map((item, index) => (
                    <tr key={`${item.productId ?? index}-${item.productSku ?? index}`}>
                      <td>{item.productName ?? "Unknown"}</td>
                      <td>{item.quantity}</td>
                      <td>${item.unitPrice.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
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

function formatIso(iso: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
