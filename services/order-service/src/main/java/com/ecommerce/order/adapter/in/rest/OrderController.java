package com.ecommerce.order.adapter.in.rest;

import com.ecommerce.order.adapter.in.rest.dto.CancelOrderRequest;
import com.ecommerce.order.adapter.in.rest.dto.CreateOrderRequest;
import com.ecommerce.order.adapter.in.rest.dto.OrderResponse;
import com.ecommerce.order.application.command.CreateOrderCommand;
import com.ecommerce.order.application.service.CancelOrderUseCase;
import com.ecommerce.order.application.service.CreateOrderUseCase;
import com.ecommerce.order.application.service.OrderQueryService;
import com.ecommerce.order.domain.model.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order lifecycle: create, view, cancel")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final CancelOrderUseCase cancelOrder;
    private final OrderQueryService queries;

    public OrderController(CreateOrderUseCase createOrder, CancelOrderUseCase cancelOrder,
                           OrderQueryService queries) {
        this.createOrder = createOrder;
        this.cancelOrder = cancelOrder;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Create an order", description = "Persists the order and publishes OrderCreated, starting the saga")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @RequestHeader(value = "X-User-Id", required = false) String userId) {
        List<CreateOrderCommand.NewOrderItem> items = request.items().stream()
                .map(i -> new CreateOrderCommand.NewOrderItem(
                        i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        // when present, the gateway-validated identity always wins over the body
        String customerId = userId != null ? userId : request.customerId();
        Order order = createOrder.handle(
                new CreateOrderCommand(customerId, request.currency(), items));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id")
    public OrderResponse get(@PathVariable String id,
                             @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return OrderResponse.from(queries.getById(id, userId));
    }

    @GetMapping
    @Operation(summary = "List orders, optionally filtered by customer")
    public List<OrderResponse> list(@RequestParam(required = false) String customerId,
                                    @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String filter = userId != null ? userId : customerId;
        return queries.list(filter).stream().map(OrderResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order",
            description = "Only legal before payment completes; afterwards returns 409")
    public OrderResponse cancel(@PathVariable String id,
                                @Valid @RequestBody(required = false) CancelOrderRequest request,
                                @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String reason = request == null ? "customer request" : request.reasonOrDefault();
        return OrderResponse.from(cancelOrder.handle(id, reason, userId));
    }
}
