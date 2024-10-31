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
package org.apache.dubbo.xds.resource.route.plugin;

import org.apache.dubbo.xds.resource.common.ConfigOrError;

import com.google.protobuf.Message;

/**
 * Defines the parsing functionality of a ClusterSpecifierPlugin as defined in the Enovy proto
 * api/envoy/config/route/v3/route.proto.
 * 定义 Enovy proto api/envoy/config/route/v3/route.proto 中定义的 ClusterSpecifierPlugin 的解析功能。
 */
public interface ClusterSpecifierPlugin {
    /**
     * The proto message types supported by this plugin. A plugin will be registered by each of its supported message
     * types.
     * 此插件支持的 proto 消息类型。插件将按其每个支持的消息类型进行注册
     */
    String[] typeUrls();

    ConfigOrError<? extends PluginConfig> parsePlugin(Message rawProtoMessage);
}
