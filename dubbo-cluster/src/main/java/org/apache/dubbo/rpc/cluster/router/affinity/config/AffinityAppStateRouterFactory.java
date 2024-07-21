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
package org.apache.dubbo.rpc.cluster.router.affinity.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.cluster.router.condition.config.AppStateRouter;
import org.apache.dubbo.rpc.cluster.router.state.StateRouter;
import org.apache.dubbo.rpc.cluster.router.state.StateRouterFactory;

/**
 * Application level router factory
 * AppRouter should after ServiceRouter 并且是在 service之后
 *
 * 这个 AppRouter保证的始终只有一个实例。
 * 应该是dubbo的app级别的
 */
@Activate(order = 135)
public class AffinityAppStateRouterFactory implements StateRouterFactory {
    public static final String NAME = "affinity_app";

    @SuppressWarnings("rawtypes")
    private volatile StateRouter router;

    @SuppressWarnings("unchecked")
    @Override
    public <T> StateRouter<T> getRouter(Class<T> interfaceClass, URL url) {
        if (router != null) {
            return router;
        }
        synchronized (this) {
            if (router == null) {
                router = createRouter(url);
            }
        }
        return router;
    }

    private <T> StateRouter<T> createRouter(URL url) {
        return new AppStateRouter<>(url);
    }
}
