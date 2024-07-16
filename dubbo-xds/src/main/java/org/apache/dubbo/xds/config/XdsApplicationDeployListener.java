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
package org.apache.dubbo.xds.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.deploy.ApplicationDeployListener;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.xds.PilotExchanger;

import java.util.Collection;

import static org.apache.dubbo.config.Constants.SUPPORT_MESH_TYPE;

//在MOdule启动之后的Listener
public class XdsApplicationDeployListener implements ApplicationDeployListener {
    @Override
    public void onInitialize(ApplicationModel scopeModel) {}

    @Override
    public void onStarting(ApplicationModel scopeModel) {
        Collection<RegistryConfig> registryConfigs =
                scopeModel.getApplicationConfigManager().getRegistries(); // registry的只有一个的地址： istio://47.251.101.225:15010?security=plaintext
        for (RegistryConfig registryConfig : registryConfigs) {
            String protocol = registryConfig.getProtocol(); //protocol为istio
            if (StringUtils.isNotEmpty(protocol) && SUPPORT_MESH_TYPE.contains(protocol)) {
                URL url = URL.valueOf(registryConfig.getAddress()); //address直接成url了
                url = url.setScopeModel(scopeModel);
                scopeModel.getFrameworkModel().getBeanFactory().registerBean(PilotExchanger.createInstance(url)); //通过url创建PilotExchanger并且注入到beanFactory中
                break;
            }
        }
    }

    @Override
    public void onStarted(ApplicationModel scopeModel) {}

    @Override
    public void onStopping(ApplicationModel scopeModel) {}

    @Override
    public void onStopped(ApplicationModel scopeModel) {}

    @Override
    public void onFailure(ApplicationModel scopeModel, Throwable cause) {}
}
