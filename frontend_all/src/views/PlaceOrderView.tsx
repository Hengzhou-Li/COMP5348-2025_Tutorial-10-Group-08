import { useMemo, useState } from "react";
import type { CartSnapshot, Product } from "../types";

type PlaceOrderViewProps = {
  products: Product[];
  cart: CartSnapshot | null;
  onAddToCart: (productId: number, quantity: number) => void;
};

export function PlaceOrderView({ products, cart, onAddToCart }: PlaceOrderViewProps) {
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [filter, setFilter] = useState("");

  const quantitiesInCart = useMemo(() => {
    if (!cart) {
      return new Map<number, number>();
    }
    return new Map(cart.items.map((item) => [item.productId, item.quantity]));
  }, [cart]);

  const filteredProducts = useMemo(() => {
    const query = filter.trim().toLowerCase();
    if (!query) return products;
    return products.filter((product) => {
      const haystack = `${product.sku} ${product.name} ${product.description}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [filter, products]);

  const handleAdd = (product: Product) => {
    const quantity = quantities[product.id] ?? 1;
    const safeQuantity = Number.isFinite(quantity) && quantity > 0 ? quantity : 1;
    onAddToCart(product.id, Math.floor(safeQuantity));
    setQuantities((prev) => ({ ...prev, [product.id]: 1 }));
  };

  return (
    <section>
      <header className="view-header">
        <div>
          <h2>Product Catalogue</h2>
          <p>Search the live product catalogue and add items directly into the customer cart.</p>
        </div>
        <div className="inline-stats">
          <div>
            <strong>{products.length}</strong>
            <span>Products</span>
          </div>
          <div>
            <strong>{quantitiesInCart.size}</strong>
            <span>In cart</span>
          </div>
        </div>
      </header>

      <div className="catalog-filter">
        <input
          value={filter}
          placeholder="Search by SKU or product name"
          onChange={(event) => setFilter(event.target.value)}
        />
      </div>

      <div className="catalog-grid">
        {filteredProducts.map((product) => {
          const quantity = quantities[product.id] ?? 1;
          const quantityInCart = quantitiesInCart.get(product.id) ?? 0;
          const disabled = !product.active;

          return (
            <article className="product-card" key={product.id}>
              <header>
                <h3>{product.name}</h3>
                <span className="product-sku">{product.sku}</span>
              </header>
              <p className="product-description">{product.description}</p>
              <p className="product-price">${product.unitPrice.toFixed(2)}</p>
              {!product.active && (
                <p className="product-status product-status-inactive">Currently inactive</p>
              )}
              {quantityInCart > 0 && (
                <p className="product-status product-status-cart">
                  {quantityInCart} in cart
                </p>
              )}
              <div className="product-actions">
                <label>
                  Quantity
                  <input
                    type="number"
                    min={1}
                    value={quantity}
                    onChange={(event) => {
                      const next = Number.parseInt(event.target.value, 10);
                      setQuantities((prev) => ({
                        ...prev,
                        [product.id]: Number.isNaN(next) || next < 1 ? 1 : next
                      }));
                    }}
                    disabled={disabled}
                  />
                </label>
                <button type="button" onClick={() => handleAdd(product)} disabled={disabled}>
                  Add to cart
                </button>
              </div>
            </article>
          );
        })}
        {filteredProducts.length === 0 && (
          <div className="product-empty">
            <p>No products matched the search query.</p>
          </div>
        )}
      </div>
    </section>
  );
}

