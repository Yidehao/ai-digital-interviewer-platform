package org.interviewer.utils;

import java.util.List;

/**
 * Used to return paginated Grid data format
 */
public class PagedGridResult {
	
	private int page;			// Current page number
	private long total;			// Total number of pages
	private long records;		// Total number of records
	private List<?> rows;		// Content displayed in each row

	public int getPage() {
		return page;
	}
	public void setPage(int page) {
		this.page = page;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public void setTotal(int total) {
		this.total = total;
	}
	public long getRecords() {
		return records;
	}
	public void setRecords(long records) {
		this.records = records;
	}
	public List<?> getRows() {
		return rows;
	}
	public void setRows(List<?> rows) {
		this.rows = rows;
	}
}
