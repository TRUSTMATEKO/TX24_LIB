public class IouringDefaultCheck {
    public static void main(String[] args) throws Exception {
        String key = "io.lettuce.core.iouring";
        if (args[0].equals("absent")) System.clearProperty(key);
        Class.forName("kr.tx24.lib.lang.SystemUtils");
        String expected = args[0].equals("true") ? "true" : "false";
        if (!expected.equals(System.getProperty(key))) {
            System.err.println("FAIL: " + args[0]);
            System.exit(1);
        }
        System.out.println("PASS: " + args[0] + " -> " + System.getProperty(key));
        System.exit(0);
    }
}
