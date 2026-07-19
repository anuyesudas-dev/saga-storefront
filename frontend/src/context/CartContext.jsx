import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

// Cart is browser state until checkout - no cart-service, no cross-device
// carts (docs/edge-design.md's recorded trade-off). localStorage keeps it
// across reloads within the same browser.
const CART_KEY = "saga-storefront.cart";

const CartContext = createContext(null);

function loadStoredCart() {
  const raw = localStorage.getItem(CART_KEY);
  return raw ? JSON.parse(raw) : [];
}

export function CartProvider({ children }) {
  const [items, setItems] = useState(loadStoredCart);

  useEffect(() => {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
  }, [items]);

  const addItem = useCallback((product, quantity) => {
    setItems((current) => {
      const existing = current.find((item) => item.productId === product.productId);
      if (existing) {
        return current.map((item) =>
          item.productId === product.productId
            ? { ...item, quantity: item.quantity + quantity }
            : item
        );
      }
      return [
        ...current,
        {
          productId: product.productId,
          productName: product.name,
          unitPrice: product.price,
          quantity,
        },
      ];
    });
  }, []);

  const removeItem = useCallback((productId) => {
    setItems((current) => current.filter((item) => item.productId !== productId));
  }, []);

  const updateQuantity = useCallback((productId, quantity) => {
    setItems((current) =>
      current.map((item) => (item.productId === productId ? { ...item, quantity } : item))
    );
  }, []);

  const clear = useCallback(() => setItems([]), []);

  const value = useMemo(() => {
    const total = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
    return { items, addItem, removeItem, updateQuantity, clear, total };
  }, [items, addItem, removeItem, updateQuantity, clear]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error("useCart must be used within a CartProvider");
  }
  return context;
}
