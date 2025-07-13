package com.mazepeng.mycelium;

import com.mazepeng.mycelium.model.Node;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class RoutingTablePersistence {

    // 每个节点记录的固定长度 (20字节ID + 4字节IPv4 + 2字节端口)
    private static final int NODE_RECORD_LENGTH = 26;

    private final File storageFile;

    public RoutingTablePersistence(File storageFile) {
        this.storageFile = storageFile;
    }

    /**
     * 将路由表的状态以紧凑的二进制格式保存到文件。
     * @param table 要保存的路由表。
     */
    public void save(RoutingTable table) {
        List<Node> nodes = table.getAllNodes();
        if (nodes.isEmpty()) {
            log.info("Routing table is empty, nothing to save.");
            // 如果文件存在，清空它
            if (storageFile.exists()) {
                storageFile.delete();
            }
            return;
        }

        // 使用 DataOutputStream 来方便地写入二进制数据
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(storageFile)))) {
            for (Node node : nodes) {
                // 1. 写入 20 字节的 Node ID
                dos.write(node.nodeId());

                // 2. 写入 4 字节的 IP 地址
                byte[] ipBytes = InetAddress.getByName(node.ip()).getAddress();
                if (ipBytes.length != 4) {
                    // 只处理 IPv4，跳过其他格式
                    log.warn("Skipping non-IPv4 node during save: {}", node);
                    continue;
                }
                dos.write(ipBytes);

                // 3. 写入 2 字节的端口
                dos.writeShort(node.port());
            }
            dos.flush();
            log.info("Successfully saved {} nodes to {}", nodes.size(), storageFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save routing table to file: {}", storageFile.getAbsolutePath(), e);
        }
    }

    /**
     * 从二进制文件中加载节点列表。
     * @return 加载的节点列表。
     */
    public List<Node> load() {
        if (!storageFile.exists() || storageFile.length() == 0) {
            log.info("Storage file not found or is empty. Starting with a fresh routing table.");
            return Collections.emptyList();
        }

        // 验证文件大小是否为记录长度的整数倍
        if (storageFile.length() % NODE_RECORD_LENGTH != 0) {
            log.error("Storage file is corrupted (invalid length: {}). Starting fresh.", storageFile.length());
            return Collections.emptyList();
        }

        List<Node> loadedNodes = new ArrayList<>();
        // 使用 DataInputStream 来方便地读取二进制数据
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(storageFile)))) {
            while (dis.available() > 0) {
                // 1. 读取 20 字节的 Node ID
                byte[] nodeId = dis.readNBytes(NODE_RECORD_LENGTH - 6); // Node ID is 20 bytes

                // 2. 读取 4 字节的 IP 地址
                byte[] ipBytes = dis.readNBytes(4);
                String ip = InetAddress.getByAddress(ipBytes).getHostAddress();

                // 3. 读取 2 字节的端口
                int port = dis.readUnsignedShort();

                // 创建 Node 对象 (lastSeen 设为当前时间，因为我们不知道上次的时间)
                loadedNodes.add(new Node(nodeId, ip, port, System.currentTimeMillis()));
            }
            log.info("Successfully loaded {} nodes from {}", loadedNodes.size(), storageFile.getAbsolutePath());
            return loadedNodes;
        } catch (IOException e) {
            log.error("Failed to load or parse routing table from file: {}. Starting fresh.", storageFile.getAbsolutePath(), e);
            return Collections.emptyList();
        }
    }
}