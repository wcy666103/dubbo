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
package org.apache.dubbo.xds;

import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.xds.istio.IstioEnv;

import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.envoyproxy.envoy.config.core.v3.Node;

/**
 * 构建一个io.envoyproxy.envoy.config.core.v3.Node对象，该对象表示Envoy代理的节点信息。
 * 它通过读取IstioEnv实例中的Pod名称、Pod命名空间、服务账户名称等环境变量信息来构建一个包含metadata的Node对象，并设置节点的ID和集群名称。
 */
public class NodeBuilder {

    private static final String SVC_CLUSTER_LOCAL = ".svc.cluster.local";

    public static Node build() {
        //        String podName = System.getenv("metadata.name");
        //        String podNamespace = System.getenv("metadata.namespace");

        String podName = IstioEnv.getInstance().getPodName();
        String podNamespace = IstioEnv.getInstance().getWorkloadNameSpace();
        String svcName = IstioEnv.getInstance().getIstioMetaClusterId();
        String saName = IstioEnv.getInstance().getServiceAccountName();

        Map<String, Value> metadataMap = new HashMap<>(2);

        metadataMap.put(
                "ISTIO_META_NAMESPACE",
                Value.newBuilder().setStringValue(podNamespace).build());
        metadataMap.put(
                "SERVICE_ACCOUNT", Value.newBuilder().setStringValue(saName).build());

        Struct metadata = Struct.newBuilder().putAllFields(metadataMap).build();

        // id -> sidecar~ip~{POD_NAME}~{NAMESPACE_NAME}.svc.cluster.local
        // cluster -> {SVC_NAME}
        return Node.newBuilder()
                .setMetadata(metadata)
                .setId("sidecar~" + NetUtils.getLocalHost() + "~" + podName + "~" + podNamespace + SVC_CLUSTER_LOCAL)
                .setCluster(svcName)
                .build();
    }
}
