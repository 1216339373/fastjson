package data;

/**
 * 转字符串工具类
 * 
 * 2019年9月19日 上午10:32:17
 */
public class ReprUtil
{
	//给字符串加双引号，如果null则返回字符串null
	public static String repr(String s)
	{
		if (s == null) return "null";
		return '"' + s + '"';
	}

	//对集合的字符串表达处理，打印用逗号分割，前后加[]
	public static String repr(Iterable<String> it)
	{
		StringBuilder buf = new StringBuilder();
		buf.append('[');
		String sep = "";
		for (String s : it) {
			buf.append(sep); sep = ", ";
			buf.append(repr(s));
		}
		buf.append(']');
		return buf.toString();
	}
}
