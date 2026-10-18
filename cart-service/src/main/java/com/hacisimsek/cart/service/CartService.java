package com.hacisimsek.cart.service;

import com.hacisimsek.cart.client.OrderClient;
import com.hacisimsek.cart.config.CartProperties;
import com.hacisimsek.cart.dto.CartResponse;
import com.hacisimsek.cart.dto.CheckoutResponse;
import com.hacisimsek.cart.model.Cart;
import com.hacisimsek.cart.model.CartItem;
import com.hacisimsek.cart.repository.CartRepository;
import com.hacisimsek.common.client.catalog.CatalogClient;
import com.hacisimsek.common.client.catalog.CatalogProduct;
import com.hacisimsek.common.web.BusinessRuleException;
import com.hacisimsek.common.web.DependencyUnavailableException;
import com.hacisimsek.common.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cart operations. The customer id comes from the URL until authentication arrives; it will then
 * come from the access token instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CatalogClient catalogClient;
    private final OrderClient orderClient;
    private final CartProperties properties;

    /** Returns the cart with current catalog prices, or the last known prices if the catalog is down. */
    public CartResponse getCart(UUID customerId) {
        Cart cart = cartRepository.find(customerId).orElseGet(() -> Cart.empty(customerId));
        if (cart.items().isEmpty()) {
            return toResponse(cart, Map.of(), false);
        }
        try {
            Map<UUID, CatalogProduct> products = catalogClient.findProducts(productIds(cart));
            Cart refreshed = refresh(cart, products);
            if (!refreshed.items().equals(cart.items())) {
                cartRepository.save(refreshed);
            }
            return toResponse(refreshed, products, false);
        } catch (DependencyUnavailableException e) {
            log.warn("Showing cached prices for cart {}: {}", customerId, e.getMessage());
            return toResponse(cart, null, true);
        }
    }

    public CartResponse addItem(UUID customerId, UUID productId, int quantity) {
        CatalogProduct product = Optional.ofNullable(catalogClient.findProducts(List.of(productId)).get(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (!product.active()) {
            throw new BusinessRuleException("Product is no longer available: " + product.name());
        }
        Cart cart = cartRepository.find(customerId).orElseGet(() -> Cart.empty(customerId));
        int newQuantity = cart.item(productId).map(CartItem::quantity).orElse(0) + quantity;
        checkQuantity(newQuantity);

        Cart updated = cartRepository.save(cart.withItem(new CartItem(product.id(), product.sku(), product.name(),
                product.price(), product.imageUrl(), newQuantity)));
        return toResponse(updated, Map.of(productId, product), false);
    }

    public CartResponse updateQuantity(UUID customerId, UUID productId, int quantity) {
        Cart cart = cartRepository.find(customerId).orElseGet(() -> Cart.empty(customerId));
        CartItem item = cart.item(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", productId));
        checkQuantity(quantity);
        cartRepository.save(cart.withItem(item.withQuantity(quantity)));
        return getCart(customerId);
    }

    public CartResponse removeItem(UUID customerId, UUID productId) {
        cartRepository.find(customerId).ifPresent(cart -> cartRepository.save(cart.withoutItem(productId)));
        return getCart(customerId);
    }

    public void clear(UUID customerId) {
        cartRepository.delete(customerId);
    }

    /**
     * Turns the cart into an order. Prices are not sent: order-service takes them from the catalog.
     * The cart revision is the idempotency key, so if the response is lost and the client retries,
     * the same order comes back. The cart is emptied only after the order exists.
     */
    public CheckoutResponse checkout(UUID customerId) {
        Cart cart = cartRepository.find(customerId)
                .filter(c -> !c.items().isEmpty())
                .orElseThrow(() -> new BusinessRuleException("Cart is empty"));

        List<OrderClient.OrderLine> lines = cart.items().stream()
                .map(i -> new OrderClient.OrderLine(i.productId(), i.quantity()))
                .toList();
        CheckoutResponse order = orderClient.placeOrder(customerId, lines, "cart-" + cart.revision());

        cartRepository.delete(customerId);
        log.info("Checked out cart of customer {} as order {}", customerId, order.orderId());
        return order;
    }

    private void checkQuantity(int quantity) {
        if (quantity > properties.maxQuantityPerItem()) {
            throw new BusinessRuleException("At most " + properties.maxQuantityPerItem() + " units per product");
        }
    }

    /** Brings names and prices up to date; products the catalog no longer knows are dropped. */
    private static Cart refresh(Cart cart, Map<UUID, CatalogProduct> products) {
        List<CartItem> items = cart.items().stream()
                .filter(i -> products.containsKey(i.productId()))
                .map(i -> {
                    CatalogProduct p = products.get(i.productId());
                    return new CartItem(p.id(), p.sku(), p.name(), p.price(), p.imageUrl(), i.quantity());
                })
                .toList();
        return items.equals(cart.items()) ? cart : new Cart(cart.customerId(), items, cart.revision(), cart.updatedAt());
    }

    private static List<UUID> productIds(Cart cart) {
        return cart.items().stream().map(CartItem::productId).toList();
    }

    /** @param products current catalog data, or null when it is unknown (catalog down) */
    private static CartResponse toResponse(Cart cart, Map<UUID, CatalogProduct> products, boolean pricesStale) {
        List<CartResponse.Line> lines = cart.items().stream()
                .map(i -> new CartResponse.Line(i.productId(), i.sku(), i.name(), i.unitPrice(), i.imageUrl(),
                        i.quantity(), i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())),
                        products == null || products.get(i.productId()) == null || products.get(i.productId()).active()))
                .toList();
        BigDecimal subtotal = lines.stream().map(CartResponse.Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = lines.stream().mapToInt(CartResponse.Line::quantity).sum();
        return new CartResponse(cart.customerId(), lines, itemCount, subtotal, pricesStale, cart.updatedAt());
    }
}
