package com.mazepeng.mycelium;

import com.mazepeng.mycelium.model.Node;

import java.util.LinkedList;
import java.util.List;

public class KBucket {
    private final int k;
    private final LinkedList<Node> nodes;

    public KBucket(int k) {
        this.k = k;
        this.nodes = new LinkedList<>();
    }

    /**
     * 向桶中添加一个节点。此方法假定调用者已经检查过桶是否已满。
     * 如果节点已存在，它会被移动到队尾。
     * @param node 要添加的节点。
     */
    public void addNode(Node node) {
        synchronized (this.nodes) {
            // 如果已存在（根据ID判断），先移除旧的
            this.nodes.remove(node);
            // 总是添加到队尾，表示最新看到
            this.nodes.addLast(node);
        }
    }

    public boolean hasNode(Node node) {
        synchronized (this.nodes) {
            return this.nodes.contains(node);
        }
    }

    public boolean isFull() {
        return this.nodes.size() >= k;
    }

    public void removeNode(Node node) {
        synchronized (this.nodes) {
            nodes.remove(node);
        }
    }

    public Node getOldestNode() {
        synchronized (this.nodes) {
            // 使用Java 21 Sequenced Collections API
            return nodes.isEmpty() ? null : nodes.getFirst();
        }
    }

    public List<Node> getAllNodes() {
        synchronized (this.nodes) {
            return List.copyOf(nodes); // 返回一个不可变副本
        }
    }

    public int size() {
        return nodes.size();
    }
}