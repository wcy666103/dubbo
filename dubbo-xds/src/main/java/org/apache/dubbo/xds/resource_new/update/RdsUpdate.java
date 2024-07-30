package org.apache.dubbo.xds.resource_new.update;

import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.xds.resource_new.route.VirtualHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RdsUpdate implements ResourceUpdate {
    // The list virtual hosts that make up the route table.
    final List<VirtualHost> virtualHosts;

    public RdsUpdate(List<VirtualHost> virtualHosts) {
        Assert.notNull(virtualHosts, "virtualHosts must not be null");
        this.virtualHosts = Collections.unmodifiableList(new ArrayList<>(virtualHosts));
    }

    @Override
    public String toString() {
        return "RdsUpdate{" + "virtualHosts=" + virtualHosts + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(virtualHosts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RdsUpdate that = (RdsUpdate) o;
        return Objects.equals(virtualHosts, that.virtualHosts);
    }
}
