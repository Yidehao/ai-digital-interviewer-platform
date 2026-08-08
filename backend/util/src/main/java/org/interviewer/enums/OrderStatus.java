package org.interviewer.enums;

/**
 * Order status enum
 */
public enum OrderStatus {

	/*10: Pending payment
	20: Paid, pending shipment
	30: Shipped, pending receipt (auto-confirm after 7 days)
	40: Transaction successful (can rate at this time)
	50: Transaction closed (user cancelled when pending payment or long time unpaid, system auto-closed after identification)
	Return/refund, this branch flow is not supported, so not included*/

	WAIT_PAY(10, "Pending Payment"),
//	WAIT_DELIVER(20, "Paid, Pending Shipment"),
//	WAIT_RECEIVE(30, "Shipped, Pending Receipt"),
	SUCCESS(40, "Transaction Successful"),
	CLOSE(50, "Transaction Closed");

	public final Integer type;
	public final String value;

	OrderStatus(Integer type, String value){
		this.type = type;
		this.value = value;
	}

}
