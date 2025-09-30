package kr.tx24.lib.lang;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 50 BYTE MS949 전문 만들기
 * byte[] b = new ByteBufferUtils()
 *                  .create(50,"MS949")
 *                  .zpad("111",4)
 *                  .lpad("ABC",4)
 *                  .spad(2)
 *                  .put("TEST".getBytes())
 *                  .fill()
 *                  .getArray();
 */
public class ByteBufferUtils {
    private static final Logger logger = LoggerFactory.getLogger(ByteBufferUtils.class);
    private static final byte SPACE = (byte) 0x20;

    private Charset charset = Charset.defaultCharset();
    private ByteBuffer buffer = null;

    public ByteBufferUtils() {}

    public ByteBufferUtils(String charsetName) {
        this.charset = Charset.forName(charsetName);
    }

    public ByteBufferUtils(Charset charset) {
        this.charset = charset;
    }

    public ByteBufferUtils create(int capacity, String charsetName) {
        this.buffer = ByteBuffer.allocate(capacity);
        this.charset = Charset.forName(charsetName);
        return this;
    }

    public ByteBufferUtils create(int capacity, Charset charset) {
        this.buffer = ByteBuffer.allocate(capacity);
        this.charset = charset;
        return this;
    }

    public ByteBufferUtils position(int position) {
        this.buffer.position(position);
        return this;
    }

    public int position() {
        return this.buffer.position();
    }

    public ByteBufferUtils spad(int length, Object... labels) {
        byte[] add = new byte[length];
        Arrays.fill(add, SPACE);
        buffer.put(add);
        log(labels, add);
        return this;
    }

    public ByteBufferUtils zpad(Object o, int length, Object... labels) {
        long val = CommonUtils.parseLong(o);
        byte[] b = String.format("%0" + length + "d", val).getBytes(charset);
        buffer.put(b);
        log(labels, b);
        return this;
    }

    public ByteBufferUtils lpad(String s, int length, Object... labels) {
        byte[] b = s.getBytes(charset);
        if (b.length >= length) {
            buffer.put(b, 0, length);
        } else {
            buffer.put(b);
            spad(length - b.length);
        }
        log(labels, b);
        return this;
    }

    public ByteBufferUtils put(byte[] b, Object... labels) {
        buffer.put(b);
        log(labels, b);
        return this;
    }

    public ByteBufferUtils put(ByteBuffer buf, Object... labels) {
        buffer.put(buf);
        log(labels, new byte[buf.capacity()]);
        return this;
    }

    public ByteBufferUtils put(byte b, Object... labels) {
        buffer.put(b);
        log(labels, new byte[]{b});
        return this;
    }

    public ByteBufferUtils fill(Object... labels) {
        spad(buffer.capacity() - buffer.position());
        log(labels, null);
        return this;
    }

    public ByteBuffer get() {
        logInfo();
        return buffer;
    }

    public byte[] getArray() {
        logInfo();
        byte[] buf = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(buf);
        return buf;
    }

    public void get(byte[] dst) {
        buffer.rewind();
        buffer.get(dst);
    }

    private void log(Object[] labels, byte[] value) {
        if (SystemUtils.deepview()) {
            String v = value == null ? "" : CommonUtils.toString(value, charset.name());
            String label = (labels != null && labels.length > 0) ? labels[0].toString() : "";
            logger.info(">> {}{}: [{}] {}-{}", label, setSpace(label), v, buffer.position() - (value != null ? value.length : 0), buffer.position());
        }
    }

    private void logInfo() {
        if (SystemUtils.deepview()) {
            logger.info("created : capacity : {}, position : {}", buffer.capacity(), buffer.position());
        }
    }

    private String setSpace(String label) {
        final int fixed = 16;
        int len = label.codePoints()
                .map(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                           Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
                           Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.HANGUL_JAMO ? 2 : 1)
                .sum();
        return " ".repeat(Math.max(0, fixed - len));
    }

    public long getN(byte[] b, int offset, int length) {
        try {
            return Long.parseLong(getS(b, offset, length));
        } catch (Exception e) {
            return 0;
        }
    }

    public String getS(byte[] b, int offset, int length) {
        if (b == null) return "";
        int size = Math.min(length == 0 ? b.length : length, b.length - offset);
        String str = size > 0 ? toString(b, offset, size).trim() : "";
        logger.debug("[{}],{}", str, size);
        return str;
    }

    public String toString(byte[] b) {
        return toString(b, 0, b.length);
    }

    public String toString(byte[] b, int offset, int length) {
        try {
            return new String(b, offset, length, charset);
        } catch (Exception e) {
            return "";
        }
    }
}
