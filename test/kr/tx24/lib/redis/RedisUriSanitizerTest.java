package kr.tx24.lib.redis;

/** Self-contained contract test because TX24_LIB does not currently use a unit-test framework. */
public class RedisUriSanitizerTest {

    public static void main(String[] args) {
        assertEquals("redis://host:6379/0", RedisUriSanitizer.redact("redis://host:6379/0"));
        assertEquals("redis://***@host:6379/0", RedisUriSanitizer.redact("redis://user:password@host:6379/0"));
        assertEquals("rediss://***@host:6379/0", RedisUriSanitizer.redact("rediss://encoded%40secret@host:6379/0"));
        assertEquals(
                "redis://***@host1:6379/0,redis://***@host2:6379/0",
                RedisUriSanitizer.redact("redis://one:secret1@host1:6379/0,redis://two:secret2@host2:6379/0"));

        String error = RedisUriSanitizer.errorMessage(
                new IllegalStateException("Unable to connect to redis://admin:topsecret@localhost:6379/0"));
        assertFalse(error.contains("admin"));
        assertFalse(error.contains("topsecret"));
        assertTrue(error.contains("redis://***@localhost:6379/0"));
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "] but was [" + actual + "].");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected condition to be false.");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true.");
        }
    }
}
