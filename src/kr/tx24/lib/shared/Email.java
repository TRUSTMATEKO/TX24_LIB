package kr.tx24.lib.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class Email implements java.io.Serializable{
	private static final long serialVersionUID = -7047374973778666074L;
	
	public String id		= "";						//관리용 ID
	public String table		= "";						//관리용 TABLE
	public String from 		= "";						//보내는사람 이메일
	public String fromName 	= "";						//보내는사람 이름
	public List<String> to 	= new ArrayList<String>(); 	//받는 사람
	public List<String> cc 	= new ArrayList<String>(); 	//참조
	public List<String> bcc = new ArrayList<String>();	//숨은참조
	public String subject	= "";
	public String contents	= "";
	public boolean isHtml 	= true;
	public List<String[]> names		= new ArrayList<String[]>();
	public List<byte[]> files		= new ArrayList<byte[]>();
	public String udf		= "";

	
	public Email() {
	}
	
	public void setAttatch(String name) throws Exception {
		
		Path attach = Paths.get(name);
		if(Files.exists(attach) && !Files.isDirectory(attach)) {
			if(names == null) {
				names = new ArrayList<String[]>();
				files = new ArrayList<byte[]>();
			}
			
			byte[] allBytes = Files.readAllBytes(attach);
			String[] fileDesc = new String[2];
			fileDesc[0] = attach.getFileName().toString();
			fileDesc[1] = Files.probeContentType(attach);
			names.add(fileDesc);
			files.add(allBytes);
			
		}else {
			throw new Exception("File is not exist : "+attach.toRealPath());
		}
		
	}
	
	

}
