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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.url.component.URLAddress;
import org.apache.dubbo.xds.bootstrap.Bootstrapper;
import org.apache.dubbo.xds.bootstrap.BootstrapperImpl;
import org.apache.dubbo.xds.security.api.CertPair;
import org.apache.dubbo.xds.security.api.CertSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc;
import io.envoyproxy.envoy.service.discovery.v3.DeltaDiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DeltaDiscoveryResponse;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollDomainSocketChannel;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.stub.StreamObserver;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.REGISTRY_ERROR_CREATE_CHANNEL_XDS;

public class XdsChannel {

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(XdsChannel.class);

    private static final String USE_AGENT = "use-agent";

    private URL url;

    private static final String SECURITY = "security";

    private static final String PLAINTEXT = "plaintext";

//    最终就是适配到了 grpc的 ManagedChannel 上去了
    private final ManagedChannel channel;

    public URL getUrl() {
        return url;
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    public XdsChannel(URL url) {
        ManagedChannel managedChannel = null;
        this.url = url;
        try {
//            todo 是否使用了 grpc-agent？？ 但是代码行为像是是否使用边车模式
            if (!url.getParameter(USE_AGENT, false)) {
                // TODO：Need to consider situation where only user sa_jwt
                if (PLAINTEXT.equals(url.getParameter(SECURITY))) {
//                    NettyChannelBuilder 是grpc的shade的类
                    managedChannel = NettyChannelBuilder.forAddress(url.getHost(), url.getPort())
                            .usePlaintext()
                            .build();
                } else {
                    CertSource signer = url.getOrDefaultApplicationModel()
                            .getExtensionLoader(CertSource.class)
                            .getExtension(url.getProtocol());
                    CertPair certPair = signer.getCert(url, null);
                    SslContext context = GrpcSslContexts.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .keyManager(
                                    new ByteArrayInputStream(
                                            certPair.getPublicKey().getBytes(StandardCharsets.UTF_8)),
                                    new ByteArrayInputStream(
                                            certPair.getPrivateKey().getBytes(StandardCharsets.UTF_8)))
                            .build();
                    managedChannel = NettyChannelBuilder.forAddress(url.getHost(), url.getPort())
                            .sslContext(context)
                            .build();
                }
            } else {
                BootstrapperImpl bootstrapper = new BootstrapperImpl();
                Bootstrapper.BootstrapInfo bootstrapInfo = bootstrapper.bootstrap();
                URLAddress address =
                        URLAddress.parse(bootstrapInfo.servers().get(0).target(), null, false);
//                io.grpc.netty.shaded.io.netty.channel.epoll;
                EpollEventLoopGroup elg = new EpollEventLoopGroup();
                managedChannel = NettyChannelBuilder.forAddress(new DomainSocketAddress("/" + address.getPath()))
                        .eventLoopGroup(elg)
                        .channelType(EpollDomainSocketChannel.class)
                        .usePlaintext()
                        .build();
            }
        } catch (Exception e) {
            logger.error(
                    REGISTRY_ERROR_CREATE_CHANNEL_XDS,
                    "",
                    "",
                    "Error occurred when creating gRPC channel to control panel.",
                    e);
        }
        channel = managedChannel;
    }

    /**
     *
     * 这些函数是用于与Envoy代理进行gRPC通信的函数，用于进行Delta Discovery Requests（增量发现请求）。
     * observeDeltaDiscoveryRequest函数创建一个StreamObserver，用于监听DeltaDiscoveryRequest类型的请求，并将其转换为DeltaDiscoveryResponse类型后发送给观察者。
     * createDeltaDiscoveryRequest函数创建一个StreamObserver，用于发送DiscoveryRequest类型的请求，并接收DiscoveryResponse类型的响应。
     * observeDeltaDiscoveryRequestV2和createDeltaDiscoveryRequestV2函数与上述两个函数类似，但使用的是Envoy的v2版本的API。
     * 这些函数通过AggregatedDiscoveryServiceGrpc类的newStub方法创建了一个gRPC的存根，用于与Envoy代理建立连接并进行通信。
     */

// 数学中的 德尔塔（三角形）  主要就是在 AdsObserver 类中进行了使用，观察者模式
    public StreamObserver<DeltaDiscoveryRequest> observeDeltaDiscoveryRequest(
            StreamObserver<DeltaDiscoveryResponse> observer) {
//        envoy的grpc的stub
//        AggregatedDiscoveryServiceGrpc类是Envoy代理与配置服务器进行交互的gRPC服务的客户端存根（Stub）。在gRPC中，存根（Stub）是编译时生成的客户端代码，它提供了与远程服务通信的方法。
        return AggregatedDiscoveryServiceGrpc.newStub(channel).deltaAggregatedResources(observer);
    }

    public StreamObserver<DiscoveryRequest> createDeltaDiscoveryRequest(StreamObserver<DiscoveryResponse> observer) {
        return AggregatedDiscoveryServiceGrpc.newStub(channel).streamAggregatedResources(observer);
    }

    public StreamObserver<io.envoyproxy.envoy.api.v2.DeltaDiscoveryRequest> observeDeltaDiscoveryRequestV2(
            StreamObserver<io.envoyproxy.envoy.api.v2.DeltaDiscoveryResponse> observer) {
        return io.envoyproxy.envoy.service.discovery.v2.AggregatedDiscoveryServiceGrpc.newStub(channel)
                .deltaAggregatedResources(observer);
    }

    public StreamObserver<io.envoyproxy.envoy.api.v2.DiscoveryRequest> createDeltaDiscoveryRequestV2(
            StreamObserver<io.envoyproxy.envoy.api.v2.DiscoveryResponse> observer) {
        return io.envoyproxy.envoy.service.discovery.v2.AggregatedDiscoveryServiceGrpc.newStub(channel)
                .streamAggregatedResources(observer);
    }

    public void destroy() {
        channel.shutdown();
    }
}
