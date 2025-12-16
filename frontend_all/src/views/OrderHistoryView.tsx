import type { CustomerOrderSummary } from "../types";

type OrderHistoryViewProps = {
  orders: CustomerOrderSummary[];
};

export function OrderHistoryView({ orders }: OrderHistoryViewProps) {
  return (
    <section>
      <header className="view-header">
        <div>
          <h2>Order History</h2>
          <p>All orders created for this customer during the current session.</p>
        </div>
      </header>

      {orders.length === 0 ? (
        <div className="history-empty">
          <p>No orders recorded yet.</p>
        </div>
      ) : (
        <table className="history-table">
          <thead>
            <tr>
              <th scope="col">Order</th>
              <th scope="col">Status</th>
              <th scope="col">Total</th>
              <th scope="col">Created</th>
              <th scope="col">Updated</th>
              <th scope="col">Items</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.orderId}>
                <td>#{order.orderId}</td>
                <td>{order.status}</td>
                <td>${order.orderTotal.toFixed(2)}</td>
                <td>{formatIso(order.createdAt)}</td>
                <td>{formatIso(order.updatedAt)}</td>
                <td>
                  {order.items
                    .map((item) => `${item.productName ?? "Unknown"} ×${item.quantity}`)
                    .join(", ")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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

