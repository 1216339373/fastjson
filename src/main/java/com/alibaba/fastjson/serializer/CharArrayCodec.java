package com.alibaba.fastjson.serializer;

import java.lang.reflect.Type;
import java.util.Collection;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;

//主要作用就是toCharArray转成字符数组char[]
public class CharArrayCodec implements ObjectDeserializer {

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        return (T) deserialze(parser);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            String val = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val.toCharArray();
        }
        
        if (lexer.token() == JSONToken.LITERAL_INT) {
            Number val = lexer.integerValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val.toString().toCharArray();
        }

        Object value = parser.parse();

        //把string转成 单个字符数组
        if (value instanceof  String) {
            return (T) ((String) value).toCharArray();
        }

        if (value instanceof Collection) {
            @SuppressWarnings("rawtypes")
			Collection<?> collection = (Collection) value;

            //设置标志量，是否可以转为char[]
            boolean accept = true;
            for (Object item : collection) {
                if (item instanceof String) {
                    int itemLength = ((String) item).length();
                    //转成字符数组，单个字符长度必须是1
                    if (itemLength != 1) {
                        accept = false;
                        break;
                    }
                }
            }

            if (!accept) {
                throw new JSONException("can not cast to char[]");
            }

            //根据集合大小创建char[]
            char[] chars = new char[collection.size()];
            int pos = 0;
            //迭代赋值给char[]
            for (Object item : collection) {
                chars[pos++] = ((String) item).charAt(0);
            }
            return (T) chars;
        }

        return value == null //
            ? null //
            : (T) JSON.toJSONString(value).toCharArray();
    }

    //返回String的类别代码
    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }
}
