package org.interviewer.enums;

/**
 * Payment center payment status enum: 10: Unpaid 20: Paid 30: Payment failed 40: Refunded
 */
public enum PaymentStatus {

	WAIT_PAY(10, "Unpaid"),
	PAID(20, "Paid"),
	PAY_FAILED(30, "Payment Failed"),
	SUCCESS(40, "Refunded");

	public final Integer type;
	public final String value;

	PaymentStatus(Integer type, String value){
		this.type = type;
		this.value = value;
	}

}
