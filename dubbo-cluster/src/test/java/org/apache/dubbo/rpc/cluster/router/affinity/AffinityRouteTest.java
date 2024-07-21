package org.apache.dubbo.rpc.cluster.router.affinity;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffinityRouteTest {

    private static final String[] providerUrls = {
            "dubbo://127.0.0.1/com.foo.BarService",
            "dubbo://127.0.0.1/com.foo.BarService",
            "dubbo://127.0.0.1/com.foo.BarService?env=normal",
            "dubbo://127.0.0.1/com.foo.BarService?env=normal",
            "dubbo://127.0.0.1/com.foo.BarService?env=normal",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
            "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=normal",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=gray",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=gray",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=normal",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=normal",
            "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService",
            "dubbo://dubbo.apache.org/com.foo.BarService",
            "dubbo://dubbo.apache.org/com.foo.BarService?env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService?env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService?env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=gray",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=gray",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal",
            "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal"
    };

    private List<Invoker> buildInvokers() {
        List<Invoker> res = new ArrayList<>();
        for (String url : providerUrls) {
            URL u = new URL(url);
            res.add(new BaseInvoker(u));
        }
        return res;
    }

    private URL newUrl(String s) {
        return new URL(s);
    }

    private FieldMatcher genMatcher(String key) {
        return new FieldMatcher(key);
    }

    class InvokersFilters extends ArrayList<FieldMatcher> {
        public InvokersFilters addMatcher(String rule) {
            this.add(genMatcher(rule));
            return this;
        }

        public List<Invoker> filtrate(List<Invoker> invokers, URL url, Invocation invocation) {
            for (FieldMatcher cond : this) {
                List<Invoker> tmpInv = new ArrayList<>();
                for (Invoker invoker : invokers) {
                    if (cond.matchInvoker(url, invoker, invocation)) {
                        tmpInv.add(invoker);
                    }
                }
                invokers = tmpInv;
            }
            return invokers;
        }
    }

    @Test
    void testAffinityRoute() {
        AffinityRoute affinityRoute = new AffinityRoute();

        List<Invoker> invokers = buildInvokers();
        URL url = newUrl("consumer://127.0.0.1/com.foo.BarService?env=gray&region=beijing");
        Invocation invocation = new RPCInvocation("getComment", null, null);

        // Test base affinity router
        affinityRoute.setEnabled(true);
        affinityRoute.setMatcher(genMatcher("region"));
        affinityRoute.setRatio(20);

        InvokersFilters filters = new InvokersFilters().addMatcher("region=$region");

        List<Invoker> res = affinityRoute.route(invokers, url, invocation);
        List<Invoker> filtered = filters.filtrate(invokers, url, invocation);
        if (filtered.size() < providerUrls.length * (affinityRoute.getRatio() / 100.0)) {
            assertEquals(0, filtered.size());
        } else {
            assertEquals(filtered, res);
        }

        // Test bad ratio
        affinityRoute.setRatio(101);
        res = affinityRoute.route(invokers, url, invocation);
        filtered = filters.filtrate(invokers, url, invocation);
        assertTrue(res.isEmpty());

        // Test ratio false
        affinityRoute.setRatio(80);
        res = affinityRoute.route(invokers, url, invocation);
        filtered = filters.filtrate(invokers, url, invocation);
        assertEquals(filtered, res);

        // Test ignore affinity route
        affinityRoute.setMatcher(genMatcher("bad-key"));
        res = affinityRoute.route(invokers, url, invocation);
        filtered = filters.filtrate(invokers, url, invocation);
        assertEquals(filtered, res);
    }
}
