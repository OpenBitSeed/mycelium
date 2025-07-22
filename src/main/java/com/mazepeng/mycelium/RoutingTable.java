package com.mazepeng.mycelium;

import com.mazepeng.mycelium.model.Node;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 一个被动的、只负责数据存储和路由算法的 Kademlia 路由表。
 * 它不主动发起任何网络操作或回调。
 */
public class RoutingTable {
    public static final int ID_LENGTH_BITS = 160;
    public static final int ID_LENGTH_BYTES = 20;
    public static final int IPV4_LENGTH_BYTES = 4;
    public static final int PORT_LENGTH_BYTES = 2;

    private final Node selfNode;
    private final int k;
    private final KBucket[] buckets;

    public RoutingTable(Node selfNode, int k) {
        this.selfNode = selfNode;
        this.k = k;
        this.buckets = new KBucket[ID_LENGTH_BITS];
        for (int i = 0; i < ID_LENGTH_BITS; i++) {
            buckets[i] = new KBucket(k);
        }
    }
    public byte[] getSelfNodeId() {
        return selfNode.nodeId();
    }

    public List<Node> getRecentNodes(int limit) {
        return Stream.of(this.buckets)
                .flatMap(bucket -> bucket.getAllNodes().stream())
                .sorted(Comparator.comparingLong(Node::lastSeen).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }


    /**
     * 尝试添加一个节点。如果桶已满，新节点将被忽略。
     * @param node 要添加的节点。
     */
    public void addNode(Node node) {
        if (node.equals(selfNode)) return;

        int index = getBucketIndex(node.nodeId());
        KBucket bucket = buckets[index];

        // 如果节点已存在，更新它
        if (bucket.hasNode(node)) {
            bucket.addNode(node.touch());
            return;
        }

        // 如果桶未满，添加它
        if (!bucket.isFull()) {
            bucket.addNode(node);
        }
        // 如果桶已满，简单地忽略新节点。
        // 我们依赖 NodeHealthChecker 来清理死节点，为新节点腾出空间。
    }

    /**
     * 当外部检查器确认一个节点存活时，调用此方法。
     * @param node 响应了 PING 的节点。
     */
    public void nodeResponded(Node node) {
        int index = getBucketIndex(node.nodeId());
        if (index < 0) return;
        buckets[index].addNode(node.touch());
    }

    /**
     * 当外部检查器发现一个节点死亡时，调用此方法。
     * @param node 未响应 PING 的节点。
     */
    public void nodeTimedOut(Node node) {
        int index = getBucketIndex(node.nodeId());
        if (index < 0) return;
        buckets[index].removeNode(node);
    }

    /**
     * 获取路由表中所有最久未见的节点，供健康检查器使用。
     * @return 需要检查的节点列表。
     */
    public List<Node> getNodesForHealthCheck(long inactivityThresholdMillis) {
        final long now = System.currentTimeMillis();
        return Stream.of(this.buckets)
                .parallel()
                .flatMap(bucket -> {
                    List<Node> nodesInBucket = new ArrayList<>();
                    bucket.forEachUntil(node -> {
                        if ((now - node.lastSeen()) > inactivityThresholdMillis) {
                            nodesInBucket.add(node);
                            return true;
                        } else {
                            return false;
                        }
                    });
                    return nodesInBucket.stream();
                })
                .collect(Collectors.toList());
    }

    public List<Node> findClosestNodes(byte[] targetId, int count) {
        var distanceComparator = Comparator.comparing((Node n) -> getDistance(n.nodeId(), targetId));
        return Stream.of(this.buckets)
                .flatMap(bucket -> bucket.getAllNodes().stream())
                .sorted(distanceComparator)
                .limit(count)
                .toList();
    }

    public List<Node> findClosestNodes(byte[] targetId) {
        return findClosestNodes(targetId, this.k);
    }

    private int getBucketIndex(byte[] remoteNodeId) {
        var distance = getDistance(selfNode.nodeId(), remoteNodeId);
        if (distance.equals(BigInteger.ZERO)) return 0;
        int index = distance.bitLength() - 1;
        return Math.max(index, 0);
    }

    public static BigInteger getDistance(byte[] id1, byte[] id2) {
        return new BigInteger(1, id1).xor(new BigInteger(1, id2));
    }

    public List<Node> getAllNodes() {
        return Stream.of(this.buckets)
                .flatMap(bucket -> bucket.getAllNodes().stream())
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("RoutingTable for Node: ").append(selfNode).append("\n");
        long totalNodes = 0;
        for (int i = 0; i < ID_LENGTH_BITS; i++) {
            if (buckets[i].size() > 0) {
                sb.append(String.format("  Bucket %d: %d nodes%n", i, buckets[i].size()));
                totalNodes += buckets[i].size();
            }
        }
        sb.append("Total nodes in table: ").append(totalNodes);
        return sb.toString();
    }
}