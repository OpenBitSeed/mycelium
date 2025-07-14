package com.mazepeng.mycelium.model;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * 代表DHT网络中的一个节点，支持IPv4和IPv6。
 * 使用 Java record 实现，确保其为不可变的数据载体。
 *
 * @param nodeId   节点的20字节ID
 * @param address  节点的网络地址 (IP 和 端口)
 * @param lastSeen 最后一次见到该节点的时间戳 (毫秒)
 */
public record Node(byte[] nodeId, InetSocketAddress address, long lastSeen) {

    // Record 的紧凑构造函数，用于参数校验
    public Node {
        if (nodeId == null || nodeId.length != 20) { // 假设ID长度为20
            throw new IllegalArgumentException("Node ID 必须是 20 字节");
        }
        if (address == null) {
            throw new IllegalArgumentException("Address 不能为空");
        }
    }

    /**
     * 工厂方法：通过 IP 地址字符串和端口创建 Node 实例。
     * 这个方法会处理 DNS 解析和地址创建，是创建新 Node 的推荐方式。
     *
     * @param nodeId 节点的 20 字节 ID
     * @param ip     IP 地址或主机名 (例如 "192.168.1.1" 或 "::1" 或 "example.com")
     * @param port   端口号
     * @return 一个新的 Node 实例
     */
    public static Node fromIpPort(byte[] nodeId, String ip, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("端口号必须在 0-65535 之间");
        }
        // InetSocketAddress 会处理 IPv4/v6 和 DNS 解析
        var socketAddress = new InetSocketAddress(ip, port);
        return new Node(nodeId, socketAddress, System.currentTimeMillis());
    }

    /**
     * 工厂方法：通过 InetAddress 和端口创建 Node 实例。
     */
    public static Node fromInetAddress(byte[] nodeId, InetAddress ip, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("端口号必须在 0-65535 之间");
        }
        var socketAddress = new InetSocketAddress(ip, port);
        return new Node(nodeId, socketAddress, System.currentTimeMillis());
    }


    /**
     * 创建一个更新了 lastSeen 时间戳的新 Node 实例。
     * 这是实现不可变对象状态更新的正确方式。
     *
     * @return 一个新的、带有当前时间戳的 Node 实例。
     */
    public Node touch() {
        return new Node(this.nodeId, this.address, System.currentTimeMillis());
    }

    /**
     * 获取 IP 地址对象。
     * @return InetAddress
     */
    public InetAddress getIp() {
        return this.address.getAddress();
    }

    /**
     * 获取端口号。
     * @return int
     */
    public int getPort() {
        return this.address.getPort();
    }


    // --- 重写 record 的默认行为 ---

    // record 对数组的 equals 和 hashCode 是基于引用的，我们必须重写它以比较内容。
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        // 节点ID相同即视为同一节点
        return Arrays.equals(nodeId, node.nodeId);
    }

    @Override
    public int hashCode() {
        // 哈希码应只基于节点ID
        return Arrays.hashCode(nodeId);
    }

    // 自定义 toString 以获得更美观的输出，并正确处理 IPv6 地址。
    @Override
    public String toString() {
        String hexId = new BigInteger(1, this.nodeId).toString(16);
        String hostAddress = this.address.getAddress().getHostAddress();

        // IPv6 地址标准表示法需要用方括号括起来
        boolean isIPv6 = this.address.getAddress() instanceof java.net.Inet6Address;
        if (isIPv6) {
            return String.format("Node{id=%s, addr='[%s]:%d'}", hexId, hostAddress, getPort());
        } else {
            return String.format("Node{id=%s, addr='%s:%d'}", hexId, hostAddress, getPort());
        }
    }
}