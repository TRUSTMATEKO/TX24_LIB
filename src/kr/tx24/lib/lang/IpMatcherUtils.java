package kr.tx24.lib.lang;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class IpMatcherUtils {

	private final List<IpRange> allowedRanges = new ArrayList<>();

	public IpMatcherUtils(String ipAddresses) {
        if (ipAddresses == null) return;
        for (String part : ipAddresses.split(",")) {
            addRange(part.trim());
        }
    }

  
    public IpMatcherUtils(List<String> ipAddresses) {
        if (ipAddresses == null) return;
        for (String ip : ipAddresses) {
            if (ip != null) {
                addRange(ip.trim());
            }
        }
    }

    private void addRange(String ipOrCidr) {
        if (!ipOrCidr.isEmpty()) {
            allowedRanges.add(new IpRange(ipOrCidr));
        }
    }

    public boolean matches(String clientIp) {
        long clientIpLong = ipToLong(clientIp);
        if (clientIpLong == -1) return false;

        // 리스트를 직접 순회하여 오버헤드 최소화
        for (int i = 0; i < allowedRanges.size(); i++) {
            if (allowedRanges.get(i).isMatch(clientIpLong)) {
                return true;
            }
        }
        return false;
    }

    private static long ipToLong(String ipAddress) {
        try {
            byte[] bytes = InetAddress.getByName(ipAddress).getAddress();
            if (bytes.length != 4) return -1; // IPv4만 지원 (IPv6는 long 범위를 넘음)
            
            long result = 0;
            for (byte b : bytes) {
                result = (result << 8) | (b & 0xFF);
            }
            return result;
        } catch (UnknownHostException e) {
            return -1;
        }
    }

    private static class IpRange {
        private final long startIp;
        private final long endIp;

        public IpRange(String range) {
            if (range.contains("/")) {
                String[] parts = range.split("/");
                long baseIp = ipToLong(parts[0]);
                int prefix = Integer.parseInt(parts[1]);
                
                // CIDR 마스크 계산 (예: /24 -> 0xFFFFFF00)
                long mask = (prefix == 0) ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
                this.startIp = baseIp & mask;
                this.endIp = startIp | (~mask & 0xFFFFFFFFL);
            } else {
                this.startIp = ipToLong(range);
                this.endIp = this.startIp;
            }
        }

        public boolean isMatch(long clientIpLong) {
            return clientIpLong >= startIp && clientIpLong <= endIp;
        }
    }

    public static void main(String[] args) {
    	IpMatcherUtils matcher = new IpMatcherUtils("127.0.0.1, 192.168.1.0/24");
        System.out.println(matcher.matches("192.168.1.50")); // true
        System.out.println(matcher.matches("192.168.2.1"));  // false
    }
}
