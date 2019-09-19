package cn.com.tx.domain.pagination;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页工具
 * @author KongLei
 *
 * @param <T>
 */
public class Pagination<T> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5038839734218582220L;

	private int totalResult = 0;//总结果数

	private int totalPage = 1;//总页数

	private int pageIndex = 1;//当前页

	private int maxLength = 5;//每页的最大记录数，就是pagesize

	private int fromIndex = 0;//开始坐标

	private int toIndex = 0;//结束坐标

	private List<T> list = new ArrayList<T>();//结果集
	
	public Pagination(){
		
	}

	public void setTotalResult(int totalResult) {
		this.totalResult = totalResult;
	}

	public void setTotalPage(int totalPage) {
		this.totalPage = totalPage;
	}

	public void setPageIndex(int pageIndex) {
		this.pageIndex = pageIndex;
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	public void setFromIndex(int fromIndex) {
		this.fromIndex = fromIndex;
	}

	public void setToIndex(int toIndex) {
		this.toIndex = toIndex;
	}
	
	/**
	 * @param datas
	 *            the datas to set
	 */
	public void setList(List<T> list) {
		this.list = list;
	}

	public int getFromIndex() {
		return this.fromIndex;
	}

	public int getToIndex() {
		return this.toIndex;
	}
	/**
	 * @return the totalPage
	 */
	public int getTotalPage() {
		return totalPage;
	}

	/**
	 * @return the totalRecord
	 */
	public int getTotalResult() {
		return totalResult;
	}

	public int getMaxLength() {
		return maxLength;
	}
	

//---------校验上一页和下一页有没有超出范围----------------------
	//下一页
	public int getNextPage() {
		//如果当前页小于总页数，返回下一页
		if (this.pageIndex < this.totalPage) {
			return this.pageIndex + 1;
		} else {
			//如果当前页大于等于 总页数，则还是返回当前页
			return this.pageIndex;
		}
	}
	
	//上一页
	public int getPrevPage() {
		//如果当前页不是首页，则返回上一页
		if (this.pageIndex > 1) {
			return this.pageIndex - 1;
		}
		//否则返回当前页
		return this.pageIndex;
	}

	/**
	 * 获取当前页
	 * @return the currentPage
	 */
	public int getPageIndex() {
		return pageIndex;
	}

	/**
	 * 返回结果集  过滤空
	 * @return the list
	 */
	public List<T> getList() {
		if (list == null) {
			return new ArrayList<T>();
		}
		return new ArrayList<T>(list);
	}

	/**
	 * 初始化制作分页
	 * @param totalResult 
	 * @param pageIndex
	 * @param maxLength 每页大小pagesize
	 */
	public Pagination(int totalResult, int pageIndex, int maxLength) {
		//校验输入值
		if (maxLength > 0) {
			this.maxLength = maxLength;
		}
		if (totalResult > 0) {
			this.totalResult = totalResult;
		}
		if (pageIndex > 0) {
			this.pageIndex = pageIndex;
		}
		
		//先用总记录数除以pagesize，粗略获取一共多少页
		this.totalPage = this.totalResult / this.maxLength;
		//如果不能整除要多加一页
		if (this.totalResult % this.maxLength != 0) {
			this.totalPage = this.totalPage + 1;
		}
		//如果没有记录数 ，或者页数为0，设为1页
		if (this.totalPage == 0) {
			this.totalPage = 1;
		}
		//校验当前页不超出范围
		if (this.pageIndex > this.totalPage) {
			this.pageIndex = this.totalPage;
		}
		if (this.pageIndex <= 0) {
			this.pageIndex = 1;
		}
		
		//根据当前页计算出开始和结束坐标
		this.fromIndex = (this.pageIndex - 1) * maxLength;
		this.toIndex = this.fromIndex + maxLength;
		//校验开始和结束坐标不超出范围
		if (this.toIndex < 0) {
			this.toIndex = fromIndex;
		}
		if (this.toIndex > this.totalResult) {
			this.toIndex = this.totalResult;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Pagination:\r\n");
		sb.append("\tpageIndex\t" + this.pageIndex + "\r\n");
		sb.append("\ttotalPage\t" + this.totalPage + "\r\n");
		sb.append("\tmaxLength\t" + this.maxLength + "\r\n");
		sb.append("\ttotalResult\t" + this.totalResult + "\r\n");
		for (T t : list) {
			sb.append(t + "\r\n");
		}
		return sb.toString();
	}
	
}
