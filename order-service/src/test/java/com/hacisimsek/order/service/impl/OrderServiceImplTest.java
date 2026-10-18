package com.hacisimsek.order.service.impl;

import com.hacisimsek.common.client.catalog.CatalogClient;
import com.hacisimsek.common.client.catalog.CatalogProduct;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.common.web.BusinessRuleException;
import com.hacisimsek.order.dto.OrderCreation;
import com.hacisimsek.order.dto.OrderItemRequest;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID KEYBOARD = UUID.randomUUID();
    private static final UUID MUG = UUID.randomUUID();

    private final OrderRepository repository = mock(OrderRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(repository, publisher, mock(IdempotencyGuard.class), catalog, transactions);
        when(transactions.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(repository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(catalog.findProducts(anyCollection())).thenReturn(Map.of(
                KEYBOARD, new CatalogProduct(KEYBOARD, "KB-1", "Keyboard", new BigDecimal("119.90"), null, true),
                MUG, new CatalogProduct(MUG, "MUG-1", "Mug", new BigDecimal("10.00"), null, false)));
    }

    @Test
    void pricesTheOrderFromTheCatalogAndStartsTheSaga() {
        OrderCreation creation = service.createOrder(request(item(KEYBOARD, 2)), "key-1");

        assertThat(creation.replayed()).isFalse();
        assertThat(creation.order().getTotalAmount()).isEqualByComparingTo("239.80");
        assertThat(creation.order().getItems()).singleElement()
                .satisfies(i -> assertThat(i.getProductName()).isEqualTo("Keyboard"));
        verify(publisher).publish(eq("order-events"), anyString(), any(OrderCreatedEvent.class));
    }

    @Test
    void mergesRepeatedProductsIntoOneLine() {
        OrderCreation creation = service.createOrder(request(item(KEYBOARD, 1), item(KEYBOARD, 2)), null);

        assertThat(creation.order().getItems()).singleElement()
                .satisfies(i -> assertThat(i.getQuantity()).isEqualTo(3));
    }

    @Test
    void rejectsInactiveAndUnknownProducts() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.createOrder(request(item(MUG, 1), item(unknown, 1)), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MUG.toString())
                .hasMessageContaining(unknown.toString());
        verify(repository, never()).save(any());
    }

    @Test
    void returnsTheExistingOrderForARepeatedIdempotencyKey() {
        Order existing = Order.builder().id(UUID.randomUUID()).customerId(CUSTOMER)
                .status(Order.OrderStatus.SHIPPED).totalAmount(BigDecimal.TEN).items(new ArrayList<>()).build();
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        OrderCreation creation = service.createOrder(request(item(KEYBOARD, 2)), "key-1");

        assertThat(creation.replayed()).isTrue();
        assertThat(creation.order().getOrderId()).isEqualTo(existing.getId());
        verifyNoInteractions(catalog, publisher);
    }

    private static OrderRequest request(OrderItemRequest... items) {
        return new OrderRequest(CUSTOMER, List.of(items));
    }

    private static OrderItemRequest item(UUID productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }
}
