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
package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import org.apache.dubbo.rpc.cluster.router.AbstractRouterRule;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.dubbo.rpc.cluster.Constants.CONDITIONS_KEY;

/**
 * 所以走完这个方法之后，这个对象就是对应的那个配置文件中的素有的
 * 这里边是有 scope属性的，直接就是确定好是 service还是Application
 */
public class ConditionRouterRule extends AbstractRouterRule {

//    里边放的是String，如果要其他类型，是否就需要换成对象了
    private List<String> conditions;
//    静态方法，里边会创建对应的对象，真会玩啊
    @SuppressWarnings("unchecked")
    public static ConditionRouterRule parseFromMap(Map<String, Object> map) {
        ConditionRouterRule conditionRouterRule = new ConditionRouterRule();
//        抽象类提供的方法
        conditionRouterRule.parseFromMap0(map);
//        条件处理在这
        Object conditions = map.get(CONDITIONS_KEY);
//        确定此 Class 对象表示的类或接口是否与指定参数表示的类或接口相同，或者是该 Class 类或接口的超类或超接口。
        if (conditions != null && List.class.isAssignableFrom(conditions.getClass())) {
//           这里边的每一条数据，应该都是按照 & 进行拆分之后然后分的
//            进来的是consumer，也就是说需要按照
            conditionRouterRule.setConditions(
                    ((List<Object>) conditions).stream().map(String::valueOf).collect(Collectors.toList()));
        }

        System.out.println("condition.config.model.ConditionRouterRule.parseFromMap = " + "==转换后==");
        conditionRouterRule.conditions.forEach(System.out::println);

        return conditionRouterRule;
    }

    public ConditionRouterRule() {}

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions;
    }
}
