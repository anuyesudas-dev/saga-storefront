import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";

export default function Layout() {
  const { isAuthenticated, user, logout } = useAuth();
  const { items } = useCart();
  const cartCount = items.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand">
          Saga Storefront
        </Link>
        <nav>
          <NavLink to="/">Catalog</NavLink>
          <NavLink to="/cart">Cart{cartCount > 0 ? ` (${cartCount})` : ""}</NavLink>
          {isAuthenticated && <NavLink to="/orders">My Orders</NavLink>}
        </nav>
        <div className="auth-status">
          {isAuthenticated ? (
            <>
              <span>{user.displayName}</span>
              <button type="button" onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Log in</Link>
              <Link to="/register">Register</Link>
            </>
          )}
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
