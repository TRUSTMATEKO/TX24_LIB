package kr.tx24.lib.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class Sms implements java.io.Serializable{
	private static final long serialVersionUID = -6501429849224892431L;
	
	
	public String id				= "";	//관리용 ID
	public String table				= "";	//관리용 TABLE
	public String sender			= "";	//보내는 전화번호
	public List<String> receiver 	= new ArrayList<String>();
	public String message			= "";

	public Sms() {
		// TODO Auto-generated constructor stub
	}

}
