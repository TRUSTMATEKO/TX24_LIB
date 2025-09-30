package kr.tx24.lib.lang;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * 
 */
public class NetUtils {

	public NetUtils() {	
	}
	
	
	public String getRemoteIp(Socket socket) {
		return socket.getInetAddress().toString();
	}
	
	
	/**
	 * 특정한 길이열을 지정하지 않고 전체를 수신
	 * 네트워크에 문제가 발생 할 수도 있을 경우 사용하며. 기본값으로 지정되어 있다.
	 * Socket 및 Stream 은 close되지 않는다.
	 * @return
	 * @throws IOException
	 */
	public byte[] stramRecv(InputStream inputStream) throws IOException {

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
        int bcount = 0;
        byte[] buf = new byte[2048];
        int read_retry_count = 0;
        while(true) {
			int n = inputStream.read(buf);
            if ( n > 0 ) { bcount += n; bout.write(buf,0,n); }
            else if (n == -1) break;
            else  { // n == 0
                if (++read_retry_count >= 5)
                  throw new IOException("inputstream-read-retry-count(5) exceed !");
            }
            if(inputStream.available() == 0){ break; }
        }
        bout.flush();
        byte[] res = bout.toByteArray();
        bout.close();
        return res;
	}
	
	/**
	 * 지정된 문자열만큼 데이터 수신.
	 * Socket 및 Stream 은 close되지 않는다.
	 * @param size
	 * @return
	 * @throws IOException
	 */
	public byte[] stramRecv(InputStream inputStream, int size) throws IOException{

		byte[] recv = new byte[size];

		boolean run = true;
		int recvedSize = 0;

		while(run){
			byte[] temp = new byte[size-recvedSize];
			int read = inputStream.read(temp);
			System.arraycopy(temp,0,recv,recvedSize,read);
			
			recvedSize += read;
			if(recvedSize >= size){
				run = false;
			}
		}
		return recv;
	}
	
	/**
	 * 전체 수신 
	 * Socket 및 Stream 은 close되지 않는다.
	 * @param size
	 * @return
	 * @throws IOException
	 */
	public byte[] stramRecvAll(InputStream inputStream) throws IOException{
		
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        int bcount = 0;
        byte[] buf = new byte[2048];
        int read_retry_count = 0;
        while(true) {
			int n = inputStream.read(buf);
            if ( n > 0 ) { bcount += n; bout.write(buf,0,n); }
            else if (n == -1) break;
            else  { // n == 0
                if (++read_retry_count >= 5)
                  throw new IOException("inputstream-read-retry-count(5) exceed !");
            }
            if(inputStream.available() == 0){ break; }
        }
        bout.flush();
        byte[] res = bout.toByteArray();
        bout.close();
        return res;

	}


}
