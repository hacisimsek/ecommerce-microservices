package com.hacisimsek.shipping.controller;

import com.hacisimsek.shipping.model.Shipment;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/{shipmentId}")
    public ResponseEntity<Shipment> getShipmentById(@PathVariable UUID shipmentId) {
        return ResponseEntity.ok(shippingService.getShipmentById(shipmentId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Shipment> getShipmentByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(shippingService.getShipmentByOrderId(orderId));
    }
}