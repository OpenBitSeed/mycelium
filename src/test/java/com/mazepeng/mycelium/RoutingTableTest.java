package com.mazepeng.mycelium;

import com.mazepeng.mycelium.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingTableTest {

    private static final SecureRandom random = new SecureRandom();
    private static final int K = 8; // 使用一个标准的 k 值

    private Node selfNode;
    private RoutingTable routingTable;

    // 在每个测试方法运行前执行，确保测试环境是干净的
    @BeforeEach
    void setUp() {
        selfNode = createRandomNode();
        routingTable = new RoutingTable(selfNode, K);
    }

    @Test
    @DisplayName("Test adding a new node to an empty bucket")
    void testAddNode_toEmptyBucket_shouldSucceed() {
        Node newNode = createRandomNode();
        routingTable.addNode(newNode);

        // 验证节点被添加
        assertTrue(routingTable.getAllNodes().contains(newNode), "New node should be in the routing table");
        assertEquals(1, routingTable.getAllNodes().size(), "Routing table should contain exactly one node");
    }

    @Test
    @DisplayName("Test adding the self node should be ignored")
    void testAddNode_selfNode_shouldBeIgnored() {
        routingTable.addNode(selfNode);
        assertTrue(routingTable.getAllNodes().isEmpty(), "Routing table should remain empty after adding self-node");
    }

    @Test
    @DisplayName("Test adding an existing node should update its lastSeen time")
    void testAddNode_existingNode_shouldUpdate() throws InterruptedException {
        Node existingNode = createRandomNode();
        routingTable.addNode(existingNode);

        long firstSeen = getNodeFromTable(existingNode).lastSeen();
        Thread.sleep(10); // 等待一小段时间以确保时间戳不同

        routingTable.addNode(existingNode); // 再次添加同一个节点
        long secondSeen = getNodeFromTable(existingNode).lastSeen();

        assertEquals(1, routingTable.getAllNodes().size(), "Should still only be one node in the table");
        assertTrue(secondSeen > firstSeen, "lastSeen timestamp should have been updated");
    }

    @Test
    @DisplayName("Test adding a node to a full bucket should be ignored")
    void testAddNode_toFullBucket_shouldBeIgnored() {
        // 找到一个特定的桶，并填满它
        int bucketIndex = 10;
        // 创建 K 个节点，它们的ID经过精心设计，都落在同一个桶里
        for (int i = 0; i < K; i++) {
            Node node = createNodeForBucket(selfNode.nodeId(), bucketIndex);
            routingTable.addNode(node);
        }
        assertEquals(K, routingTable.getAllNodes().size(), "Bucket should be full with K nodes");

        // 创建第 K+1 个节点，它也属于这个桶
        Node extraNode = createNodeForBucket(selfNode.nodeId(), bucketIndex);
        routingTable.addNode(extraNode);

        // 验证新节点被忽略
        assertEquals(K, routingTable.getAllNodes().size(), "Table size should not change when bucket is full");
        assertFalse(routingTable.getAllNodes().contains(extraNode), "The extra node should have been ignored");
    }

    @Test
    @DisplayName("Test nodeResponded should update a node")
    void testNodeResponded_shouldUpdateLastSeen() throws InterruptedException {
        Node node = createRandomNode();
        routingTable.addNode(node);

        long firstSeen = getNodeFromTable(node).lastSeen();
        Thread.sleep(10);

        routingTable.nodeResponded(node);
        long secondSeen = getNodeFromTable(node).lastSeen();

        assertTrue(secondSeen > firstSeen, "nodeResponded should update the lastSeen timestamp");
    }

    @Test
    @DisplayName("Test nodeTimedOut should remove a node")
    void testNodeTimedOut_shouldRemoveNode() {
        Node node = createRandomNode();
        routingTable.addNode(node);

        assertTrue(routingTable.getAllNodes().contains(node), "Node should exist before timeout");

        routingTable.nodeTimedOut(node);

        assertFalse(routingTable.getAllNodes().contains(node), "Node should be removed after timeout");
    }

    @Test
    @DisplayName("Test findClosestNodes should return correct nodes sorted by distance")
    void testFindClosestNodes_shouldReturnSortedNodes() {
        // 添加 20 个随机节点
        for (int i = 0; i < 20; i++) {
            routingTable.addNode(createRandomNode());
        }

        byte[] targetId = generateRandomId();
        List<Node> closestNodes = routingTable.findClosestNodes(targetId, 5);

        assertEquals(5, closestNodes.size(), "Should return the requested number of nodes");

        // 验证返回的列表是按距离升序排列的
        BigInteger previousDistance = BigInteger.valueOf(-1);
        for (Node node : closestNodes) {
            BigInteger currentDistance = RoutingTable.getDistance(targetId, node.nodeId());
            assertTrue(currentDistance.compareTo(previousDistance) >= 0, "Nodes should be sorted by distance");
            previousDistance = currentDistance;
        }
    }

    @Test
    @DisplayName("Test getNodesForHealthCheck should return only inactive nodes")
    void testGetNodesForHealthCheck() throws InterruptedException {
        long inactivityThreshold = 50; // 50ms

        // 添加一个“旧”节点
        Node oldNode = new Node(generateRandomId(), new InetSocketAddress("1.1.1.1", 1111), System.currentTimeMillis() - 100);
        routingTable.addNode(oldNode);

        // 添加一个“新”节点
        Node newNode = new Node(generateRandomId(), new InetSocketAddress("2.2.2.2", 2222), System.currentTimeMillis());
        routingTable.addNode(newNode);

        // 在阈值生效前等待
        Thread.sleep(10);

        List<Node> nodesToCheck = routingTable.getNodesForHealthCheck(inactivityThreshold);

        assertEquals(1, nodesToCheck.size(), "Should only find one node for health check");
        assertTrue(nodesToCheck.contains(oldNode), "The old node should be in the check list");
        assertFalse(nodesToCheck.contains(newNode), "The new node should not be in the check list");
    }

    // --- Helper Methods ---

    private byte[] generateRandomId() {
        byte[] id = new byte[RoutingTable.ID_LENGTH_BYTES];
        random.nextBytes(id);
        return id;
    }

    private Node createRandomNode() {
        return new Node(generateRandomId(), new InetSocketAddress("127.0.0.1", 1234), System.currentTimeMillis());
    }

    /**
     * 创建一个节点，其ID保证它会落在相对于 baseId 的指定 bucketIndex 中。
     */
    private Node createNodeForBucket(byte[] baseId, int bucketIndex) {
        BigInteger base = new BigInteger(1, baseId);
        // 创建一个在 2^bucketIndex 和 2^(bucketIndex+1) 之间的随机数
        BigInteger offset = BigInteger.ONE.shiftLeft(bucketIndex);
        BigInteger randomPart = new BigInteger(bucketIndex, random); // 确保随机部分不会让距离变得更大

        BigInteger distance = offset.add(randomPart);
        BigInteger targetId = base.xor(distance);

        byte[] targetIdBytes = targetId.toByteArray();
        byte[] finalIdBytes = new byte[RoutingTable.ID_LENGTH_BYTES];

        // 确保ID是20字节长
        System.arraycopy(targetIdBytes, 0, finalIdBytes,
                finalIdBytes.length - targetIdBytes.length, targetIdBytes.length);

        return new Node(finalIdBytes, new InetSocketAddress("127.0.0.1", 5678), System.currentTimeMillis());
    }

    /**
     * 从路由表中获取一个节点的实例，用于检查其内部状态。
     */
    private Node getNodeFromTable(Node nodeToFind) {
        return routingTable.getAllNodes()
                .stream()
                .filter(n -> n.equals(nodeToFind))
                .findFirst()
                .orElse(null);
    }
}