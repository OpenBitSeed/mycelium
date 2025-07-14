# 📡 Mycelium RoutingTable

**Mycelium** 是一个轻量级、线程安全的 **Kademlia DHT 路由表实现**，专注于数据结构与本地持久化，不包含网络通信逻辑。适合用于构建去中心化网络、BT 客户端、分布式缓存、区块链等底层基础模块。

---

## ✨ 特性

* **IPv4 & IPv6 完整支持**：`Node` 以 Java record 实现，并且 `RoutingTablePersistence` 采用可变长编码同时序列化 IPv4/IPv6。
* 完整实现 Kademlia 路由算法（节点距离计算、K‑bucket 管理、最近节点查找等）。
* 被动型设计：**不负责网络通信**，调用者可自由集成 I/O 实现。
* 支持节点过期淘汰与健康检查辅助机制。
* 支持本地持久化（加载 / 保存路由表到文件）。
* 线程安全设计（桶内同步）。

---

## 🧱 模块结构

### `Node`

不可变数据载体，采用 Java 17+ `record`：

| 字段                          | 说明                         |
| --------------------------- | -------------------------- |
| `byte[] nodeId`             | 20 字节节点 ID（SHA‑1 大小）。      |
| `InetSocketAddress address` | IP（IPv4/IPv6 或主机名解析结果）与端口。 |
| `long lastSeen`             | 最后一次看到该节点的时间戳（毫秒）。         |

关键方法：

```java
// 工厂方法，自动解析 IPv4/IPv6/DNS
Node node = Node.fromIpPort(nodeId, "2606:4700:4700::1111", 6881);

// 不可变更新 lastSeen
afterPing = node.touch();
```

### `KBucket`

* 保存最多 **K** 个节点，遵循“最久未用优先淘汰”策略。
* `addNode` 若节点已存在则移动至队尾（最新）。
* 线程安全，使用桶级 `synchronized` 保护。

### `RoutingTable`

* 包含 **160** 个 `KBucket`（对应 160‑bit Node ID）。
* 使用 **XOR 距离** 进行桶定位与节点排序。
* 提供：

    * `addNode` 添加/更新节点。
    * `findClosestNodes` 按距离返回最近节点集合。
    * `getNodesForHealthCheck` 按闲置阈值提取需 PING 的节点。
    * `nodeResponded` / `nodeTimedOut` 配合外部健康检查器维护桶状态。

### `RoutingTablePersistence`

* **可变长二进制格式**，同一文件混存 IPv4 与 IPv6 节点。
* 写入：`Node ID → Address Type (1 B) → IP (4 B/16 B) → Port (2 B)`。
* 加载时根据地址类型自动判别并创建 `Node`。
* 全面支持双栈序列化/反序列化，冷启动更快。

---

## 🛠 快速上手

```java
byte[] myId = new byte[20];
Node self = Node.fromIpPort(myId, "::1", 6881); // IPv6 支持

RoutingTable table = new RoutingTable(self, 8);  // K = 8

Node remote = Node.fromIpPort(new byte[20], "192.0.2.5", 6881);
table.addNode(remote);

List<Node> closest = table.findClosestNodes(remote.nodeId(), 5);

// 持久化
RoutingTablePersistence persist = new RoutingTablePersistence(new File("routing_table.bin"));
persist.save(table);
```

---

## 💾 文件格式

| 偏移    | 大小         | 字段         | 说明                      |
| ----- | ---------- | ---------- | ----------------------- |
| 0     | 20 B       | Node ID    | 固定 20 字节。               |
| 20    | 1 B        | AddrType   | `4` = IPv4, `6` = IPv6。 |
| 21    | 4 B / 16 B | IP Address | 依据 AddrType。            |
| 25/37 | 2 B        | Port       | 无符号 short。              |

---

## 📦 依赖

* **Java 17+**
* **Lombok**（可选：`@Slf4j`等）
* **SLF4J**（日志门面）

---

## 📘 规划

* [ ] 可插拔持久化策略（如 SQLite、JSON）。

---

## 🔖 License

本项目采用 [MIT License](https://opensource.org/licenses/MIT) 许可协议，详见 LICENSE 文件。
