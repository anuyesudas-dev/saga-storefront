import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { useCart } from "../context/CartContext";

export default function CheckoutPage() {
  const { items, total, clear } = useCart();
  const navigate = useNavigate();
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState(null);

  async function placeOrder() {
    setPlacing(true);
    setError(null);
    try {
      const orderItems = items.map((item) => ({
        productId: item.productId,
        productName: item.productName,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      }));
      const order = await api.createOrder("USD", orderItems);
      clear();
      navigate(`/orders/${order.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setPlacing(false);
    }
  }

  if (items.length === 0) {
    return (
      <section>
        <h1>Checkout</h1>
        <p>Your cart is empty.</p>
      </section>
    );
  }

  return (
    <section>
      <h1>Checkout</h1>
      <ul className="checkout-summary">
        {items.map((item) => (
          <li key={item.productId}>
            {item.quantity} &times; {item.productName} - $
            {(item.unitPrice * item.quantity).toFixed(2)}
          </li>
        ))}
      </ul>
      <p className="cart-total">Total: ${total.toFixed(2)}</p>
      {error && <p className="error">{error}</p>}
      <button type="button" disabled={placing} onClick={placeOrder}>
        {placing ? "Placing order..." : "Place order"}
      </button>
    </section>
  );
}
