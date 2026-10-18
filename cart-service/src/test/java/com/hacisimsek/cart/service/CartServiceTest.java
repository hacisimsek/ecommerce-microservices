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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID BOOK = UUID.randomUUID();

    private final CartRepository repository = mock(CartRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final OrderClient orders = mock(OrderClient.class);
    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(repository, catalog, orders, new CartProperties(Duration.ofDays(30), 5));
        when(repository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.find(CUSTOMER)).thenReturn(Optional.empty());
        catalogReturns(book(new BigDecimal("45.00"), true));
    }

    @Test
    void addingTheSameProductTwiceIncreasesTheQuantity() {
        when(repository.find(CUSTOMER)).thenReturn(Optional.of(cartWith(2, "45.00")));

        CartResponse cart = service.addItem(CUSTOMER, BOOK, 1);

        assertThat(cart.items()).singleElement().satisfies(l -> assertThat(l.quantity()).isEqualTo(3));
        assertThat(cart.subtotal()).isEqualByComparingTo("135.00");
    }

    @Test
    void enforcesTheQuantityLimit() {
        when(repository.find(CUSTOMER)).thenReturn(Optional.of(cartWith(4, "45.00")));

        assertThatThrownBy(() -> service.addItem(CUSTOMER, BOOK, 2)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesInactiveProducts() {
        catalogReturns(book(new BigDecimal("45.00"), false));

        assertThatThrownBy(() -> service.addItem(CUSTOMER, BOOK, 1)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void showsCurrentCatalogPrices() {
        when(repository.find(CUSTOMER)).thenReturn(Optional.of(cartWith(2, "45.00")));
        catalogReturns(book(new BigDecimal("40.00"), true));

        CartResponse cart = service.getCart(CUSTOMER);

        assertThat(cart.subtotal()).isEqualByComparingTo("80.00");
        assertThat(cart.pricesStale()).isFalse();
    }

    @Test
    void fallsBackToLastKnownPricesWhenTheCatalogIsDown() {
        when(repository.find(CUSTOMER)).thenReturn(Optional.of(cartWith(2, "45.00")));
        when(catalog.findProducts(anyCollection()))
                .thenThrow(new DependencyUnavailableException("catalog-service", new RuntimeException()));

        CartResponse cart = service.getCart(CUSTOMER);

        assertThat(cart.pricesStale()).isTrue();
        assertThat(cart.subtotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void checkoutUsesTheCartRevisionAsIdempotencyKeyAndEmptiesTheCart() {
        Cart cart = cartWith(2, "45.00");
        when(repository.find(CUSTOMER)).thenReturn(Optional.of(cart));
        when(orders.placeOrder(eq(CUSTOMER), anyList(), eq("cart-" + cart.revision())))
                .thenReturn(new CheckoutResponse(UUID.randomUUID(), "INVENTORY_CHECKING", new BigDecimal("90.00")));

        service.checkout(CUSTOMER);

        verify(orders).placeOrder(CUSTOMER, List.of(new OrderClient.OrderLine(BOOK, 2)), "cart-" + cart.revision());
        verify(repository).delete(CUSTOMER);
    }

    @Test
    void keepsTheCartWhenTheOrderCannotBePlaced() {
        when(repository.find(CUSTOMER)).thenReturn(Optional.of(cartWith(2, "45.00")));
        when(orders.placeOrder(any(), anyList(), any()))
                .thenThrow(new DependencyUnavailableException("order-service", new RuntimeException()));

        assertThatThrownBy(() -> service.checkout(CUSTOMER)).isInstanceOf(DependencyUnavailableException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void emptyCartCannotBeCheckedOut() {
        assertThatThrownBy(() -> service.checkout(CUSTOMER)).isInstanceOf(BusinessRuleException.class);
    }

    private void catalogReturns(CatalogProduct product) {
        when(catalog.findProducts(anyCollection())).thenReturn(Map.of(BOOK, product));
    }

    private static CatalogProduct book(BigDecimal price, boolean active) {
        return new CatalogProduct(BOOK, "BOOK-1", "Book", price, null, active);
    }

    private static Cart cartWith(int quantity, String price) {
        return new Cart(CUSTOMER, List.of(new CartItem(BOOK, "BOOK-1", "Book", new BigDecimal(price), null, quantity)),
                UUID.randomUUID(), Instant.now());
    }
}
