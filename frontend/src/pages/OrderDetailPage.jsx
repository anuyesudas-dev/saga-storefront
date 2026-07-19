import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";

const TERMINAL_STATUSES = new Set(["PAID", "FAILED", "CANCELLED"]);
const POLL_INTERVAL_MS = 2000;

export default function OrderDetailPage() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const fetchOrder = useCallback(() => api.getOrder(orderId), [orderId]);

  useEffect(() => {
    let cancelled = false;
    let timer;

    async function poll() {
      try {
        const data = await fetchOrder();
        if (cancelled) return;
        setOrder(data);
        if (!TERMINAL_STATUSES.has(data.status)) {
          timer = setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (err) {
        if (!cancelled) setError(err.message);
      }
    }

    poll();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [fetchOrder]);

  async function handleCancel() {
    setCancelling(true);
    setError(null);
    try {
      const updated = await api.cancelOrder(orderId, "customer request");
      setOrder(updated);
    } catch (err) {
      setError(err.message);
    } finally {
      setCancelling(false);
    }
  }

  if (error && !order) {
    return (
      <section>
        <p className="error">Could not load order: {error}</p>
        <Link to="/orders">Back to My Orders</Link>
      </section>
    );
  }
  if (!order) return <p>Loading order...</p>;

  const canCancel = order.status === "CREATED" || order.status === "INVENTORY_RESERVED";
  const isPolling = !TERMINAL_STATUSES.has(order.status);

  return (
    <section>
      <h1>Order {order.id}</h1>
      <p>
        Status: <span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>
        {isPolling && <span className="polling-indicator"> - updating live</span>}
      </p>
      {order.statusReason && <p className="status-reason">{order.statusReason}</p>}

      <ul className="checkout-summary">
        {order.items.map((item) => (
          <li key={item.productId}>
            {item.quantity} &times; {item.productName} - $
            {(item.unitPrice * item.quantity).toFixed(2)}
          </li>
        ))}
      </ul>
      <p className="cart-total">
        Total: ${order.totalAmount.toFixed(2)} {order.currency}
      </p>

      {error && <p className="error">{error}</p>}
      {canCancel && (
        <button type="button" disabled={cancelling} onClick={handleCancel}>
          {cancelling ? "Cancelling..." : "Cancel order"}
        </button>
      )}

      <p>
        <Link to="/orders">Back to My Orders</Link>
      </p>
    </section>
  );
}
