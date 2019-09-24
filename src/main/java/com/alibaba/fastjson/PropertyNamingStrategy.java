package com.alibaba.fastjson;

/**
 * 属性名策略枚举值
 * @since 1.2.15
 */
public enum PropertyNamingStrategy {
                                    CamelCase, //驼峰标志
                                    PascalCase, //
                                    SnakeCase, //
                                    KebabCase;
//	属性名策略说明：
//	CamelCase策略，Java对象属性：personId，序列化后属性：personId
//	PascalCase策略，Java对象属性：personId，序列化后属性：PersonId
//	SnakeCase策略，Java对象属性：personId，序列化后属性：person_id
//	KebabCase策略，Java对象属性：personId，序列化后属性：person-id
	//根据名称进行转换
    public String translate(String propertyName) {
        switch (this) {
            case SnakeCase: {
                StringBuilder buf = new StringBuilder();
                for (int i = 0; i < propertyName.length(); ++i) {
                    char ch = propertyName.charAt(i);
                    if (ch >= 'A' && ch <= 'Z') {
                        char ch_ucase = (char) (ch + 32);
                        if (i > 0) {
                            buf.append('_');
                        }
                        buf.append(ch_ucase);
                    } else {
                        buf.append(ch);
                    }
                }
                return buf.toString();
            }
            case KebabCase: {
                StringBuilder buf = new StringBuilder();
                for (int i = 0; i < propertyName.length(); ++i) {
                    char ch = propertyName.charAt(i);
                    if (ch >= 'A' && ch <= 'Z') {
                        char ch_ucase = (char) (ch + 32);
                        if (i > 0) {
                            buf.append('-');
                        }
                        buf.append(ch_ucase);
                    } else {
                        buf.append(ch);
                    }
                }
                return buf.toString();
            }
            case PascalCase: {
                char ch = propertyName.charAt(0);
                if (ch >= 'a' && ch <= 'z') {
                    char[] chars = propertyName.toCharArray();
                    chars[0] -= 32;//小写转大写
                    return new String(chars);
                }

                return propertyName;
            }
            case CamelCase: {
                char ch = propertyName.charAt(0);
                //如果首字母大写
                if (ch >= 'A' && ch <= 'Z') {
                    char[] chars = propertyName.toCharArray();
                    chars[0] += 32;//大写转小写
                    return new String(chars);
                }

                return propertyName;
            }
            default:
                return propertyName;
        }
    }
}
