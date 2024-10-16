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
package org.apache.dubbo.rpc.protocol.tri.rest.mapping.meta;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.protocol.tri.rest.util.PathUtils;
import org.apache.dubbo.rpc.protocol.tri.rest.util.RestToolKit;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ServiceMeta extends AnnotationSupport {

    private final List<Class<?>> hierarchy;
    private final Class<?> type;
    private final Object service;
    private final URL url;
    private final String contextPath;

    private List<MethodMeta> exceptionHandlers;

    public ServiceMeta(Collection<Class<?>> hierarchy, Object service, URL url, RestToolKit toolKit) {
        super(toolKit);
        this.hierarchy = new ArrayList<>(hierarchy);
        type = this.hierarchy.get(0);
        this.service = service;
        this.url = url;
        contextPath = PathUtils.getContextPath(url);
    }

    public List<Class<?>> getHierarchy() {
        return hierarchy;
    }

    public Class<?> getType() {
        return type;
    }

    public Object getService() {
        return service;
    }

    public URL getUrl() {
        return url;
    }

    public String getServiceInterface() {
        return url.getServiceInterface();
    }

    public String getServiceGroup() {
        return url.getGroup();
    }

    public String getServiceVersion() {
        return url.getVersion();
    }

    public String getContextPath() {
        return contextPath;
    }

    public List<MethodMeta> getExceptionHandlers() {
        return exceptionHandlers;
    }

    @Override
    protected List<? extends AnnotatedElement> getAnnotatedElements() {
        return hierarchy;
    }

    @Override
    protected AnnotatedElement getAnnotatedElement() {
        return hierarchy.get(0);
    }

    @Override
    public String toString() {
        return "ServiceMeta{class='" + getType().getName() + "', service=" + service + '}';
    }

    public void addExceptionHandler(MethodMeta methodMeta) {
        if (exceptionHandlers == null) {
            exceptionHandlers = new ArrayList<>();
        }
        exceptionHandlers.add(methodMeta);
    }
}
