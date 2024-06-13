# 关键类

ListenableStateRouter#process -> ConditionRuleParser#parse -> 生成 ConditionRouterRule对象，然后在

ListenableStateRouter类中将对象转换成 state/StateRouter

ListenableStateRouter中的process方法，将会处理rawConfig变成 ConditionRouterRule，成最终的东西

