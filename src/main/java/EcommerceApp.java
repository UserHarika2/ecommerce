package com.ecommerce;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * E‑Commerce Application – Like Amazon
 * Thread‑safe inventory, cart, and order management.
 */
public class EcommerceApp {

    // ---------- Data stores ----------
    private static final Map<String, Product> products = new ConcurrentHashMap<>();
    private static final Map<String, Cart> userCarts = new ConcurrentHashMap<>();
    private static final Map<String, List<Order>> userOrders = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> inventory = new ConcurrentHashMap<>();
    private static HttpServer server;
    private static int assignedPort;

    // ---------- Inner classes ----------
    public static class Product {
        private final String id;
        private final String name;
        private final String description;
        private final long price; // in paise/cents
        private final String currency;

        public Product(String id, String name, String description, long price, String currency) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.currency = currency;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public long getPrice() { return price; }
        public String getCurrency() { return currency; }

        public String toJson() {
            return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"price\":%d,\"currency\":\"%s\"}",
                escapeJson(id), escapeJson(name), escapeJson(description), price, escapeJson(currency));
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }
    }

    public static class CartItem {
        private final String productId;
        private final int quantity;

        public CartItem(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
    }

    public static class Cart {
        private final Map<String, Integer> items = new ConcurrentHashMap<>();

        public void addItem(String productId, int quantity) {
            items.merge(productId, quantity, Integer::sum);
        }

        public boolean removeItem(String productId) {
            return items.remove(productId) != null;
        }

        public void updateQuantity(String productId, int quantity) {
            if (quantity <= 0) items.remove(productId);
            else items.put(productId, quantity);
        }

        public Map<String, Integer> getItems() { return new HashMap<>(items); }
        public void clear() { items.clear(); }
    }

    public static class Order {
        private final String id;
        private final String userId;
        private final List<OrderItem> items;
        private final long totalAmount;
        private final String currency;
        private final String status; // PENDING, CONFIRMED, CANCELLED
        private final String timestamp;
        private final String formattedDate;

        public Order(String userId, List<OrderItem> items, long totalAmount, String currency, String status) {
            this.id = UUID.randomUUID().toString();
            this.userId = userId;
            this.items = new ArrayList<>(items);
            this.totalAmount = totalAmount;
            this.currency = currency;
            this.status = status;
            this.timestamp = LocalDateTime.now().toString();
            this.formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // Getters
        public String getId() { return id; }
        public String getUserId() { return userId; }
        public List<OrderItem> getItems() { return items; }
        public long getTotalAmount() { return totalAmount; }
        public String getCurrency() { return currency; }
        public String getStatus() { return status; }
        public String getTimestamp() { return timestamp; }
        public String getFormattedDate() { return formattedDate; }

        public String toJson() {
            StringBuilder itemsJson = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) itemsJson.append(",");
                itemsJson.append(items.get(i).toJson());
            }
            itemsJson.append("]");
            return String.format(
                "{\"id\":\"%s\",\"userId\":\"%s\",\"items\":%s,\"totalAmount\":%d,\"currency\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\",\"formattedDate\":\"%s\"}",
                escapeJson(id), escapeJson(userId), itemsJson, totalAmount, escapeJson(currency), escapeJson(status), escapeJson(timestamp), escapeJson(formattedDate)
            );
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }
    }

    public static class OrderItem {
        private final String productId;
        private final String productName;
        private final int quantity;
        private final long unitPrice;

        public OrderItem(String productId, String productName, int quantity, long unitPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public long getUnitPrice() { return unitPrice; }

        public String toJson() {
            return String.format("{\"productId\":\"%s\",\"productName\":\"%s\",\"quantity\":%d,\"unitPrice\":%d}",
                escapeJson(productId), escapeJson(productName), quantity, unitPrice);
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }
    }

    // ---------- Initialization (sample products) ----------
    static {
        // Add some sample products
        addProduct(new Product("p1", "Laptop", "High performance laptop", 7500000, "INR")); // 75,000 INR
        addProduct(new Product("p2", "Mouse", "Wireless mouse", 150000, "INR"));          // 1,500 INR
        addProduct(new Product("p3", "Keyboard", "Mechanical keyboard", 450000, "INR"));  // 4,500 INR
        addProduct(new Product("p4", "Monitor", "27 inch 4K", 2500000, "INR"));          // 25,000 INR

        // Initial inventory
        updateInventory("p1", 10);
        updateInventory("p2", 50);
        updateInventory("p3", 30);
        updateInventory("p4", 15);
    }

    // ---------- Product & Inventory Management ----------
    public static void addProduct(Product product) {
        products.put(product.getId(), product);
    }

    public static List<Product> getAllProducts() {
        return new ArrayList<>(products.values());
    }

    public static Product getProduct(String productId) {
        return products.get(productId);
    }

    public static void updateInventory(String productId, int quantity) {
        inventory.put(productId, new AtomicInteger(quantity));
    }

    public static int getInventory(String productId) {
        AtomicInteger inv = inventory.get(productId);
        return inv == null ? 0 : inv.get();
    }

    /**
     * Thread‑safe inventory deduction.
     * @return true if enough stock and deducted, false otherwise.
     */
    public static boolean deductInventory(String productId, int quantity) {
        if (quantity <= 0) return false;
        AtomicInteger inv = inventory.get(productId);
        if (inv == null) return false;
        int current;
        do {
            current = inv.get();
            if (current < quantity) return false;
        } while (!inv.compareAndSet(current, current - quantity));
        return true;
    }

    // ---------- Cart Operations ----------
    private static Cart getOrCreateCart(String userId) {
        return userCarts.computeIfAbsent(userId, k -> new Cart());
    }

    public static boolean addToCart(String userId, String productId, int quantity) {
        if (quantity <= 0) return false;
        Product product = getProduct(productId);
        if (product == null) return false;
        Cart cart = getOrCreateCart(userId);
        cart.addItem(productId, quantity);
        return true;
    }

    public static boolean removeFromCart(String userId, String productId) {
        Cart cart = userCarts.get(userId);
        if (cart == null) return false;
        return cart.removeItem(productId);
    }

    public static Map<String, Integer> getCart(String userId) {
        Cart cart = userCarts.get(userId);
        return cart == null ? new HashMap<>() : cart.getItems();
    }

    public static void clearCart(String userId) {
        Cart cart = userCarts.get(userId);
        if (cart != null) cart.clear();
    }

    // ---------- Checkout & Orders ----------
    public static Order checkout(String userId) {
        Cart cart = userCarts.get(userId);
        if (cart == null || cart.getItems().isEmpty()) {
            return new Order(userId, new ArrayList<>(), 0, "INR", "FAILED_EMPTY_CART");
        }

        Map<String, Integer> items = cart.getItems();
        List<OrderItem> orderItems = new ArrayList<>();
        long total = 0;

        // First, check inventory availability without modifying
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String productId = entry.getKey();
            int qty = entry.getValue();
            Product product = getProduct(productId);
            if (product == null) {
                return new Order(userId, new ArrayList<>(), 0, "INR", "FAILED_PRODUCT_NOT_FOUND");
            }
            if (getInventory(productId) < qty) {
                return new Order(userId, new ArrayList<>(), 0, "INR", "FAILED_OUT_OF_STOCK");
            }
        }

        // Now deduct inventory atomically (if any fails, rollback)
        List<String> deductedProducts = new ArrayList<>();
        try {
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                String productId = entry.getKey();
                int qty = entry.getValue();
                boolean deducted = deductInventory(productId, qty);
                if (!deducted) {
                    throw new RuntimeException("Inventory deduction failed for " + productId);
                }
                deductedProducts.add(productId);
                Product product = getProduct(productId);
                orderItems.add(new OrderItem(productId, product.getName(), qty, product.getPrice()));
                total += product.getPrice() * qty;
            }
        } catch (Exception e) {
            // Rollback deducted inventory
            for (String pid : deductedProducts) {
                AtomicInteger inv = inventory.get(pid);
                if (inv != null) inv.addAndGet(items.get(pid));
            }
            return new Order(userId, new ArrayList<>(), 0, "INR", "FAILED_INVENTORY_ERROR");
        }

        // Create order
        Order order = new Order(userId, orderItems, total, "INR", "CONFIRMED");
        userOrders.computeIfAbsent(userId, k -> new ArrayList<>()).add(order);
        // Clear cart after successful checkout
        clearCart(userId);
        return order;
    }

    public static List<Order> getOrderHistory(String userId) {
        return userOrders.getOrDefault(userId, new ArrayList<>()).stream()
                .sorted((a,b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    // ---------- Reset for testing ----------
    public static void reset() {
        products.clear();
        userCarts.clear();
        userOrders.clear();
        inventory.clear();
        // Re-initialize sample data
        addProduct(new Product("p1", "Laptop", "High performance laptop", 7500000, "INR"));
        addProduct(new Product("p2", "Mouse", "Wireless mouse", 150000, "INR"));
        addProduct(new Product("p3", "Keyboard", "Mechanical keyboard", 450000, "INR"));
        addProduct(new Product("p4", "Monitor", "27 inch 4K", 2500000, "INR"));
        updateInventory("p1", 10);
        updateInventory("p2", 50);
        updateInventory("p3", 30);
        updateInventory("p4", 15);
    }

    // ---------- JSON Helpers ----------
    private static String toJson(List<Product> products) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(products.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    private static String cartToJson(Map<String, Integer> cart) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            if (i > 0) sb.append(",");
            Product p = getProduct(entry.getKey());
            sb.append(String.format("{\"productId\":\"%s\",\"name\":\"%s\",\"quantity\":%d,\"price\":%d}",
                entry.getKey(), p != null ? p.getName() : "Unknown", entry.getValue(), p != null ? p.getPrice() : 0));
            i++;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String ordersToJson(List<Order> orders) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(orders.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    // ---------- HTTP Server ----------
    private static class EcommerceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if (method.equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                if (path.equals("/") || path.equals("/index.html")) {
                    serveHtml(exchange);
                } else if (path.equals("/api/products") && method.equals("GET")) {
                    handleGetProducts(exchange);
                } else if (path.equals("/api/cart/add") && method.equals("POST")) {
                    handleAddToCart(exchange);
                } else if (path.equals("/api/cart/remove") && method.equals("POST")) {
                    handleRemoveFromCart(exchange);
                } else if (path.equals("/api/cart") && method.equals("GET")) {
                    handleGetCart(exchange);
                } else if (path.equals("/api/checkout") && method.equals("POST")) {
                    handleCheckout(exchange);
                } else if (path.equals("/api/orders") && method.equals("GET")) {
                    handleGetOrders(exchange);
                } else if (path.equals("/api/inventory") && method.equals("GET")) {
                    handleGetInventory(exchange);
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private void serveHtml(HttpExchange exchange) throws IOException {
            String html = getHtmlContent();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            sendResponse(exchange, 200, html);
        }

        private void handleGetProducts(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, toJson(getAllProducts()));
        }

        private void handleAddToCart(HttpExchange exchange) throws IOException {
            Map<String, String> params = parsePostBody(exchange);
            String userId = params.get("userId");
            String productId = params.get("productId");
            int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
            boolean ok = addToCart(userId, productId, quantity);
            sendResponse(exchange, ok ? 200 : 400, ok ? "{\"status\":\"added\"}" : "{\"error\":\"failed\"}");
        }

        private void handleRemoveFromCart(HttpExchange exchange) throws IOException {
            Map<String, String> params = parsePostBody(exchange);
            String userId = params.get("userId");
            String productId = params.get("productId");
            boolean ok = removeFromCart(userId, productId);
            sendResponse(exchange, ok ? 200 : 400, ok ? "{\"status\":\"removed\"}" : "{\"error\":\"not found\"}");
        }

        private void handleGetCart(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange);
            String userId = params.get("userId");
            if (userId == null) {
                sendResponse(exchange, 400, "{\"error\":\"userId required\"}");
                return;
            }
            Map<String, Integer> cart = getCart(userId);
            sendResponse(exchange, 200, cartToJson(cart));
        }

        private void handleCheckout(HttpExchange exchange) throws IOException {
            Map<String, String> params = parsePostBody(exchange);
            String userId = params.get("userId");
            if (userId == null) {
                sendResponse(exchange, 400, "{\"error\":\"userId required\"}");
                return;
            }
            Order order = checkout(userId);
            sendResponse(exchange, order.getStatus().equals("CONFIRMED") ? 200 : 400, order.toJson());
        }

        private void handleGetOrders(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange);
            String userId = params.get("userId");
            if (userId == null) {
                sendResponse(exchange, 400, "{\"error\":\"userId required\"}");
                return;
            }
            List<Order> orders = getOrderHistory(userId);
            sendResponse(exchange, 200, ordersToJson(orders));
        }

        private void handleGetInventory(HttpExchange exchange) throws IOException {
            Map<String, Integer> invMap = new HashMap<>();
            for (String pid : products.keySet()) {
                invMap.put(pid, getInventory(pid));
            }
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<String, Integer> e : invMap.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            }
            sb.append("}");
            sendResponse(exchange, 200, sb.toString());
        }

        private Map<String, String> parseQueryParams(HttpExchange exchange) {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> map = new HashMap<>();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) map.put(kv[0], kv[1]);
                }
            }
            return map;
        }

        private Map<String, String> parsePostBody(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> map = new HashMap<>();
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) map.put(kv[0], kv[1]);
            }
            return map;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String getHtmlContent() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>🛒 E‑Commerce | Amazon-like</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Inter', system-ui, sans-serif; }
                body {
                    background: linear-gradient(145deg, #0F172A 0%, #1E293B 100%);
                    min-height: 100vh;
                    padding: 1.5rem;
                }
                .container { max-width: 1400px; margin: 0 auto; }
                .header { text-align: center; margin-bottom: 2rem; }
                .header h1 { font-size: 2.5rem; background: linear-gradient(135deg, #FBBF24, #F59E0B); -webkit-background-clip: text; background-clip: text; color: transparent; }
                .port-badge { background: rgba(255,255,255,0.1); display: inline-block; padding: 0.3rem 1rem; border-radius: 40px; font-size: 0.8rem; margin-top: 0.5rem; color: #CBD5E1; }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; }
                .card {
                    background: rgba(30, 41, 59, 0.8);
                    backdrop-filter: blur(12px);
                    border-radius: 1.5rem;
                    padding: 1.5rem;
                    border: 1px solid rgba(255,255,255,0.1);
                    transition: 0.3s;
                }
                .card:hover { transform: translateY(-5px); border-color: #F59E0B; }
                .card h2 { color: #FDE68A; margin-bottom: 1rem; font-size: 1.4rem; border-left: 3px solid #F59E0B; padding-left: 0.75rem; }
                input, select, button {
                    width: 100%;
                    padding: 0.75rem;
                    margin: 0.5rem 0;
                    background: rgba(0,0,0,0.4);
                    border: 1px solid #475569;
                    border-radius: 1rem;
                    color: white;
                }
                button {
                    background: linear-gradient(135deg, #F59E0B, #D97706);
                    border: none;
                    font-weight: bold;
                    cursor: pointer;
                    transition: 0.2s;
                }
                button:hover { transform: scale(0.98); filter: brightness(1.05); }
                .product-item, .cart-item {
                    background: #0F172A;
                    margin: 0.5rem 0;
                    padding: 0.75rem;
                    border-radius: 1rem;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }
                .cart-total { font-size: 1.2rem; font-weight: bold; color: #FBBF24; margin-top: 1rem; }
                .order-item {
                    background: #0F172A;
                    margin: 0.75rem 0;
                    padding: 0.75rem;
                    border-radius: 1rem;
                    border-left: 3px solid #F59E0B;
                }
                .toast {
                    position: fixed;
                    bottom: 20px;
                    right: 20px;
                    background: #1E293B;
                    padding: 0.75rem 1.5rem;
                    border-radius: 2rem;
                    color: white;
                    animation: slideIn 0.3s;
                    z-index: 1000;
                }
                @keyframes slideIn {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
                .flex-between { display: flex; justify-content: space-between; align-items: center; }
                .inventory { font-size: 0.7rem; color: #94A3B8; }
            </style>
        </head>
        <body>
        <div class="container">
            <div class="header">
                <h1>🛒 E‑Commerce Store</h1>
                <p>Like Amazon – Shop, Cart, Checkout with real inventory</p>
                <div class="port-badge" id="portInfo">🔌 Connecting...</div>
            </div>

            <div class="grid">
                <!-- Products -->
                <div class="card">
                    <h2>📦 Products</h2>
                    <div id="productsList">Loading...</div>
                </div>

                <!-- Cart -->
                <div class="card">
                    <h2>🛍️ Your Cart</h2>
                    <div id="cartList">Cart is empty</div>
                    <div id="cartTotal" class="cart-total"></div>
                    <input type="text" id="checkoutUserId" placeholder="User ID for checkout">
                    <button onclick="checkout()">✅ Checkout</button>
                </div>

                <!-- Orders -->
                <div class="card">
                    <h2>📋 Order History</h2>
                    <input type="text" id="orderUserId" placeholder="User ID">
                    <button onclick="loadOrders()">View Orders</button>
                    <div id="ordersList">No orders yet</div>
                </div>
            </div>
        </div>

        <script>
            const API_URL = window.location.origin + '/api';
            document.getElementById('portInfo').innerHTML = `📍 Server on port ${window.location.port}`;
            let currentUserId = 'user1'; // demo user

            function showToast(msg, type='info') {
                const toast = document.createElement('div');
                toast.className = 'toast';
                toast.style.background = type === 'error' ? '#B91C1C' : '#065F46';
                toast.innerText = msg;
                document.body.appendChild(toast);
                setTimeout(() => toast.remove(), 3000);
            }

            async function loadProducts() {
                const res = await fetch(API_URL + '/products');
                const products = await res.json();
                const container = document.getElementById('productsList');
                container.innerHTML = products.map(p => `
                    <div class="product-item">
                        <div><strong>${p.name}</strong><br>${p.description}<br>💰 ${(p.price/100).toFixed(2)} ${p.currency}</div>
                        <div><input type="number" id="qty_${p.id}" value="1" min="1" style="width:70px"><button onclick="addToCart('${p.id}')">Add</button></div>
                    </div>
                `).join('');
            }

            async function addToCart(productId) {
                const qty = document.getElementById(`qty_${productId}`).value;
                const userId = prompt("Enter User ID (default: user1)", currentUserId) || currentUserId;
                currentUserId = userId;
                const res = await fetch(API_URL + '/cart/add', {
                    method: 'POST',
                    headers: {'Content-Type':'application/x-www-form-urlencoded'},
                    body: `userId=${userId}&productId=${productId}&quantity=${qty}`
                });
                if (res.ok) {
                    showToast(`Added ${qty} item(s) to cart`);
                    loadCart();
                } else showToast('Failed to add', 'error');
            }

            async function loadCart() {
                const userId = prompt("Enter User ID to view cart", currentUserId) || currentUserId;
                currentUserId = userId;
                const res = await fetch(API_URL + `/cart?userId=${userId}`);
                if (!res.ok) { document.getElementById('cartList').innerHTML = 'Cart empty'; return; }
                const items = await res.json();
                if (items.length === 0) { document.getElementById('cartList').innerHTML = 'Cart is empty'; document.getElementById('cartTotal').innerHTML = ''; return; }
                let total = 0;
                let html = '';
                for (let item of items) {
                    total += item.price * item.quantity;
                    html += `<div class="cart-item flex-between"><span>${item.name} x ${item.quantity}</span><span>₹${(item.price*item.quantity/100).toFixed(2)}</span><button onclick="removeFromCart('${item.productId}')">❌</button></div>`;
                }
                document.getElementById('cartList').innerHTML = html;
                document.getElementById('cartTotal').innerHTML = `Total: ₹${(total/100).toFixed(2)}`;
            }

            async function removeFromCart(productId) {
                const res = await fetch(API_URL + '/cart/remove', {
                    method: 'POST',
                    headers: {'Content-Type':'application/x-www-form-urlencoded'},
                    body: `userId=${currentUserId}&productId=${productId}`
                });
                if (res.ok) { loadCart(); showToast('Removed from cart'); }
                else showToast('Remove failed', 'error');
            }

            async function checkout() {
                const userId = document.getElementById('checkoutUserId').value.trim();
                if (!userId) { showToast('User ID required', 'error'); return; }
                const res = await fetch(API_URL + '/checkout', {
                    method: 'POST',
                    headers: {'Content-Type':'application/x-www-form-urlencoded'},
                    body: `userId=${userId}`
                });
                const order = await res.json();
                if (order.status === 'CONFIRMED') {
                    showToast(`Order placed! Total: ₹${(order.totalAmount/100).toFixed(2)}`);
                    loadCart();
                    if (document.getElementById('orderUserId').value === userId) loadOrders();
                } else {
                    showToast(`Checkout failed: ${order.status}`, 'error');
                }
            }

            async function loadOrders() {
                const userId = document.getElementById('orderUserId').value.trim();
                if (!userId) { showToast('User ID required', 'error'); return; }
                const res = await fetch(API_URL + `/orders?userId=${userId}`);
                const orders = await res.json();
                const container = document.getElementById('ordersList');
                if (orders.length === 0) { container.innerHTML = 'No orders yet'; return; }
                container.innerHTML = orders.map(o => `
                    <div class="order-item">
                        <div class="flex-between"><strong>Order #${o.id.slice(0,8)}</strong> <span>${o.formattedDate}</span></div>
                        <div>${o.items.map(i => `${i.productName} x ${i.quantity}`).join(', ')}</div>
                        <div><strong>Total: ₹${(o.totalAmount/100).toFixed(2)}</strong> | Status: ${o.status}</div>
                    </div>
                `).join('');
            }

            loadProducts();
        </script>
        </body>
        </html>
        """;
    }

    // ---------- Server Lifecycle ----------
    public static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        assignedPort = server.getAddress().getPort();
        server.createContext("/", new EcommerceHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║          E‑COMMERCE SYSTEM - SERVER STARTED              ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║  🌐 Server: http://localhost:" + assignedPort + "                          ║");
        System.out.println("║  🛒 Features: Products, Cart, Checkout, Orders           ║");
        System.out.println("║  🔒 Thread‑safe inventory using AtomicInteger & CAS     ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    public static void stopServer() {
        if (server != null) server.stop(0);
    }

    public static void main(String[] args) throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(EcommerceApp::stopServer));
        startServer();
    }
}