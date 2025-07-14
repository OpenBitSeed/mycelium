package com.mazepeng.mycelium.model;

import com.mazepeng.mycelium.RoutingTable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * 代表DHT网络中的一个节点。
 *
 */
@Getter
public final class Node {

    private final byte[] nodeId;
    private final InetAddress address; // 使用 InetAddress 存储 IP
    private final int port;
    private final long lastSeen;

    // 使用紧凑构造函数进行参数校验
    public Node(byte[] nodeId, String ip, int port, long lastSeen) {
        // ... id 校验 ...
        this.nodeId = nodeId;
        try {
            this.address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address or hostname: " + ip, e);
        }
        this.port = port;
        this.lastSeen = lastSeen;
    }

    private Node(byte[] nodeId, InetAddress address, int port, long lastSeen) {
        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.lastSeen = lastSeen;
    }

    /**
     * 创建一个更新了 lastSeen 时间戳的新 Node 实例。
     * @return 一个新的、带有当前时间戳的 Node 实例。
     */
    public Node touch() {
        return new Node(this.nodeId, this.address, this.port, System.currentTimeMillis());
    }

    // record 对数组默认使用引用相等，我们必须重写它以比较内容。
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Arrays.equals(nodeId, node.nodeId);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nodeId);
    }

    @Override
    public String toString() {
        String hexId = new BigInteger(1, this.nodeId).toString(16);
        return String.format("Node{id=%s, addr='%s:%d'}", hexId, address.getHostAddress(), port);
    }
}