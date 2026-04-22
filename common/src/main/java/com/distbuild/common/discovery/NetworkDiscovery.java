package com.distbuild.common.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Network-based device discovery for distributed build system
 * Coordinator broadcasts availability, workers discover automatically
 */
public class NetworkDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(NetworkDiscovery.class);
    
    private static final String DISCOVERY_MESSAGE = "DISTBUILD_COORDINATOR";
    private static final int DISCOVERY_PORT = 8081;
    private static final int BROADCAST_INTERVAL = 5000; // 5 seconds
    private static final int DISCOVERY_TIMEOUT = 30000; // 30 seconds
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int coordinatorPort;
    
    public NetworkDiscovery(int coordinatorPort) {
        this.coordinatorPort = coordinatorPort;
    }
    
    /**
     * Start broadcasting coordinator availability
     */
    public CompletableFuture<Void> startBroadcasting() {
        return CompletableFuture.runAsync(() -> {
            running.set(true);
            logger.info("Starting coordinator discovery broadcast on port {}", DISCOVERY_PORT);
            
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                
                while (running.get()) {
                    try {
                        String message = DISCOVERY_MESSAGE + ":" + coordinatorPort;
                        byte[] buffer = message.getBytes();
                        
                        // Broadcast to all network interfaces
                        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface networkInterface = interfaces.nextElement();
                            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                                continue;
                            }
                            
                            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                                InetAddress broadcast = interfaceAddress.getBroadcast();
                                if (broadcast != null) {
                                    DatagramPacket packet = new DatagramPacket(
                                        buffer, buffer.length, broadcast, DISCOVERY_PORT
                                    );
                                    socket.send(packet);
                                    logger.debug("Broadcast discovery message to {}", broadcast);
                                }
                            }
                        }
                        
                        Thread.sleep(BROADCAST_INTERVAL);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("Error broadcasting discovery message", e);
                    }
                }
                
            } catch (SocketException e) {
                logger.error("Error creating broadcast socket", e);
            }
            
            logger.info("Coordinator discovery broadcast stopped");
        });
    }
    
    /**
     * Stop broadcasting
     */
    public void stopBroadcasting() {
        running.set(false);
    }
    
    /**
     * Discover coordinator on network
     */
    public static CompletableFuture<CoordinatorInfo> discoverCoordinator() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting coordinator discovery...");
            
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                socket.setSoTimeout(DISCOVERY_TIMEOUT);
                socket.setReuseAddress(true);
                
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                
                long startTime = System.currentTimeMillis();
                
                while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                    try {
                        socket.receive(packet);
                        
                        String message = new String(packet.getData(), 0, packet.getLength()).trim();
                        logger.debug("Received discovery message: {}", message);
                        
                        if (message.startsWith(DISCOVERY_MESSAGE)) {
                            String[] parts = message.split(":");
                            if (parts.length == 2) {
                                int port = Integer.parseInt(parts[1]);
                                InetAddress address = packet.getAddress();
                                
                                CoordinatorInfo info = new CoordinatorInfo(
                                    address.getHostAddress(), 
                                    port
                                );
                                
                                logger.info("Discovered coordinator at {}:{}", address.getHostAddress(), port);
                                return info;
                            }
                        }
                        
                    } catch (SocketTimeoutException e) {
                        // Timeout is expected, continue listening
                    } catch (Exception e) {
                        logger.error("Error receiving discovery message", e);
                    }
                }
                
                logger.warn("No coordinator discovered within timeout");
                return null;
                
            } catch (SocketException e) {
                logger.error("Error creating discovery socket", e);
                return null;
            }
        });
    }
    
    /**
     * Coordinator information
     */
    public static class CoordinatorInfo {
        private final String host;
        private final int port;
        
        public CoordinatorInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
        
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
