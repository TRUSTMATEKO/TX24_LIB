package kr.tx24.test.lb;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import kr.tx24.lib.lb.LoadBalancer;

/**
 * nlb.json 리로드로 서버 목록이 줄어든 뒤에도 getServer 가 IndexOutOfBoundsException 을 던지지 않는지 검증한다.
 * 실행: java -cp classes:lib/* kr.tx24.test.lb.LoadBalancerTest (실패 시 종료코드 1)
 */
public class LoadBalancerTest {

    public static void main(String[] args) {
        try {
            run();
            System.out.println("PASS");
            System.exit(0);
        } catch (Throwable t) {
            System.out.println("FAIL: " + t);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        ConcurrentHashMap<String, List<String>> serverList = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> serverPosition = new ConcurrentHashMap<>();
        setStatic("SERVER_LIST", serverList);
        setStatic("SERVER_POSITION", serverPosition);
        setStatic("SERVER_BROKEN_LIST", new ConcurrentHashMap<>());

        serverList.put("wallet", names(7));
        for (int i = 0; i < 6; i++) {
            LoadBalancer.getServer("wallet");
        }
        // 위치는 6, 이후 리로드로 목록이 5개로 줄어든 상황 (SERVER_POSITION 은 유지됨)
        serverList.put("wallet", names(5));

        List<String> got = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            got.add(LoadBalancer.getServer("wallet"));
            LoadBalancer.getExcludeBrokenServer("wallet");
        }
        for (String s : got) {
            check(names(5).contains(s), "unexpected server " + s);
        }
        check(got.stream().distinct().count() == 5, "rotation must cover all 5 servers: " + got);

        serverList.put("empty", new ArrayList<>());
        check("UNKNOWN_SERVER:PORT".equals(LoadBalancer.getServer("empty")), "empty list");
        check("UNKNOWN_SERVER:PORT".equals(LoadBalancer.getServer("none")), "missing key");

        // int 오버플로 후에도 음수 인덱스가 나오지 않아야 한다
        serverPosition.put("wallet", new AtomicInteger(Integer.MAX_VALUE));
        for (int i = 0; i < 3; i++) {
            check(names(5).contains(LoadBalancer.getServer("wallet")), "overflow");
        }
    }

    private static List<String> names(int n) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add("10.0.0." + i + ":9000");
        }
        return l;
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            System.out.println("FAIL: " + msg);
            System.exit(1);
        }
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field f = LoadBalancer.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }
}
