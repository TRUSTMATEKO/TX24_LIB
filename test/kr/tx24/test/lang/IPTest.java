package kr.tx24.test.lang;

import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.IpMatcherUtils;

public class IPTest {




    public static void main(String[] args){
        IpMatcherUtils ipUtils = new IpMatcherUtils("192.168.0.0/24");
        System.out.println(ipUtils.matches("192.168.1.2"));
        System.out.println(ipUtils.matches("192.168.0.2"));

            System.out.println(DateUtils.getCurrentDay("yyyyMM")+"00");
            System.out.println(DateUtils.getCurrentDay("yyyyMM00"));

    }
}
