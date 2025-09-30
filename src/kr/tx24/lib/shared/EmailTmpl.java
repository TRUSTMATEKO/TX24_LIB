package kr.tx24.lib.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 
 */
public class EmailTmpl implements java.io.Serializable{
	private static final long serialVersionUID = 331276696360303191L;
	
	public String id		= "";						//관리용 ID
	public String table		= "";						//관리용 TABLE
	public String tmplId 	= "";						//Temeplate ID
	
	public List<String> to 	= new ArrayList<String>(); 	//받는 사람
	public List<String> cc 	= new ArrayList<String>(); 	//참조
	public LinkedHashMap<String,Object> contents = new LinkedHashMap<String,Object>();
	public String udf		= "";
	public List<String[]> names		= new ArrayList<String[]>();
	public List<byte[]> files		= new ArrayList<byte[]>();
	
	public EmailTmpl() {
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
