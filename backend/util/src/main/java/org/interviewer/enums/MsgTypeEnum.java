package org.interviewer.enums;

/**
 * Message type/action enum
 */
public enum MsgTypeEnum {
	
	CONNECT_INIT(0, "First (or reconnection) initialization connection"),
	WORDS(1, "Text emoji message"),
	IMAGE(2, "Image"),
	VOICE(3, "Voice"),
	VIDEO(4, "Video"),
	RESUME(5, "Resume"),
	JOB_OFFER(6, "Job offer"),

	INVITE(7, "Interview invitation"),
	MSG_INTERVIEW_CANCEL(71, "HR cancelled interview"),
	MSG_INTERVIEW_REFUSE(72, "Candidate refused interview"),
	MSG_INTERVIEW_ACCEPT(73, "Candidate accepted interview"),
	MSG_LOADING(911, "Message long wait loading effect"),

	SIGNED(8, "Message signed"),
	KEEPALIVE(9, "Client keepalive heartbeat"),
	heart(10, "Pull friends");
	
	public final Integer type;
	public final String content;
	
	MsgTypeEnum(Integer type, String content){
		this.type = type;
		this.content = content;
	}
	
	public Integer getType() {
		return type;
	}  
}
