package kr.tx24.test.redis;

import kr.tx24.lib.redis.RedisUtils;

public class RedisTest {


    public static void main(String[] args){
        System.setProperty("LOGGER"		, "true,false,false");
        System.setProperty("REDIS"		, "112.175.48.155:6379/0");

        System.out.println(RedisUtils.getGeoIpCountry("127.0.0.1"));
        System.out.println(RedisUtils.getGeoIpCountry("112.175.48.155"));
    }
}
