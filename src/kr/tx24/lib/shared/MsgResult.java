package kr.tx24.lib.shared;

/**
 * 
 */
public class MsgResult implements java.io.Serializable{
	private static final long serialVersionUID = 702789770925468631L;
	
	public String id			= "";	//관리아이디
	public String table			= "";	//관리테이블
	public String msgType		= "";	//EMAIL, SMS
	public String to 			= "";	//수신자
	public String subject		= "";	//제목
	public String contents		= "";	//내용
	public String code			= "";	//결과코드
	public String message		= "";	//결과메세지
	public long sentDate		= 0;	//송신일자 
	
	
	
	
	public MsgResult() {
		// TODO Auto-generated constructor stub
	}

}
