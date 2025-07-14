package com.mazepeng.mycelium;

import com.mazepeng.mycelium.model.Node; // 假设 Node 已经使用了 InetSocketAddress
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class RoutingTablePersistence {

    private final File storageFile;

    // 定义地址类型常量
    private static final byte ADDRESS_TYPE_IPV4 = 4;
    private static final byte ADDRESS_TYPE_IPV6 = 6;

    // 固定的部分：20字节ID + 2字节端口 + 1字节地址类型
    private static final int NODE_HEADER_LENGTH = 20 + 2 + 1;

    public RoutingTablePersistence(File storageFile) {
        this.storageFile = storageFile;
    }

    /**
     * 将路由表的状态以支持 v4/v6 的可变长格式保存到文件。
     * @param table 要保存的路由表。
     */
    public void save(RoutingTable table) {
        List<Node> nodes = table.getAllNodes();
        if (nodes.isEmpty()) {
            if (storageFile.exists()) {
                storageFile.delete();
            }
            log.info("Routing table is empty, cleared storage file.");
            return;
        }

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(storageFile)))) {
            for (Node node : nodes) {
                // 1. 写入 20 字节的 Node ID
                dos.write(node.nodeId());

                InetAddress address = node.getIp();
                byte[] ipBytes = address.getAddress();

                // 2. 写入 1 字节的地址类型
                if (address instanceof Inet4Address) {
                    dos.writeByte(ADDRESS_TYPE_IPV4);
                } else if (address instanceof Inet6Address) {
                    dos.writeByte(ADDRESS_TYPE_IPV6);
                } else {
                    log.warn("Skipping node with unsupported address type: {}", node);
                    continue;
                }

                // 3. 写入 IP 地址 (4 或 16 字节)
                dos.write(ipBytes);

                // 4. 写入 2 字节的端口
                dos.writeShort(node.getPort());
            }
            dos.flush();
            log.info("Successfully saved {} nodes (IPv4/IPv6) to {}", nodes.size(), storageFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save routing table to file: {}", storageFile.getAbsolutePath(), e);
        }
    }

    /**
     * 从支持 v4/v6 的文件中加载节点列表。
     * @return 加载的节点列表。
     */
    public List<Node> load() {
        if (!storageFile.exists() || storageFile.length() == 0) {
            log.info("Storage file not found or is empty.");
            return Collections.emptyList();
        }

        List<Node> loadedNodes = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(storageFile)))) {
            while (dis.available() > 0) {
                // 1. 读取 20 字节的 Node ID
                byte[] nodeId = new byte[20]; // 假设ID长度为20
                dis.readFully(nodeId);

                // 2. 读取 1 字节的地址类型
                byte addressType = dis.readByte();

                // 3. 根据类型读取 IP 地址
                byte[] ipBytes;
                if (addressType == ADDRESS_TYPE_IPV4) {
                    ipBytes = new byte[4];
                } else if (addressType == ADDRESS_TYPE_IPV6) {
                    ipBytes = new byte[16];
                } else {
                    log.error("Corrupted storage file: unknown address type '{}'. Stopping load.", addressType);
                    return loadedNodes; // 返回已成功加载的部分
                }
                dis.readFully(ipBytes);
                InetAddress ipAddress = InetAddress.getByAddress(ipBytes);

                // 4. 读取 2 字节的端口
                int port = dis.readUnsignedShort();

                // 使用工厂方法创建 Node
                loadedNodes.add(Node.fromInetAddress(nodeId, ipAddress, port));
            }
            log.info("Successfully loaded {} nodes (IPv4/IPv6) from {}", loadedNodes.size(), storageFile.getAbsolutePath());
            return loadedNodes;
        } catch (EOFException e) {
            // 文件意外结束，可能是损坏的
            log.error("Storage file is corrupted (unexpected end of file). Loaded {} nodes before error.", loadedNodes.size(), e);
            return loadedNodes;
        } catch (IOException e) {
            log.error("Failed to load or parse routing table from file: {}. Starting fresh.", storageFile.getAbsolutePath(), e);
            return Collections.emptyList();
        }
    }
}