package com.memmcol.hes.controller;

import com.memmcol.hes.netty.NettyChannelInitializer;
import com.memmcol.hes.netty.NettyServerHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;


@RestController
@RequestMapping("/api/netty")
public class NettyStatusController {

    @Value("${nettyserver.port}")
    private int port;

    private final NettyServerHolder holder;
    private final NettyChannelInitializer initializer;

    public NettyStatusController(NettyServerHolder holder, NettyChannelInitializer initializer) {
        this.holder = holder;
        this.initializer = initializer;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", holder.isRunning() ? "RUNNING" : "STOPPED",
                "connectedMeters", holder.getActiveMeterCount()
        ));
    }

    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdown() {
        holder.shutdown();
        return ResponseEntity.ok("Netty server shut down");
    }

    @PostMapping("/restart")
    public ResponseEntity<String> restart() {
        holder.restart(port, initializer);
        return ResponseEntity.ok("Netty server restarted");
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(Map.of(
                "connectedMeters", holder.getActiveMeterCount(),
                "meterSerials", holder.getActiveMeters()
        ));
    }
}
