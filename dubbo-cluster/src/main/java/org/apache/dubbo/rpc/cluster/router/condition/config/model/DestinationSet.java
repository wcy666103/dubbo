package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.router.state.BitList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class DestinationSet<T> {
    private final List<Destination<T>> destinations;
    private long weightSum;
    private final ThreadLocalRandom random;

    public DestinationSet() {
        this.destinations = new ArrayList<>();
        this.weightSum = 0;
        this.random = ThreadLocalRandom.current();
    }

    public void addDest(int weight, BitList<Invoker<T>> ivks) {
        destinations.add(new Destination(weight, ivks));
        weightSum += weight;
    }

    public BitList<Invoker<T>> randDest() {
        if (destinations.size() == 1) {
            return destinations.get(0).getInvokers();
        }

        long sum = random.nextLong(weightSum);
        for (Destination destination : destinations) {
            sum -= destination.getWeight();
            if (sum <= 0) {
                return destination.getInvokers();
            }
        }
        return BitList.emptyList(); // 永远不会到达这里才对
    }

    public List<Destination<T>> getDestinations() {
        return destinations;
    }

    public long getWeightSum() {
        return weightSum;
    }

    public void setWeightSum(long weightSum) {
        this.weightSum = weightSum;
    }

    public Random getRandom() {
        return random;
    }
}
