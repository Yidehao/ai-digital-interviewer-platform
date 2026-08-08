package org.interviewer.enums;

/**
 * Payment method enum
 */
public enum PayMethod {

	WEIXIN(1, "WeChat"),
	ALIPAY(2, "Alipay");

	public final Integer type;
	public final String value;

	PayMethod(Integer type, String value){
		this.type = type;
		this.value = value;
	}

}
