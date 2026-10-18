package com.hacisimsek.cart.controller;

import com.hacisimsek.cart.dto.AddItemRequest;
import com.hacisimsek.cart.dto.CartResponse;
import com.hacisimsek.cart.dto.CheckoutResponse;
import com.hacisimsek.cart.dto.UpdateQuantityRequest;
import com.hacisimsek.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/carts/{customerId}")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@PathVariable UUID customerId) {
        return cartService.getCart(customerId);
    }

    @PostMapping("/items")
    public CartResponse addItem(@PathVariable UUID customerId, @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(customerId, request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateQuantity(@PathVariable UUID customerId, @PathVariable UUID productId,
                                       @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateQuantity(customerId, productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable UUID customerId, @PathVariable UUID productId) {
        return cartService.removeItem(customerId, productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable UUID customerId) {
        cartService.clear(customerId);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@PathVariable UUID customerId) {
        return cartService.checkout(customerId);
    }
}
