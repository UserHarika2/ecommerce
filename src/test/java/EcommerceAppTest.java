package com.ecommercetest;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.ecommerce.EcommerceApp;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EcommerceAppTest {

    @BeforeEach
    void setUp() {
        EcommerceApp.reset();
    }

    @Test
    void testAddToCart() {
        assertTrue(EcommerceApp.addToCart("user1", "p1", 2));
        Map<String, Integer> cart = EcommerceApp.getCart("user1");
        assertEquals(2, cart.get("p1"));
    }

    @Test
    void testRemoveFromCart() {
        EcommerceApp.addToCart("user1", "p1", 1);
        assertTrue(EcommerceApp.removeFromCart("user1", "p1"));
        assertTrue(EcommerceApp.getCart("user1").isEmpty());
    }

    @Test
    void testCheckoutSuccess() {
        EcommerceApp.addToCart("user1", "p1", 1);
        var order = EcommerceApp.checkout("user1");
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals(1, EcommerceApp.getOrderHistory("user1").size());
        assertEquals(9, EcommerceApp.getInventory("p1")); // initial 10 -> 9
    }

    @Test
    void testCheckoutOutOfStock() {
        EcommerceApp.addToCart("user1", "p1", 20); // only 10 available
        var order = EcommerceApp.checkout("user1");
        assertEquals("FAILED_OUT_OF_STOCK", order.getStatus());
        assertEquals(10, EcommerceApp.getInventory("p1")); // unchanged
    }

    @Test
    void testConcurrentCheckout() throws InterruptedException {
        // Simulate 5 users each trying to buy 2 laptops (total 10, inventory = 10)
        for (int i = 0; i < 5; i++) {
            EcommerceApp.addToCart("user" + i, "p1", 2);
        }
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                EcommerceApp.checkout(userId);
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();
        // Exactly 5 orders should succeed? Actually inventory 10, 5*2=10, all should succeed
        int successCount = 0;
        for (int i = 0; i < 5; i++) {
            var orders = EcommerceApp.getOrderHistory("user" + i);
            if (!orders.isEmpty() && orders.get(0).getStatus().equals("CONFIRMED")) successCount++;
        }
        assertEquals(5, successCount);
        assertEquals(0, EcommerceApp.getInventory("p1"));
    }
}