/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.zookeeper.curator5;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigItem;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.zookeeper.AbstractZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.DataListener;
import org.apache.dubbo.remoting.zookeeper.EventType;
import org.apache.dubbo.remoting.zookeeper.StateListener;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import static org.apache.dubbo.common.constants.CommonConstants.SESSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_FAILED_CONNECT_REGISTRY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.REGISTRY_ZOOKEEPER_EXCEPTION;

public class Curator5ZookeeperClient
        extends AbstractZookeeperClient<
                Curator5ZookeeperClient.NodeCacheListenerImpl, Curator5ZookeeperClient.CuratorWatcherImpl> {

    protected static final ErrorTypeAwareLogger logger =
            LoggerFactory.getErrorTypeAwareLogger(Curator5ZookeeperClient.class);

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private final CuratorFramework client;
    private static Map<String, NodeCache> nodeCacheMap = new ConcurrentHashMap<>();

    public Curator5ZookeeperClient(URL url) {
        super(url);
        try {
            int timeout = url.getParameter(TIMEOUT_KEY, DEFAULT_CONNECTION_TIMEOUT_MS);
            int sessionExpireMs = url.getParameter(SESSION_KEY, DEFAULT_SESSION_TIMEOUT_MS);
            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(url.getBackupAddress())
//                    重试策略
                    .retryPolicy(new RetryNTimes(1, 1000))
//                    连接超时设置
                    .connectionTimeoutMs(timeout)
                    .sessionTimeoutMs(sessionExpireMs);
            String userInformation = url.getUserInformation();
            if (userInformation != null && userInformation.length() > 0) {
//                digest认证方式是基于 用户名和密码的认证机制
                builder = builder.authorization("digest", userInformation.getBytes());
//                ACL（Access Control List）提供者用于定义ZooKeeper节点的访问控制列表。
//                在ZooKeeper中，ACL是由一系列Id和权限组成的。
//                Id可以是world、auth、digest等，权限包括CREATE、READ、WRITE、DELETE和ADMIN。
                builder.aclProvider(new ACLProvider() {
                    @Override
                    public List<ACL> getDefaultAcl() {
//                        表示创建者拥有所有权限（CREATE、READ、WRITE、DELETE和ADMIN）
                        return ZooDefs.Ids.CREATOR_ALL_ACL;
                    }

                    @Override
                    public List<ACL> getAclForPath(String path) {
                        return ZooDefs.Ids.CREATOR_ALL_ACL;
                    }
                });
            }
            client = builder.build();
//            添加一个连接状态监听器，当连接状态发生变化时，会触发其相应的方法
            client.getConnectionStateListenable().addListener(new CuratorConnectionStateListener(url));
            client.start();
//            阻塞当前线程，直到客户端成功连接到ZooKeeper服务器或超时。返回值 connected 表示是否成功连接。
            boolean connected = client.blockUntilConnected(timeout, TimeUnit.MILLISECONDS);

            if (!connected) {
                IllegalStateException illegalStateException =
                        new IllegalStateException("zookeeper not connected, the address is: " + url);

                // 5-1 Failed to connect to configuration center.
                logger.error(
                        CONFIG_FAILED_CONNECT_REGISTRY,
                        "Zookeeper server offline",
                        "",
                        "Failed to connect with zookeeper",
                        illegalStateException);

                throw illegalStateException;
            }

        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void createPersistent(String path, boolean faultTolerant) {
        try {
            client.create().forPath(path);
        } catch (NodeExistsException e) {
            if (!faultTolerant) {
                logger.warn(REGISTRY_ZOOKEEPER_EXCEPTION, "", "", "ZNode " + path + " already exists.", e);
                throw new IllegalStateException(e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void createEphemeral(String path, boolean faultTolerant) {
        try {
//            创建临时节点
            client.create().withMode(CreateMode.EPHEMERAL).forPath(path);
        } catch (NodeExistsException e) {
//            根据节点存在情况来创建
            if (faultTolerant) {
                logger.info("ZNode " + path
                        + " already exists, since we will only try to recreate a node on a session expiration"
                        + ", this duplication might be caused by a delete delay from the zk server, which means the old expired session"
                        + " may still holds this ZNode and the server just hasn't got time to do the deletion. In this case, "
                        + "we can just try to delete and create again.");
                deletePath(path);
                createEphemeral(path, true);
            } else {
                logger.warn(REGISTRY_ZOOKEEPER_EXCEPTION, "", "", "ZNode " + path + " already exists.", e);
                throw new IllegalStateException(e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createPersistent(String path, String data, boolean faultTolerant) {
        byte[] dataBytes = data.getBytes(CHARSET);
        try {
            client.create().forPath(path, dataBytes);
        } catch (NodeExistsException e) {
            if (faultTolerant) {
                logger.info("ZNode " + path + " already exists. Will be override with new data.");
                try {
                    client.setData().forPath(path, dataBytes);
                } catch (Exception e1) {
                    throw new IllegalStateException(e.getMessage(), e1);
                }
            } else {
                logger.warn(REGISTRY_ZOOKEEPER_EXCEPTION, "", "", "ZNode " + path + " already exists.", e);
                throw new IllegalStateException(e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createEphemeral(String path, String data, boolean faultTolerant) {
        byte[] dataBytes = data.getBytes(CHARSET);
        try {
            client.create().withMode(CreateMode.EPHEMERAL).forPath(path, dataBytes);
        } catch (NodeExistsException e) {
            if (faultTolerant) {
                logger.info("ZNode " + path
                        + " already exists, since we will only try to recreate a node on a session expiration"
                        + ", this duplication might be caused by a delete delay from the zk server, which means the old expired session"
                        + " may still holds this ZNode and the server just hasn't got time to do the deletion. In this case, "
                        + "we can just try to delete and create again.");
                deletePath(path);
                createEphemeral(path, data, true);
            } else {
                logger.warn(REGISTRY_ZOOKEEPER_EXCEPTION, "", "", "ZNode " + path + " already exists.", e);
                throw new IllegalStateException(e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void update(String path, String data, int version) {
        byte[] dataBytes = data.getBytes(CHARSET);
        try {
            client.setData().withVersion(version).forPath(path, dataBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void update(String path, String data) {
        byte[] dataBytes = data.getBytes(CHARSET);
        try {
            client.setData().forPath(path, dataBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createOrUpdatePersistent(String path, String data) {
        try {
            if (checkExists(path)) {
                update(path, data);
            } else {
                createPersistent(path, data, true);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createOrUpdateEphemeral(String path, String data) {
        try {
            if (checkExists(path)) {
                update(path, data);
            } else {
                createEphemeral(path, data, true);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createOrUpdatePersistent(String path, String data, Integer version) {
        try {
            if (checkExists(path) && version != null) {
                update(path, data, version);
            } else {
                createPersistent(path, data, false);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void createOrUpdateEphemeral(String path, String data, Integer version) {
        try {
            if (checkExists(path) && version != null) {
                update(path, data, version);
            } else {
                createEphemeral(path, data, false);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected void deletePath(String path) {
        try {
            client.delete().deletingChildrenIfNeeded().forPath(path);
        } catch (NoNodeException ignored) {
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getChildren(String path) {
        try {
            return client.getChildren().forPath(path);
        } catch (NoNodeException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public boolean checkExists(String path) {
        try {
            if (client.checkExists().forPath(path) != null) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return client.getZookeeperClient().isConnected();
    }

    @Override
    public String doGetContent(String path) {
        try {
            byte[] dataBytes = client.getData().forPath(path);
            return (dataBytes == null || dataBytes.length == 0) ? null : new String(dataBytes, CHARSET);
        } catch (NoNodeException e) {
            // ignore NoNode Exception.
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public ConfigItem doGetConfigItem(String path) {
        String content;
        Stat stat;
        try {
            stat = new Stat();
            byte[] dataBytes = client.getData().storingStatIn(stat).forPath(path);
            content = (dataBytes == null || dataBytes.length == 0) ? null : new String(dataBytes, CHARSET);
        } catch (NoNodeException e) {
            return new ConfigItem();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return new ConfigItem(content, stat);
    }

    @Override
    public void doClose() {
        super.doClose();
//        nodeCacheMap.forEach((path, nodeCache) -> CloseableUtils.closeQuietly(nodeCache));
//        如果加上这一行，会报错：  dubbo-samples-version案例报错
        client.close();
    }
    /*org.apache.zookeeper.KeeperException$NodeExistsException: KeeperErrorCode = NodeExists for /dubbo/mapping/org.apache.dubbo.samples.version.api.VersionService
2024-11-28T03:40:12.2058898Z 	at org.apache.zookeeper.KeeperException.create(KeeperException.java:126) ~[zookeeper-3.7.2.jar:3.7.2]
2024-11-28T03:40:12.2060153Z 	at org.apache.zookeeper.KeeperException.create(KeeperException.java:54) ~[zookeeper-3.7.2.jar:3.7.2]
2024-11-28T03:40:12.2061329Z 	at org.apache.zookeeper.ZooKeeper.create(ZooKeeper.java:1450) ~[zookeeper-3.7.2.jar:3.7.2]
2024-11-28T03:40:12.2062643Z 	at org.apache.curator.framework.imps.CreateBuilderImpl$18.call(CreateBuilderImpl.java:1154) ~[curator-framework-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2064398Z 	at org.apache.curator.framework.imps.CreateBuilderImpl$18.call(CreateBuilderImpl.java:1136) ~[curator-framework-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2065786Z 	at org.apache.curator.RetryLoop.callWithRetry(RetryLoop.java:88) ~[curator-client-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2067287Z 	at org.apache.curator.framework.imps.CreateBuilderImpl.pathInForeground(CreateBuilderImpl.java:1136) ~[curator-framework-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2069193Z 	at org.apache.curator.framework.imps.CreateBuilderImpl.protectedPathInForeground(CreateBuilderImpl.java:559) ~[curator-framework-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2070991Z 	at org.apache.curator.framework.imps.CreateBuilderImpl.forPath(CreateBuilderImpl.java:551) ~[curator-framework-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2072965Z 	at org.apache.curator.framework.imps.CreateBuilderImpl.forPath(CreateBuilderImpl.java:67) ~[curator-framework-5.7.1.jar:5.7.1]
2024-11-28T03:40:12.2074972Z 	at org.apache.dubbo.remoting.zookeeper.curator5.Curator5ZookeeperClient.createPersistent(Curator5ZookeeperClient.java:161) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2077345Z 	at org.apache.dubbo.remoting.zookeeper.curator5.Curator5ZookeeperClient.createOrUpdatePersistent(Curator5ZookeeperClient.java:254) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2079662Z 	at org.apache.dubbo.remoting.zookeeper.curator5.AbstractZookeeperClient.createOrUpdate(AbstractZookeeperClient.java:198) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2081993Z 	at org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport.registerServiceAppMapping(ZookeeperMetadataReport.java:203) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2084293Z 	at org.apache.dubbo.registry.client.metadata.MetadataServiceNameMapping.map(MetadataServiceNameMapping.java:123) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2086112Z 	at org.apache.dubbo.config.ServiceConfig.mapServiceName(ServiceConfig.java:431) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2087647Z 	at org.apache.dubbo.config.ServiceConfig.lambda$exported$1(ServiceConfig.java:404) ~[dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2088765Z 	at java.util.ArrayList.forEach(ArrayList.java:1259) [?:1.8.0_342]
2024-11-28T03:40:12.2089836Z 	at org.apache.dubbo.config.ServiceConfig.exported(ServiceConfig.java:397) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2091594Z 	at org.apache.dubbo.config.spring.ServiceBean.exported(ServiceBean.java:139) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2093121Z 	at org.apache.dubbo.config.ServiceConfig.doExport(ServiceConfig.java:556) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2094487Z 	at org.apache.dubbo.config.ServiceConfig.export(ServiceConfig.java:343) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2096179Z 	at org.apache.dubbo.config.deploy.DefaultModuleDeployer.exportServiceInternal(DefaultModuleDeployer.java:495) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2098163Z 	at org.apache.dubbo.config.deploy.DefaultModuleDeployer.exportServices(DefaultModuleDeployer.java:442) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2100000Z 	at org.apache.dubbo.config.deploy.DefaultModuleDeployer.startSync(DefaultModuleDeployer.java:177) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2101769Z 	at org.apache.dubbo.config.deploy.DefaultModuleDeployer.start(DefaultModuleDeployer.java:159) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2103912Z 	at org.apache.dubbo.config.spring.context.DubboDeployApplicationListener.onContextRefreshedEvent(DubboDeployApplicationListener.java:167) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2106458Z 	at org.apache.dubbo.config.spring.context.DubboDeployApplicationListener.onApplicationEvent(DubboDeployApplicationListener.java:153) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2108939Z 	at org.apache.dubbo.config.spring.context.DubboDeployApplicationListener.onApplicationEvent(DubboDeployApplicationListener.java:52) [dubbo-3.3.3-SNAPSHOT.jar:3.3.3-SNAPSHOT]
2024-11-28T03:40:12.2111604Z 	at org.springframework.context.event.SimpleApplicationEventMulticaster.doInvokeListener(SimpleApplicationEventMulticaster.java:176) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2114018Z 	at org.springframework.context.event.SimpleApplicationEventMulticaster.invokeListener(SimpleApplicationEventMulticaster.java:169) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2116354Z 	at org.springframework.context.event.SimpleApplicationEventMulticaster.multicastEvent(SimpleApplicationEventMulticaster.java:143) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2118875Z 	at org.springframework.context.support.AbstractApplicationContext.publishEvent(AbstractApplicationContext.java:421) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2121005Z 	at org.springframework.context.support.AbstractApplicationContext.publishEvent(AbstractApplicationContext.java:378) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2123140Z 	at org.springframework.context.support.AbstractApplicationContext.finishRefresh(AbstractApplicationContext.java:938) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2125232Z 	at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:586) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2127260Z 	at org.springframework.context.support.ClassPathXmlApplicationContext.<init>(ClassPathXmlApplicationContext.java:144) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2129296Z 	at org.springframework.context.support.ClassPathXmlApplicationContext.<init>(ClassPathXmlApplicationContext.java:85) [spring-context-5.3.24.jar:5.3.24]
2024-11-28T03:40:12.2130900Z 	at org.apache.dubbo.samples.version.VersionProvider.main(VersionProvider.java:29) [classes/:?]*/

    @Override
    public Curator5ZookeeperClient.CuratorWatcherImpl createTargetChildListener(String path, ChildListener listener) {
        return new Curator5ZookeeperClient.CuratorWatcherImpl(client, listener, path);
    }

    @Override
    public List<String> addTargetChildListener(String path, CuratorWatcherImpl listener) {
        try {
            return client.getChildren().usingWatcher(listener).forPath(path);
        } catch (NoNodeException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    protected Curator5ZookeeperClient.NodeCacheListenerImpl createTargetDataListener(
            String path, DataListener listener) {
        return new NodeCacheListenerImpl(client, listener, path);
    }

    @Override
    protected void addTargetDataListener(String path, Curator5ZookeeperClient.NodeCacheListenerImpl nodeCacheListener) {
        this.addTargetDataListener(path, nodeCacheListener, null);
    }

    @Override
    protected void addTargetDataListener(
            String path, Curator5ZookeeperClient.NodeCacheListenerImpl nodeCacheListener, Executor executor) {
        try {
            NodeCache nodeCache = new NodeCache(client, path);
            if (nodeCacheMap.putIfAbsent(path, nodeCache) != null) {
                return;
            }
            if (executor == null) {
                nodeCache.getListenable().addListener(nodeCacheListener);
            } else {
                nodeCache.getListenable().addListener(nodeCacheListener, executor);
            }

            nodeCache.start();
        } catch (Exception e) {
            throw new IllegalStateException("Add nodeCache listener for path:" + path, e);
        }
    }

    @Override
    protected void removeTargetDataListener(
            String path, Curator5ZookeeperClient.NodeCacheListenerImpl nodeCacheListener) {
        NodeCache nodeCache = nodeCacheMap.get(path);
        if (nodeCache != null) {
            nodeCache.getListenable().removeListener(nodeCacheListener);
        }
        nodeCacheListener.dataListener = null;
    }

    @Override
    public void removeTargetChildListener(String path, CuratorWatcherImpl listener) {
        listener.unwatch();
    }

    static class NodeCacheListenerImpl implements NodeCacheListener {

        private CuratorFramework client;

        private volatile DataListener dataListener;

        private String path;

        protected NodeCacheListenerImpl() {}

        public NodeCacheListenerImpl(CuratorFramework client, DataListener dataListener, String path) {
            this.client = client;
            this.dataListener = dataListener;
            this.path = path;
        }

        @Override
        public void nodeChanged() throws Exception {
            ChildData childData = nodeCacheMap.get(path).getCurrentData();
            String content = null;
            EventType eventType;
            if (childData == null) {
                eventType = EventType.NodeDeleted;
            } else if (childData.getStat().getVersion() == 0) {
                content = new String(childData.getData(), CHARSET);
                eventType = EventType.NodeCreated;
            } else {
                content = new String(childData.getData(), CHARSET);
                eventType = EventType.NodeDataChanged;
            }
            dataListener.dataChanged(path, content, eventType);
        }
    }

    /**
     * 所有的 watcher都是走的这个自定义
     */
    static class CuratorWatcherImpl implements CuratorWatcher {

        private CuratorFramework client;
        private volatile ChildListener childListener;
        private String path;

        public CuratorWatcherImpl(CuratorFramework client, ChildListener listener, String path) {
            this.client = client;
            this.childListener = listener;
            this.path = path;
        }

        protected CuratorWatcherImpl() {}

        public void unwatch() {
            this.childListener = null;
        }

        @Override
        public void process(WatchedEvent event) throws Exception {
            // if client connect or disconnect to server, zookeeper will queue
            // watched event(Watcher.Event.EventType.None, .., path = null).
            if (event.getType() == Watcher.Event.EventType.None) {
                return;
            }

            if (childListener != null) {
//                子节点监听器也需要变动
                childListener.childChanged(
                        path, client.getChildren().usingWatcher(this).forPath(path));
            }
        }
    }

    /**
     * 给 Curator 当连接的Listener，收到状态变更信息之后还要转成自己的
     */
    private class CuratorConnectionStateListener implements ConnectionStateListener {
        private final long UNKNOWN_SESSION_ID = -1L;

        private long lastSessionId;
        private int timeout;
        private int sessionExpireMs;

        public CuratorConnectionStateListener(URL url) {
            this.timeout = url.getParameter(TIMEOUT_KEY, DEFAULT_CONNECTION_TIMEOUT_MS);
            this.sessionExpireMs = url.getParameter(SESSION_KEY, DEFAULT_SESSION_TIMEOUT_MS);
        }

        @Override
        public void stateChanged(CuratorFramework client, ConnectionState state) {
            long sessionId = UNKNOWN_SESSION_ID;
            try {
                sessionId = client.getZookeeperClient().getZooKeeper().getSessionId();
            } catch (Exception e) {
                logger.warn(
                        REGISTRY_ZOOKEEPER_EXCEPTION,
                        "",
                        "",
                        "Curator client state changed, but failed to get the related zk session instance.");
            }

            if (state == ConnectionState.LOST) {
                logger.warn(
                        REGISTRY_ZOOKEEPER_EXCEPTION,
                        "",
                        "",
                        "Curator zookeeper session " + Long.toHexString(lastSessionId) + " expired.");
//                续上自定义的
                Curator5ZookeeperClient.this.stateChanged(StateListener.SESSION_LOST);
            } else if (state == ConnectionState.SUSPENDED) {
                logger.warn(
                        REGISTRY_ZOOKEEPER_EXCEPTION,
                        "",
                        "",
                        "Curator zookeeper connection of session " + Long.toHexString(sessionId) + " timed out. "
                                + "connection timeout value is " + timeout + ", session expire timeout value is "
                                + sessionExpireMs);
                Curator5ZookeeperClient.this.stateChanged(StateListener.SUSPENDED);
            } else if (state == ConnectionState.CONNECTED) {
                lastSessionId = sessionId;
                logger.info("Curator zookeeper client instance initiated successfully, session id is "
                        + Long.toHexString(sessionId));
                Curator5ZookeeperClient.this.stateChanged(StateListener.CONNECTED);
            } else if (state == ConnectionState.RECONNECTED) {
                if (lastSessionId == sessionId && sessionId != UNKNOWN_SESSION_ID) {
                    logger.warn(
                            REGISTRY_ZOOKEEPER_EXCEPTION,
                            "",
                            "",
                            "Curator zookeeper connection recovered from connection lose, " + "reuse the old session "
                                    + Long.toHexString(sessionId));
                    Curator5ZookeeperClient.this.stateChanged(StateListener.RECONNECTED);
                } else {
                    logger.warn(
                            REGISTRY_ZOOKEEPER_EXCEPTION,
                            "",
                            "",
                            "New session created after old session lost, " + "old session "
                                    + Long.toHexString(lastSessionId) + ", new session " + Long.toHexString(sessionId));
                    lastSessionId = sessionId;
                    Curator5ZookeeperClient.this.stateChanged(StateListener.NEW_SESSION_CREATED);
                }
            }
        }
    }

    /**
     * just for unit test
     *
     * @return
     */
    CuratorFramework getClient() {
        return client;
    }
}
