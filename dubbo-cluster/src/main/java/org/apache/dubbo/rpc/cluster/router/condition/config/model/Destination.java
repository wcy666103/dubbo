package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.router.state.BitList;

public class Destination<T> {
    private int weight;
    private BitList<Invoker<T>> invokers;

    Destination(int weight, BitList<Invoker<T>> invokers) {
        this.weight = weight;
        this.invokers = invokers;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public BitList<Invoker<T>> getInvokers() {
        return invokers;
    }

    public void setInvokers(BitList<Invoker<T>> invokers) {
        this.invokers = invokers;
    }
}
