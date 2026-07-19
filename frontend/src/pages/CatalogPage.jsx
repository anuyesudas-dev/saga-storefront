import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useCart } from "../context/CartContext";

export default function CatalogPage() {
  const [products, setProducts] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState(null);
  const { addItem } = useCart();

  useEffect(() => {
    let cancelled = false;
    api
      .listCatalog()
      .then((data) => {
        if (!cancelled) {
          setProducts(data);
          setStatus("ready");
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message);
          setStatus("error");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (status === "loading") return <p>Loading catalog...</p>;
  if (status === "error") return <p className="error">Could not load catalog: {error}</p>;

  return (
    <section>
      <h1>Catalog</h1>
      {products.length === 0 ? (
        <p>No products yet.</p>
      ) : (
        <div className="product-grid">
          {products.map((product) => (
            <article key={product.productId} className="product-card">
              <h2>{product.name}</h2>
              <p className="price">${product.price.toFixed(2)}</p>
              <p className="stock">
                {product.availableQty > 0
                  ? `${product.availableQty} in stock`
                  : "Out of stock"}
              </p>
              <button
                type="button"
                disabled={product.availableQty === 0}
                onClick={() => addItem(product, 1)}
              >
                Add to cart
              </button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
