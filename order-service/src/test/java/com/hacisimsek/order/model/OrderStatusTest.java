package com.hacisimsek.order.model;

import com.hacisimsek.order.model.Order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void movesForwardThroughTheSaga() {
        assertThat(OrderStatus.INVENTORY_CHECKING.canTransitionTo(OrderStatus.INVENTORY_RESERVED)).isTrue();
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.PAYMENT_COMPLETED)).isTrue();
        assertThat(OrderStatus.PAYMENT_COMPLETED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    void ignoresLateEventsThatWouldMoveBackwards() {
        // PaymentProcessed consumed before InventoryReserved (different topics)
        assertThat(OrderStatus.PAYMENT_COMPLETED.canTransitionTo(OrderStatus.INVENTORY_RESERVED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PAYMENT_COMPLETED)).isFalse();
    }

    @Test
    void canFailFromAnyInFlightState() {
        assertThat(OrderStatus.INVENTORY_CHECKING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.FAILED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED", "FAILED"})
    void terminalStatesAreFinal(OrderStatus terminal) {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(terminal.canTransitionTo(next)).as("%s -> %s", terminal, next).isFalse();
        }
    }
}
