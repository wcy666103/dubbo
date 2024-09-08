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
package org.apache.dubbo.xds.resource.update;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已经验证过的 资源更新的合集
 * @param <T>
 */
public final class ValidatedResourceUpdate<T extends ResourceUpdate> {
    private Map<String, ParsedResource<T>> parsedResources;
    private Set<String> unpackedResources;
    private Set<String> invalidResources;
    private List<String> errors;

    // validated resource update
    public ValidatedResourceUpdate(
            Map<String, ParsedResource<T>> parsedResources,
            Set<String> unpackedResources,
            Set<String> invalidResources,
            List<String> errors) {
        this.parsedResources = parsedResources;
        this.unpackedResources = unpackedResources;
        this.invalidResources = invalidResources;
        this.errors = errors;
    }

    public Map<String, ParsedResource<T>> getParsedResources() {
        return parsedResources;
    }

    public Set<String> getUnpackedResources() {
        return unpackedResources;
    }

    public Set<String> getInvalidResources() {
        return invalidResources;
    }

    public List<String> getErrors() {
        return errors;
    }
}
