import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";

export default function CartPage() {
  const { items, updateQuantity, removeItem, total } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  if (items.length === 0) {
    return (
      <section>
        <h1>Cart</h1>
        <p>
          Your cart is empty. <Link to="/">Browse the catalog</Link>.
        </p>
      </section>
    );
  }

  return (
    <section>
      <h1>Cart</h1>
      <table className="cart-table">
        <thead>
          <tr>
            <th>Item</th>
            <th>Unit price</th>
            <th>Quantity</th>
            <th>Subtotal</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.productId}>
              <td>{item.productName}</td>
              <td>${item.unitPrice.toFixed(2)}</td>
              <td>
                <input
                  type="number"
                  min="1"
                  value={item.quantity}
                  onChange={(e) =>
                    updateQuantity(item.productId, Math.max(1, Number(e.target.value)))
                  }
                />
              </td>
              <td>${(item.unitPrice * item.quantity).toFixed(2)}</td>
              <td>
                <button type="button" onClick={() => removeItem(item.productId)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="cart-total">Total: ${total.toFixed(2)}</p>
      <button
        type="button"
        onClick={() => navigate(isAuthenticated ? "/checkout" : "/login")}
      >
        {isAuthenticated ? "Checkout" : "Log in to checkout"}
      </button>
    </section>
  );
}
