package com.mazepeng.mycelium.model;

import com.mazepeng.mycelium.RoutingTable;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * 代表DHT网络中的一个节点。
 * 使用Java 21的 record 定义，它是一个不可变的数据载体。
 *
 * @param nodeId   节点的20字节ID
 * @param ip       节点的IP地址
 * @param port     节点的端口
 * @param lastSeen 最后一次见到该节点的时间戳
 */
public record Node(byte[] nodeId, String ip, int port, long lastSeen) {

    // 使用紧凑构造函数进行参数校验
    public Node {
        if (nodeId == null || nodeId.length != RoutingTable.ID_LENGTH_BYTES) {
            throw new IllegalArgumentException("节点ID必须是 " + RoutingTable.ID_LENGTH_BYTES + " 字节");
        }
    }

    /**
     * 创建一个更新了 lastSeen 时间戳的新 Node 实例。
     * @return 一个新的、带有当前时间戳的 Node 实例。
     */
    public Node touch() {
        return new Node(this.nodeId, this.ip, this.port, System.currentTimeMillis());
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
        return String.format("Node{id=%s, addr='%s:%d'}", hexId, ip, port);
    }
}