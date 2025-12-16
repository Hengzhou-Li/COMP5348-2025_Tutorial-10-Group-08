export type Product = {
  id: number;
  sku: string;
  name: string;
  description: string;
  unitPrice: number;
  active: boolean;
};

export type CartItem = {
  productId: number;
  sku: string;
  name: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
};

export type CartSnapshot = {
  customerId: number;
  items: CartItem[];
  cartTotal: number;
};

export type OrderItemInput = {
  productId: number;
  quantity: number;
};

export type CustomerOrderItem = {
  productId: number | null;
  productSku: string | null;
  productName: string | null;
  quantity: number;
  unitPrice: number;
};

export type CustomerOrderSummary = {
  orderId: number;
  orderTotal: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  items: CustomerOrderItem[];
};

export type CustomerProfile = {
  customerId: number;
  fullName: string;
};

export type ReserveStockResponse = {
  orderId: number;
  status: string;
};

export type CreateOrderResponse = {
  orderId: number;
  status: string;
  correlationId: string;
};
