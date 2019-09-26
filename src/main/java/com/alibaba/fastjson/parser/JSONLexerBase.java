/*
 * Copyright 1999-2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.fastjson.parser;

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.Read;
import com.alibaba.fastjson.util.IOUtils;

import static com.alibaba.fastjson.parser.JSONToken.*;

/**
 * @author wenshao[szujobs@hotmail.com]
 * 抽象类
 * 扫描类，解析的核心类
 * Closeable接口就一个close方法
 */
public abstract class JSONLexerBase implements JSONLexer, Closeable {

	public int matchStat  = UNKNOWN;//0未知
    //token存放当前读取的字符的类型代号
    protected int token;
    /** 记录当前扫描字符位置 */
    protected int pos;
    protected int features;
    /** 当前有效字符 */
    protected char  currentCursor;
    /** 流(或者json字符串)中当前的位置，每次读取字符会递增 */
    protected int   bp;

    protected int   eofPos;

    //是否包含特殊字符
    protected boolean  hasSpecial;
    protected Calendar calendar  = null;
    protected TimeZone timeZone  = JSON.defaultTimeZone;//获取默认时区
    protected Locale   locale  = JSON.defaultLocale;//获取当地语言

    protected String  stringDefaultValue = null;
    
    protected static final long  MULTMIN_RADIX_TEN     = Long.MIN_VALUE / 10;
    protected static final int   INT_MULTMIN_RADIX_TEN = Integer.MIN_VALUE / 10;
    
    //digits作用是什么
    protected final static int[] digits                = new int[(int) 'f' + 1];// (int)'f'=102
    							//字段名称				"@type":"[]
    protected final static char[] typeFieldName = ("\"" + JSON.DEFAULT_TYPE_KEY + "\":\"").toCharArray();

    /**字符缓冲区 构造函数初始化大小512
     * A character buffer for literals.
     */
    protected char[]   sbuf;
    /** 字符缓冲区的索引，指向下一个可写
     *  字符的位置，也代表字符缓冲区字符数量
     */
    protected int  sbufPos;
    /**
     * number start position
     * 可以理解为 找到token时 token的首字符位置
     * 和bp不一样，这个不会递增，会在开始token前记录一次
     */
    protected int  numberStartPos;
    //赋值给缓冲区的数组
    private final static ThreadLocal<char[]> SBUF_LOCAL         = new ThreadLocal<char[]>();

    //这一段作用是什么
    static {
    	//如果是0~9 
        for (int i = '0'; i <= '9'; ++i) {
            digits[i] = i - '0';
        }
        //如果是a~f   0~5  +10
        for (int i = 'a'; i <= 'f'; ++i) {
            digits[i] = (i - 'a') + 10;
        }
        //如果是A~F   0~5  +10
        for (int i = 'A'; i <= 'F'; ++i) {
            digits[i] = (i - 'A') + 10;
        }
    }
    
    @Read
    public JSONLexerBase(int features){
        this.features = features;

        //初始化字符串字段
        if ((features & Feature.InitStringFieldAsEmpty.mask) != 0) {
            stringDefaultValue = "";
        }
        //从threadlocal里面获取缓冲区
        sbuf = SBUF_LOCAL.get();
        //初始化缓冲区512
        if (sbuf == null) {
            sbuf = new char[512];
        }
    }

    //读取下一个   字符类型存入token中
    @Read(desc="这个是最主要的方法，对比nextToken和next区别")
    public final void nextToken() {
    	//缓冲区字符的位置，也代表字符缓冲区字符数量
        sbufPos = 0;

        for (;;) {
        	//pos当前有效字符记录当前扫描位置
            pos = bp;

            //ch是当前有效字符，这里列出所有可能读取到的情况
            //识别到斜杠就是跳过注释
            if (currentCursor == '/') {
                skipComment();
                continue;
            }
            //"是字符
            if (currentCursor == '"') {
                scanString();
                return;
            }
            //逗号是读取下一个
            if (currentCursor == ',') {
                next();
                token = COMMA;//COMMA逗号
                return;
            }
            //0~9是数字
            if (currentCursor >= '0' && currentCursor <= '9') {
                scanNumber();
                return;
            }
            //负数
            if (currentCursor == '-') {
                scanNumber();
                return;
            }

            switch (currentCursor) {
            	//读取到单引号
                case '\'':
                	//不允许单引号，异常
                    if (!isEnabled(Feature.AllowSingleQuotes)) {
                        throw new JSONException("Feature.AllowSingleQuotes is false");
                    }
                    //允许扫描单引号
                    scanStringSingleQuote();
                    return;
                case ' ':
                case '\t':
                case '\b':
                case '\f':
                case '\n':
                case '\r':
                    next();
                    break;
                case 't': // true
                    scanTrue();
                    return;
                case 'f': // false
                    scanFalse();
                    return;
                case 'n': // new,null
                    scanNullOrNew();
                    return;
                case 'T':
                case 'N': // NULL
                case 'S':
                case 'u': // undefined
                    scanIdent();
                    return;
                //左圆括号    
                case '(':
                    next();
                    token = LPAREN;
                    return;
                //右圆括号
                case ')':
                    next();
                    token = RPAREN;
                    return;
                //左方括号    
                case '[':
                    next();
                    token = LBRACKET;
                    return;
                case ']':
                    next();
                    token = RBRACKET;
                    return;
                case '{':
                    next();
                    token = LBRACE;
                    return;
                case '}':
                    next();
                    token = RBRACE;
                    return;
                case ':':
                    next();
                    token = COLON;
                    return;
                case ';':
                    next();
                    token = SEMI;
                    return;
                case '.':
                    next();
                    token = DOT;
                    return;
                //正数    
                case '+':
                    next();
                    scanNumber();
                    return;
                //16进制    
                case 'x':
                    scanHex();
                    return;
                default:
                	//读取结束
                    if (isEOF()) { // JLS
                        if (token == EOF) {
                            throw new JSONException("EOF error");
                        }

                        token = EOF;
                        eofPos = pos = bp;
                    } else {
                        if (currentCursor <= 31 || currentCursor == 127) {
                            next();
                            break;
                        }

                        lexError("illegal.char", String.valueOf((int) currentCursor));
                        next();
                    }

                    return;
            }
        }

    }

    //这个方法主要是根据期望的字符expect，判定expect对应的token
    //根据期望的类型，赋值给token，让光标ch跳过去
    @Read
    public final void nextToken(int expect) {
        sbufPos = 0;

        for (;;) {
            switch (expect) {
                case JSONToken.LBRACE:
                    if (currentCursor == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }
                    if (currentCursor == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }
                    break;
                case JSONToken.COMMA:
                    if (currentCursor == ',') {
                        token = JSONToken.COMMA;
                        next();
                        return;
                    }

                    if (currentCursor == '}') {
                        token = JSONToken.RBRACE;
                        next();
                        return;
                    }

                    if (currentCursor == ']') {
                        token = JSONToken.RBRACKET;
                        next();
                        return;
                    }

                    if (currentCursor == EOI) {
                        token = JSONToken.EOF;
                        return;
                    }

                    if (currentCursor == 'n') {
                        scanNullOrNew(false);
                        return;
                    }
                    break;
                case JSONToken.LITERAL_INT:
                    if (currentCursor >= '0' && currentCursor <= '9') {
                        pos = bp;
                        scanNumber();
                        return;
                    }

                    if (currentCursor == '"') {
                        pos = bp;
                        scanString();
                        return;
                    }

                    if (currentCursor == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }

                    if (currentCursor == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }

                    break;
                case JSONToken.LITERAL_STRING:
                    if (currentCursor == '"') {
                        pos = bp;
                        scanString();
                        return;
                    }

                    if (currentCursor >= '0' && currentCursor <= '9') {
                        pos = bp;
                        scanNumber();
                        return;
                    }

                    if (currentCursor == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }

                    if (currentCursor == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }
                    break;
                case JSONToken.LBRACKET:
                    if (currentCursor == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }

                    if (currentCursor == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }
                    break;
                case JSONToken.RBRACKET:
                    if (currentCursor == ']') {
                        token = JSONToken.RBRACKET;
                        next();
                        return;
                    }
                case JSONToken.EOF:
                    if (currentCursor == EOI) {
                        token = JSONToken.EOF;
                        return;
                    }
                    break;
                case JSONToken.IDENTIFIER:
                    nextIdent();
                    return;
                default:
                    break;
            }

            //跳过空白符
            if (currentCursor == ' ' 
            		|| currentCursor == '\n' 
            		|| currentCursor == '\r' 
            		|| currentCursor == '\t' 
            		|| currentCursor == '\f' 
            		|| currentCursor == '\b') {
                next();
                continue;
            }

            nextToken();
            break;
        }
    }

    //下一个标识符
    public final void nextIdent() {
        while (isWhitespace(currentCursor)) {
            next();
        }
        if (currentCursor == '_' || currentCursor == '$' || Character.isLetter(currentCursor)) {
            scanIdent();
        } else {
            nextToken();
        }
    }
    
    //下一个冒号，这两个方法完全可以合并
    @Read
    public final void nextTokenWithColon() {
        nextTokenWithChar(':');
    }
    @Deprecated
    public final void nextTokenWithColon(int expect) {
        nextTokenWithChar(':');
    }
    
    //跳到下一个指定字符的类型  主要通过next和nexttoken
    @Read
    public final void nextTokenWithChar(char expect) {
        sbufPos = 0;

        for (;;) {
            if (currentCursor == expect) {
                next();
                nextToken();
                return;//这里return直接就是结束整个方法
            }

            //跳过这些空白符
            if (currentCursor == ' ' 
            		|| currentCursor == '\n' 
            		|| currentCursor == '\r' 
            		|| currentCursor == '\t' 
            		|| currentCursor == '\f' 
            		|| currentCursor == '\b') {
                next();
                continue;
            }

            throw new JSONException("not match " + expect + " - " + currentCursor + ", info : " + this.info());
        }
    }

    public final String scanSymbol(final SymbolTable symbolTable) {
        skipWhitespace();

        if (currentCursor == '"') {
            return scanSymbol(symbolTable, '"');
        }

        if (currentCursor == '\'') {
            if (!isEnabled(Feature.AllowSingleQuotes)) {
                throw new JSONException("syntax error");
            }

            return scanSymbol(symbolTable, '\'');
        }

        if (currentCursor == '}') {
            next();
            token = JSONToken.RBRACE;
            return null;
        }

        if (currentCursor == ',') {
            next();
            token = JSONToken.COMMA;
            return null;
        }

        if (currentCursor == EOI) {
            token = JSONToken.EOF;
            return null;
        }

        if (!isEnabled(Feature.AllowUnQuotedFieldNames)) {
            throw new JSONException("syntax error");
        }

        return scanSymbolUnQuoted(symbolTable);
    }

    // public abstract String scanSymbol(final SymbolTable symbolTable, final char quote);


    public final String scanSymbol(final SymbolTable symbolTable, final char quote) {
        int hash = 0;

        numberStartPos = bp;
        sbufPos = 0;
        boolean hasSpecial = false;
        char chLocal;
        for (;;) {
            chLocal = next();

            if (chLocal == quote) {
                break;
            }

            if (chLocal == EOI) {
                throw new JSONException("unclosed.str");
            }

            if (chLocal == '\\') {
                if (!hasSpecial) {
                    hasSpecial = true;

                    if (sbufPos >= sbuf.length) {
                        int newCapcity = sbuf.length * 2;
                        if (sbufPos > newCapcity) {
                            newCapcity = sbufPos;
                        }
                        char[] newsbuf = new char[newCapcity];
                        System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
                        sbuf = newsbuf;
                    }

                    // text.getChars(np + 1, np + 1 + sp, sbuf, 0);
                    // System.arraycopy(this.buf, np + 1, sbuf, 0, sp);
                    arrayCopy(numberStartPos + 1, sbuf, 0, sbufPos);
                }

                chLocal = next();

                switch (chLocal) {
                    case '0':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\0');
                        break;
                    case '1':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\1');
                        break;
                    case '2':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\2');
                        break;
                    case '3':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\3');
                        break;
                    case '4':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\4');
                        break;
                    case '5':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\5');
                        break;
                    case '6':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\6');
                        break;
                    case '7':
                        hash = 31 * hash + (int) chLocal;
                        putChar('\7');
                        break;
                    case 'b': // 8
                        hash = 31 * hash + (int) '\b';
                        putChar('\b');
                        break;
                    case 't': // 9
                        hash = 31 * hash + (int) '\t';
                        putChar('\t');
                        break;
                    case 'n': // 10
                        hash = 31 * hash + (int) '\n';
                        putChar('\n');
                        break;
                    case 'v': // 11
                        hash = 31 * hash + (int) '\u000B';
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                    case 'F':
                        hash = 31 * hash + (int) '\f';
                        putChar('\f');
                        break;
                    case 'r': // 13
                        hash = 31 * hash + (int) '\r';
                        putChar('\r');
                        break;
                    case '"': // 34
                        hash = 31 * hash + (int) '"';
                        putChar('"');
                        break;
                    case '\'': // 39
                        hash = 31 * hash + (int) '\'';
                        putChar('\'');
                        break;
                    case '/': // 47
                        hash = 31 * hash + (int) '/';
                        putChar('/');
                        break;
                    case '\\': // 92
                        hash = 31 * hash + (int) '\\';
                        putChar('\\');
                        break;
                    case 'x':
                        char x1 = currentCursor = next();
                        char x2 = currentCursor = next();

                        int x_val = digits[x1] * 16 + digits[x2];
                        char x_char = (char) x_val;
                        hash = 31 * hash + (int) x_char;
                        putChar(x_char);
                        break;
                    case 'u':
                        char c1 = chLocal = next();
                        char c2 = chLocal = next();
                        char c3 = chLocal = next();
                        char c4 = chLocal = next();
                        int val = Integer.parseInt(new String(new char[] { c1, c2, c3, c4 }), 16);
                        hash = 31 * hash + val;
                        putChar((char) val);
                        break;
                    default:
                        this.currentCursor = chLocal;
                        throw new JSONException("unclosed.str.lit");
                }
                continue;
            }

            hash = 31 * hash + chLocal;

            if (!hasSpecial) {
                sbufPos++;
                continue;
            }

            if (sbufPos == sbuf.length) {
                putChar(chLocal);
            } else {
                sbuf[sbufPos++] = chLocal;
            }
        }

        token = LITERAL_STRING;

        String value;
        if (!hasSpecial) {
            // return this.text.substring(np + 1, np + 1 + sp).intern();
            int offset;
            if (numberStartPos == -1) {
                offset = 0;
            } else {
                offset = numberStartPos + 1;
            }
            value = addSymbol(offset, sbufPos, hash, symbolTable);
        } else {
            value = symbolTable.addSymbol(sbuf, 0, sbufPos, hash);
        }

        sbufPos = 0;
        this.next();

        return value;
    }

    public final String scanSymbolUnQuoted(final SymbolTable symbolTable) {
        if (token == JSONToken.ERROR && pos == 0 && bp == 1) {
            bp = 0; // adjust
        }
        final boolean[] firstIdentifierFlags = IOUtils.firstIdentifierFlags;
        final char first = currentCursor;

        final boolean firstFlag = currentCursor >= firstIdentifierFlags.length || firstIdentifierFlags[first];
        if (!firstFlag) {
            throw new JSONException("illegal identifier : " + currentCursor //
                    + info());
        }

        final boolean[] identifierFlags = IOUtils.identifierFlags;

        int hash = first;

        numberStartPos = bp;
        sbufPos = 1;
        char chLocal;
        for (;;) {
            chLocal = next();

            if (chLocal < identifierFlags.length) {
                if (!identifierFlags[chLocal]) {
                    break;
                }
            }

            hash = 31 * hash + chLocal;

            sbufPos++;
            continue;
        }

        this.currentCursor = charAt(bp);
        token = JSONToken.IDENTIFIER;

        final int NULL_HASH = 3392903;
        if (sbufPos == 4 && hash == NULL_HASH && charAt(numberStartPos) == 'n' && charAt(numberStartPos + 1) == 'u' && charAt(numberStartPos + 2) == 'l'
                && charAt(numberStartPos + 3) == 'l') {
            return null;
        }

        // return text.substring(np, np + sp).intern();

        if (symbolTable == null) {
            return subString(numberStartPos, sbufPos);
        }

        return this.addSymbol(numberStartPos, sbufPos, hash, symbolTable);
        // return symbolTable.addSymbol(buf, np, sp, hash);
    }

    
    @Read(desc="主要是对特殊字符的处理")
    public final void scanString() {
    	/** 记录当前流中token的开始位置, np指向引号的索引 */
        numberStartPos = bp;
        hasSpecial = false;
        char ch;
        for (;;) {
        	/** 读取当前字符串的字符 */
            ch = next();
            /** 如果遇到字符串结束符"， 则结束 */
            if (ch == '\"') {
                break;
            }

            if (ch == EOI) {
            	/** 如果遇到了结束符EOI，但是没有遇到流的结尾，添加EOI结束符 */
                if (!isEOF()) {
                    putChar((char) EOI);
                    continue;
                }
                throw new JSONException("unclosed string : " + ch);
            }
            
            //读取到了反斜杠\
            if (ch == '\\') {
                if (!hasSpecial) {
                	/** 第一次遇到\认为是特殊符号 */
                    hasSpecial = true;

                    /** 如果buffer空间不够，执行2倍扩容 */
                    if (sbufPos >= sbuf.length) {
                        int newCapcity = sbuf.length * 2;
                        if (sbufPos > newCapcity) {
                        	//如果扩容了还不够，则直接拿sp的长度
                            newCapcity = sbufPos;
                        }
                        char[] newsbuf = new char[newCapcity];
                        System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
                        sbuf = newsbuf;
                    }

                    copyTo(numberStartPos + 1, sbufPos, sbuf);
                    // text.getChars(np + 1, np + 1 + sp, sbuf, 0);
                    // System.arraycopy(buf, np + 1, sbuf, 0, sp);
                }

                ch = next();
                /** 处理转译字符逻辑   这里为什么要这样处理呢?*/
                switch (ch) {
                    case '0':
                    	/** 空字符 */
                        putChar('\0');
                        break;
                    case '1':
                    	/** 标题开始 */
                        putChar('\1');
                        break;
                    case '2':
                    	/** 正文开始 */
                        putChar('\2');
                        break;
                    case '3':
                    	/** 正文结束 */
                        putChar('\3');
                        break;
                    case '4':
                    	/** 传输结束 */
                        putChar('\4');
                        break;
                    case '5':
                    	/** 请求 */
                        putChar('\5');
                        break;
                    case '6':
                    	/** 收到通知 */
                        putChar('\6');
                        break;
                    case '7':
                    	/** 响铃 */
                        putChar('\7');
                        break;
                    case 'b': // 8
                    	/** 退格 */
                        putChar('\b');
                        break;
                    case 't': // 9
                    	/** 水平制表符 */
                        putChar('\t');
                        break;
                    case 'n': // 10
                    	/** 换行键 */
                        putChar('\n');
                        break;
                    case 'v': // 11
                    	/** 垂直制表符 */
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                    	/** 换页键  不作处理，不放入缓冲*/
                    case 'F':
                    	/** 换页键 */
                        putChar('\f');
                        break;
                    case 'r': // 13
                    	/** 回车键 */
                        putChar('\r');
                        break;
                    case '"': // 34
                    	/** 双引号 */
                        putChar('"');
                        break;
                    case '\'': // 39
                    	/** 闭单引号 */
                        putChar('\'');
                        break;
                    case '/': // 47
                    	/** 斜杠 */
                        putChar('/');
                        break;
                    case '\\': // 92
                    	/** 反斜杠 */
                        putChar('\\');
                        break;
                    case 'x':
                    	/** 小写字母x, 标识一个字符 */
                        char x1 = next();
                        char x2 = next();

                        boolean hex1 = (x1 >= '0' && x1 <= '9')
                                || (x1 >= 'a' && x1 <= 'f')
                                || (x1 >= 'A' && x1 <= 'F');
                        boolean hex2 = (x2 >= '0' && x2 <= '9')
                                || (x2 >= 'a' && x2 <= 'f')
                                || (x2 >= 'A' && x2 <= 'F');
                        if (!hex1 || !hex2) {
                            throw new JSONException("invalid escape character \\x" + x1 + x2);
                        }
                        /** x1 左移4位 + x2 */
                        char x_char = (char) (digits[x1] * 16 + digits[x2]);
                        putChar(x_char);
                        break;
                    case 'u':
                    	/** 小写字母u, 标识一个字符 往下读4个*/
                        char u1 = next();
                        char u2 = next();
                        char u3 = next();
                        char u4 = next();
                        //从16进制转成10进制的数值
                        int val = Integer.parseInt(new String(new char[] { u1, u2, u3, u4 }), 16);
                        putChar((char) val);
                        break;
                    default:
                        this.currentCursor = ch;
                        throw new JSONException("unclosed string : " + ch);
                }
                continue;
            }
            /** 没有转译字符，递增buffer字符位置 */
            if (!hasSpecial) {
                sbufPos++;
                continue;
            }

            //需要扩容了
            if (sbufPos == sbuf.length) {
                putChar(ch);
            } else {
            	//不要扩容
                sbuf[sbufPos++] = ch;
            }
        }

        //读到的为string类型
        token = JSONToken.LITERAL_STRING;
        /** 自动预读下一个字符 */
        this.currentCursor = next();
    }
    
  //读取16进制
    @Read
    public final void scanHex() {
    	//必须是以x开头 否则不处理
        if (currentCursor != 'x') {
            throw new JSONException("illegal state. " + currentCursor);
        }
        next();
/** 十六进制x紧跟着单引号 */
/** @see com.alibaba.fastjson.serializer.SerializeWriter#writeHex(byte[]) */
        if (currentCursor != '\'') {
            throw new JSONException("illegal state. " + currentCursor);
        }

        numberStartPos = bp;
        /** 这里一次next, for循环也读一次next, 因为十六进制被写成2个字节的单字符 */
        next();

        if (currentCursor == '\'') {
            next();
            token = JSONToken.HEX;
            return;
        }

        //for(;;)也可以
        for (@SuppressWarnings("unused")int i = 0;;++i) {
            char ch = next();
            //读到0~9 A~F 正常，继续往下读
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F')) {
                sbufPos++;
                continue;
            } else if (ch == '\'') {
            	//如果读到单引号，结束跳出
                sbufPos++;
                next();
                break;
            } else {
                throw new JSONException("illegal state. " + ch);
            }
        }
        token = JSONToken.HEX;
    }

    //扫描数字
    @Read(desc="注意判断科学计数法")
    public final void scanNumber() {
    	/** 记录当前流中token的开始位置, np指向数字字符索引 */
        numberStartPos = bp;

        //读到负号-
        if (currentCursor == '-') {
            sbufPos++;
            next();
        }

        //读到0~9，继续直到不是数字跳出
        for (;;) {
            if (currentCursor >= '0' && currentCursor <= '9') {
                sbufPos++;
            } else {
                break;
            }
            next();
        }

        boolean isDouble = false;
        //如果读到.小数点，表示小数
        if (currentCursor == '.') {
            sbufPos++;
            next();
            //浮点数
            isDouble = true;
            //继续处理小数点后面的数字
            for (;;) {
                if (currentCursor >= '0' && currentCursor <= '9') {
                    sbufPos++;
                } else {
                    break;
                }
                next();
            }
        }

        /** 继续读取数字后面的类型 */
        if (currentCursor == 'L') {
            sbufPos++;
            next();
        } else if (currentCursor == 'S') {
            sbufPos++;
            next();
        } else if (currentCursor == 'B') {
            sbufPos++;
            next();
        } else if (currentCursor == 'F') {
            sbufPos++;
            next();
            isDouble = true;
        } else if (currentCursor == 'D') {
            sbufPos++;
            next();
            isDouble = true;
        } else if (currentCursor == 'e' || currentCursor == 'E') {
        	//E是科学计数法
            sbufPos++;
            next();

            if (currentCursor == '+' || currentCursor == '-') {
                sbufPos++;
                next();
            }

            for (;;) {
                if (currentCursor >= '0' && currentCursor <= '9') {
                    sbufPos++;
                } else {
                    break;
                }
                next();
            }

            if (currentCursor == 'D' || currentCursor == 'F') {
                sbufPos++;
                next();
            }

            isDouble = true;
        }

        //根据isDouble判断是小数还是整数
        if (isDouble) {
            token = JSONToken.LITERAL_FLOAT;
        } else {
            token = JSONToken.LITERAL_INT;
        }
    }
    
    public String scanFieldString(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return stringDefaultValue();
        }

        // int index = bp + fieldName.length;

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '"') {
            matchStat = NOT_MATCH;

            return stringDefaultValue();
        }

        final String strVal;
        {
            int startIndex = bp + fieldName.length + 1;
            int endIndex = indexOf('"', startIndex);
            if (endIndex == -1) {
                throw new JSONException("unclosed str");
            }

            int startIndex2 = bp + fieldName.length + 1; // must re compute
            String stringVal = subString(startIndex2, endIndex - startIndex2);
            if (stringVal.indexOf('\\') != -1) {
                for (;;) {
                    int slashCount = 0;
                    for (int i = endIndex - 1; i >= 0; --i) {
                        if (charAt(i) == '\\') {
                            slashCount++;
                        } else {
                            break;
                        }
                    }
                    if (slashCount % 2 == 0) {
                        break;
                    }
                    endIndex = indexOf('"', endIndex + 1);
                }

                int chars_len = endIndex - (bp + fieldName.length + 1);
                char[] chars = sub_chars( bp + fieldName.length + 1, chars_len);

                stringVal = readString(chars, chars_len);
            }

            offset += (endIndex - (bp + fieldName.length + 1) + 1);
            chLocal = charAt(bp + (offset++));
            strVal = stringVal;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return strVal;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return stringDefaultValue();
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return stringDefaultValue();
        }

        return strVal;
    }

    public String scanString(char expectNextChar) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        if (chLocal == 'n') {
            if (charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
                offset += 3;
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }

            if (chLocal == expectNextChar) {
                bp += offset;
                this.currentCursor = this.charAt(bp);
                matchStat = VALUE;
                return null;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        }

        final String strVal;
        for (;;) {
            if (chLocal == '"') {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                if (endIndex == -1) {
                    throw new JSONException("unclosed str");
                }

                String stringVal = subString(bp + offset, endIndex - startIndex);
                if (stringVal.indexOf('\\') != -1) {
                    for (; ; ) {
                        int slashCount = 0;
                        for (int i = endIndex - 1; i >= 0; --i) {
                            if (charAt(i) == '\\') {
                                slashCount++;
                            } else {
                                break;
                            }
                        }
                        if (slashCount % 2 == 0) {
                            break;
                        }
                        endIndex = indexOf('"', endIndex + 1);
                    }

                    int chars_len = endIndex - startIndex;
                    char[] chars = sub_chars(bp + 1, chars_len);

                    stringVal = readString(chars, chars_len);
                }

                offset += (endIndex - startIndex + 1);
                chLocal = charAt(bp + (offset++));
                strVal = stringVal;
                break;
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(bp + (offset++));
                continue;
            } else {
                matchStat = NOT_MATCH;

                return stringDefaultValue();
            }
        }

        for (;;) {
            if (chLocal == expectNextChar) {
                bp += offset;
                this.currentCursor = charAt(bp);
                matchStat = VALUE;
                token = JSONToken.COMMA;
                return strVal;
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(bp + (offset++));
                continue;
            } else {
                matchStat = NOT_MATCH;
                return strVal;
            }
        }
    }

    public long scanFieldSymbol(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '"') {
            matchStat = NOT_MATCH;
            return 0;
        }

        long hash = 0xcbf29ce484222325L;
        for (;;) {
            chLocal = charAt(bp + (offset++));
            if (chLocal == '\"') {
                chLocal = charAt(bp + (offset++));
                break;
            }

            hash ^= chLocal;
            hash *= 0x100000001b3L;

            if (chLocal == '\\') {
                matchStat = NOT_MATCH;
                return 0;
            }
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return hash;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return hash;
    }

    public long scanEnumSymbol(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '"') {
            matchStat = NOT_MATCH;
            return 0;
        }

        long hash = 0xcbf29ce484222325L;
        for (;;) {
            chLocal = charAt(bp + (offset++));
            if (chLocal == '\"') {
                chLocal = charAt(bp + (offset++));
                break;
            }

            hash ^= ((chLocal >= 'A' && chLocal <= 'Z') ? (chLocal + 32) : chLocal);
            hash *= 0x100000001b3L;

            if (chLocal == '\\') {
                matchStat = NOT_MATCH;
                return 0;
            }
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return hash;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return hash;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Enum<?> scanEnum(Class<?> enumClass, final SymbolTable symbolTable, char serperator) {
        String name = scanSymbolWithSeperator(symbolTable, serperator);
        if (name == null) {
            return null;
        }
        return Enum.valueOf((Class<? extends Enum>) enumClass, name);
    }

    public String scanSymbolWithSeperator(final SymbolTable symbolTable, char serperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        if (chLocal == 'n') {
            if (charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
                offset += 3;
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }

            if (chLocal == serperator) {
                bp += offset;
                this.currentCursor = this.charAt(bp);
                matchStat = VALUE;
                return null;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        }

        if (chLocal != '"') {
            matchStat = NOT_MATCH;
            return null;
        }

        String strVal;
        // int start = index;
        int hash = 0;
        for (;;) {
            chLocal = charAt(bp + (offset++));
            if (chLocal == '\"') {
                // bp = index;
                // this.ch = chLocal = charAt(bp);
                int start = bp + 0 + 1;
                int len = bp + offset - start - 1;
                strVal = addSymbol(start, len, hash, symbolTable);
                chLocal = charAt(bp + (offset++));
                break;
            }

            hash = 31 * hash + chLocal;

            if (chLocal == '\\') {
                matchStat = NOT_MATCH;
                return null;
            }
        }

        for (;;) {
            if (chLocal == serperator) {
                bp += offset;
                this.currentCursor = this.charAt(bp);
                matchStat = VALUE;
                return strVal;
            } else {
                if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + (offset++));
                    continue;
                }

                matchStat = NOT_MATCH;
                return strVal;
            }
        }
    }
    
    
    public final Number integerValue() throws NumberFormatException {
        long result = 0;
        boolean negative = false;
        if (numberStartPos == -1) {
            numberStartPos = 0;
        }
        int i = numberStartPos, max = numberStartPos + sbufPos;
        long limit;
        long multmin;
        int digit;

        char type = ' ';

        switch (charAt(max - 1)) {
            case 'L':
                max--;
                type = 'L';
                break;
            case 'S':
                max--;
                type = 'S';
                break;
            case 'B':
                max--;
                type = 'B';
                break;
            default:
                break;
        }

        if (charAt(numberStartPos) == '-') {
            negative = true;
            limit = Long.MIN_VALUE;
            i++;
        } else {
            limit = -Long.MAX_VALUE;
        }
        multmin = MULTMIN_RADIX_TEN;
        if (i < max) {
            digit = charAt(i++) - '0';
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = charAt(i++) - '0';
            if (result < multmin) {
                return new BigInteger(numberString());
            }
            result *= 10;
            if (result < limit + digit) {
                return new BigInteger(numberString());
            }
            result -= digit;
        }

        if (negative) {
            if (i > numberStartPos + 1) {
                if (result >= Integer.MIN_VALUE && type != 'L') {
                    if (type == 'S') {
                        return (short) result;
                    }

                    if (type == 'B') {
                        return (byte) result;
                    }

                    return (int) result;
                }
                return result;
            } else { /* Only got "-" */
                throw new NumberFormatException(numberString());
            }
        } else {
            result = -result;
            if (result <= Integer.MAX_VALUE && type != 'L') {
                if (type == 'S') {
                    return (short) result;
                }

                if (type == 'B') {
                    return (byte) result;
                }

                return (int) result;
            }
            return result;
        }
    }


    public float floatValue() {
        String strVal = numberString();
        float floatValue = Float.parseFloat(strVal);
        if (floatValue == 0 || floatValue == Float.POSITIVE_INFINITY) {
            char c0 = strVal.charAt(0);
            if (c0 > '0' && c0 <= '9') {
                throw new JSONException("float overflow : " + strVal);
            }
        }
        return floatValue;
    }
    public final int intValue() {
        if (numberStartPos == -1) {
            numberStartPos = 0;
        }

        int result = 0;
        boolean negative = false;
        int i = numberStartPos, max = numberStartPos + sbufPos;
        int limit;
        int digit;

        if (charAt(numberStartPos) == '-') {
            negative = true;
            limit = Integer.MIN_VALUE;
            i++;
        } else {
            limit = -Integer.MAX_VALUE;
        }
        long multmin = INT_MULTMIN_RADIX_TEN;
        if (i < max) {
            digit = charAt(i++) - '0';
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            char chLocal = charAt(i++);

            if (chLocal == 'L' || chLocal == 'S' || chLocal == 'B') {
                break;
            }

            digit = chLocal - '0';

            if (result < multmin) {
                throw new NumberFormatException(numberString());
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(numberString());
            }
            result -= digit;
        }

        if (negative) {
            if (i > numberStartPos + 1) {
                return result;
            } else { /* Only got "-" */
                throw new NumberFormatException(numberString());
            }
        } else {
            return -result;
        }
    }
    
    public final long longValue() throws NumberFormatException {
        long result = 0;
        boolean negative = false;
        long limit;
        int digit;

        if (numberStartPos == -1) {
            numberStartPos = 0;
        }

        int i = numberStartPos, max = numberStartPos + sbufPos;

        if (charAt(numberStartPos) == '-') {
            negative = true;
            limit = Long.MIN_VALUE;
            i++;
        } else {
            limit = -Long.MAX_VALUE;
        }
        long multmin = MULTMIN_RADIX_TEN;
        if (i < max) {
            digit = charAt(i++) - '0';
            result = -digit;
        }
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            char chLocal = charAt(i++);

            if (chLocal == 'L' || chLocal == 'S' || chLocal == 'B') {
                break;
            }

            digit = chLocal - '0';
            if (result < multmin) {
                throw new NumberFormatException(numberString());
            }
            result *= 10;
            if (result < limit + digit) {
                throw new NumberFormatException(numberString());
            }
            result -= digit;
        }

        if (negative) {
            if (i > numberStartPos + 1) {
                return result;
            } else { /* Only got "-" */
                throw new NumberFormatException(numberString());
            }
        } else {
            return -result;
        }
    }

    public final Number decimalValue(boolean decimal) {
        char chLocal = charAt(numberStartPos + sbufPos - 1);
        try {
            if (chLocal == 'F') {
                return Float.parseFloat(numberString());
            }

            if (chLocal == 'D') {
                return Double.parseDouble(numberString());
            }

            if (decimal) {
                return decimalValue();
            } else {
                return doubleValue();
            }
        } catch (NumberFormatException ex) {
            throw new JSONException(ex.getMessage() + ", " + info());
        }
    }
    
    public double doubleValue() {
        return Double.parseDouble(numberString());
    }

    
    public void config(Feature feature, boolean state) {
        features = Feature.config(features, feature, state);

        if ((features & Feature.InitStringFieldAsEmpty.mask) != 0) {
            stringDefaultValue = "";
        }
    }

    public final boolean isEnabled(Feature feature) {
        return isEnabled(feature.mask);
    }

    public final boolean isEnabled(int feature) {
        return (this.features & feature) != 0;
    }
    

    public final void resetStringPosition() {
        this.sbufPos = 0;
    }

    public void close() {
        if (sbuf.length <= 1024 * 8) {
            SBUF_LOCAL.set(sbuf);
        }
        this.sbuf = null;
    }

    public final boolean isRef() {
        if (sbufPos != 4) {
            return false;
        }

        return charAt(numberStartPos + 1) == '$' //
                && charAt(numberStartPos + 2) == 'r' //
                && charAt(numberStartPos + 3) == 'e' //
                && charAt(numberStartPos + 4) == 'f';
    }


   

    public final boolean matchField(char[] fieldName) {
        for (;;) {
            if (!charArrayCompare(fieldName)) {
                if (isWhitespace(currentCursor)) {
                    next();
                    continue;
                }
                return false;
            } else {
                break;
            }
        }

        bp = bp + fieldName.length;
        currentCursor = charAt(bp);

        if (currentCursor == '{') {
            next();
            token = JSONToken.LBRACE;
        } else if (currentCursor == '[') {
            next();
            token = JSONToken.LBRACKET;
        } else if (currentCursor == 'S' && charAt(bp + 1) == 'e' && charAt(bp + 2) == 't' && charAt(bp + 3) == '[') {
            bp += 3;
            currentCursor = charAt(bp);
            token = JSONToken.SET;
        } else {
            nextToken();
        }

        return true;
    }

    

    public abstract int indexOf(char ch, int startIndex);

    public abstract String addSymbol(int offset, int len, int hash, final SymbolTable symbolTable);


    public Collection<String> newCollectionByType(Class<?> type){
        if (type.isAssignableFrom(HashSet.class)) {
            HashSet<String> list = new HashSet<String>();
            return list;
        } else if (type.isAssignableFrom(ArrayList.class)) {
            ArrayList<String> list2 = new ArrayList<String>();
            return list2;
        } else {
            try {
                @SuppressWarnings("unchecked")
				Collection<String> list = (Collection<String>) type.newInstance();
                return list;
            } catch (Exception e) {
                throw new JSONException(e.getMessage(), e);
            }
        }
    }
    public final int scanType(String type) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(typeFieldName)) {
            return NOT_MATCH_NAME;
        }

        int bpLocal = this.bp + typeFieldName.length;

        final int typeLength = type.length();
        for (int i = 0; i < typeLength; ++i) {
            if (type.charAt(i) != charAt(bpLocal + i)) {
                return NOT_MATCH;
            }
        }
        bpLocal += typeLength;
        if (charAt(bpLocal) != '"') {
            return NOT_MATCH;
        }

        this.currentCursor = charAt(++bpLocal);

        if (currentCursor == ',') {
            this.currentCursor = charAt(++bpLocal);
            this.bp = bpLocal;
            token = JSONToken.COMMA;
            return VALUE;
        } else if (currentCursor == '}') {
            currentCursor = charAt(++bpLocal);
            if (currentCursor == ',') {
                token = JSONToken.COMMA;
                this.currentCursor = charAt(++bpLocal);
            } else if (currentCursor == ']') {
                token = JSONToken.RBRACKET;
                this.currentCursor = charAt(++bpLocal);
            } else if (currentCursor == '}') {
                token = JSONToken.RBRACE;
                this.currentCursor = charAt(++bpLocal);
            } else if (currentCursor == EOI) {
                token = JSONToken.EOF;
            } else {
                return NOT_MATCH;
            }
            matchStat = END;
        }

        this.bp = bpLocal;
        return matchStat;
    }

    public Collection<String> scanFieldStringArray(char[] fieldName, Class<?> type) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        Collection<String> list = newCollectionByType(type);

//        if (type.isAssignableFrom(HashSet.class)) {
//            list = new HashSet<String>();
//        } else if (type.isAssignableFrom(ArrayList.class)) {
//            list = new ArrayList<String>();
//        } else {
//            try {
//                list = (Collection<String>) type.newInstance();
//            } catch (Exception e) {
//                throw new JSONException(e.getMessage(), e);
//            }
//        }

        // int index = bp + fieldName.length;

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '[') {
            matchStat = NOT_MATCH;
            return null;
        }

        chLocal = charAt(bp + (offset++));

        for (;;) {
            // int start = index;
            if (chLocal == '"') {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                if (endIndex == -1) {
                    throw new JSONException("unclosed str");
                }

                int startIndex2 = bp + offset; // must re compute
                String stringVal = subString(startIndex2, endIndex - startIndex2);
                if (stringVal.indexOf('\\') != -1) {
                    for (;;) {
                        int slashCount = 0;
                        for (int i = endIndex - 1; i >= 0; --i) {
                            if (charAt(i) == '\\') {
                                slashCount++;
                            } else {
                                break;
                            }
                        }
                        if (slashCount % 2 == 0) {
                            break;
                        }
                        endIndex = indexOf('"', endIndex + 1);
                    }

                    int chars_len = endIndex - (bp + offset);
                    char[] chars = sub_chars(bp + offset, chars_len);

                    stringVal = readString(chars, chars_len);
                }

                offset += (endIndex - (bp + offset) + 1);
                chLocal = charAt(bp + (offset++));

                list.add(stringVal);
            } else if (chLocal == 'n' //
                    && charAt(bp + offset) == 'u' //
                    && charAt(bp + offset + 1) == 'l' //
                    && charAt(bp + offset + 2) == 'l') {
                offset += 3;
                chLocal = charAt(bp + (offset++));
                list.add(null);
            } else if (chLocal == ']' && list.size() == 0) {
                chLocal = charAt(bp + (offset++));
                break;
            } else {
                throw new JSONException("illega str");
            }

            if (chLocal == ',') {
                chLocal = charAt(bp + (offset++));
                continue;
            }

            if (chLocal == ']') {
                chLocal = charAt(bp + (offset++));
                break;
            }

            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return list;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                bp += (offset - 1);
                token = JSONToken.EOF;
                this.currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return list;
    }

    public void scanStringArray(Collection<String> list, char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        if (chLocal == 'n'
                && charAt(bp + offset) == 'u'
                && charAt(bp + offset + 1) == 'l'
                && charAt(bp + offset + 2) == 'l'
                && charAt(bp + offset + 3) == seperator
        ) {
            bp += 5;
            currentCursor = charAt(bp);
            matchStat = VALUE_NULL;
            return;
        }

        if (chLocal != '[') {
            matchStat = NOT_MATCH;
            return;
        }

        chLocal = charAt(bp + (offset++));

        for (;;) {
            if (chLocal == 'n' //
                    && charAt(bp + offset) == 'u' //
                    && charAt(bp + offset + 1) == 'l' //
                    && charAt(bp + offset + 2) == 'l') {
                offset += 3;
                chLocal = charAt(bp + (offset++));
                list.add(null);
            } else if (chLocal == ']' && list.size() == 0) {
                chLocal = charAt(bp + (offset++));
                break;
            } else if (chLocal != '"') {
                matchStat = NOT_MATCH;
                return;
            } else {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                if (endIndex == -1) {
                    throw new JSONException("unclosed str");
                }

                String stringVal = subString(bp + offset, endIndex - startIndex);
                if (stringVal.indexOf('\\') != -1) {
                    for (;;) {
                        int slashCount = 0;
                        for (int i = endIndex - 1; i >= 0; --i) {
                            if (charAt(i) == '\\') {
                                slashCount++;
                            } else {
                                break;
                            }
                        }
                        if (slashCount % 2 == 0) {
                            break;
                        }
                        endIndex = indexOf('"', endIndex + 1);
                    }

                    int chars_len = endIndex - startIndex;
                    char[] chars = sub_chars(bp + offset, chars_len);

                    stringVal = readString(chars, chars_len);
                }

                offset += (endIndex - (bp + offset) + 1);
                chLocal = charAt(bp + (offset++));
                list.add(stringVal);
            }

            if (chLocal == ',') {
                chLocal = charAt(bp + (offset++));
                continue;
            }

            if (chLocal == ']') {
                chLocal = charAt(bp + (offset++));
                break;
            }

            matchStat = NOT_MATCH;
            return;
        }

        if (chLocal == seperator) {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return;
        } else {
            matchStat = NOT_MATCH;
            return;
        }
    }

    public int scanFieldInt(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        int value;
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                } else if (chLocal == '.') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }
            if (value < 0 //
                    || offset > 11 + 3 + fieldName.length) {
                if (value != Integer.MIN_VALUE //
                        || offset != 17 //
                        || !negative) {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return negative ? -value : value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return negative ? -value : value;
    }

    public final int[] scanFieldIntArray(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '[') {
            matchStat = NOT_MATCH_NAME;
            return null;
        }
        chLocal = charAt(bp + (offset++));

        int[] array = new int[16];
        int arrayIndex = 0;

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
        } else {
            for (;;) {
                boolean nagative = false;
                if (chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                    nagative = true;
                }
                if (chLocal >= '0' && chLocal <= '9') {
                    int value = chLocal - '0';
                    for (; ; ) {
                        chLocal = charAt(bp + (offset++));

                        if (chLocal >= '0' && chLocal <= '9') {
                            value = value * 10 + (chLocal - '0');
                        } else {
                            break;
                        }
                    }

                    if (arrayIndex >= array.length) {
                        int[] tmp = new int[array.length * 3 / 2];
                        System.arraycopy(array, 0, tmp, 0, arrayIndex);
                        array = tmp;
                    }
                    array[arrayIndex++] = nagative ? -value : value;

                    if (chLocal == ',') {
                        chLocal = charAt(bp + (offset++));
                    } else if (chLocal == ']') {
                        chLocal = charAt(bp + (offset++));
                        break;
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            }
        }


        if (arrayIndex != array.length) {
            int[] tmp = new int[arrayIndex];
            System.arraycopy(array, 0, tmp, 0, arrayIndex);
            array = tmp;
        }

        if (chLocal == ',') {
            bp += (offset - 1);
            this.next();
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return array;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == EOI) {
                bp += (offset - 1);
                token = JSONToken.EOF;
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return array;
    }

    public boolean scanBoolean(char expectNext) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        boolean value = false;
        if (chLocal == 't') {
            if (charAt(bp + offset) == 'r' //
                    && charAt(bp + offset + 1) == 'u' //
                    && charAt(bp + offset + 2) == 'e') {
                offset += 3;
                chLocal = charAt(bp + (offset++));
                value = true;
            } else {
                matchStat = NOT_MATCH;
                return false;
            }
        } else if (chLocal == 'f') {
            if (charAt(bp + offset) == 'a' //
                    && charAt(bp + offset + 1) == 'l' //
                    && charAt(bp + offset + 2) == 's' //
                    && charAt(bp + offset + 3) == 'e') {
                offset += 4;
                chLocal = charAt(bp + (offset++));
                value = false;
            } else {
                matchStat = NOT_MATCH;
                return false;
            }
        } else if (chLocal == '1') {
            chLocal = charAt(bp + (offset++));
            value = true;
        } else if (chLocal == '0') {
            chLocal = charAt(bp + (offset++));
            value = false;
        }

        for (;;) {
            if (chLocal == expectNext) {
                bp += offset;
                this.currentCursor = this.charAt(bp);
                matchStat = VALUE;
                return value;
            } else {
                if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + (offset++));
                    continue;
                }
                matchStat = NOT_MATCH;
                return value;
            }
        }
    }

    public int scanInt(char expectNext) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        /** 取整数第一个字符判断是否是引号 */
        /** 如果是双引号，取第一个数字字符 */
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        final boolean negative = chLocal == '-';
        if (negative) {
        	/** 如果是负数，继续取下一个字符 */
            chLocal = charAt(bp + (offset++));
        }

        int value;
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {/** 循环将字符转换成数字 */
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                } else if (chLocal == '.') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }
            if (value < 0) {
                matchStat = NOT_MATCH;
                return 0;
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
        	/** 匹配到null */
        	matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            /** 读取null后面的一个字符 */
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
            	/** 如果读取null后面有逗号，认为结束 */
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
                } else if (isWhitespace(chLocal)) {/** 忽略空白字符 */
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        for (;;) {
        	/** 根据期望字符用于结束匹配 */
            if (chLocal == expectNext) {
                bp += offset;
                this.currentCursor = this.charAt(bp);
                matchStat = VALUE;
                token = JSONToken.COMMA;
                return negative ? -value : value;
            } else {
            	/** 忽略空白字符 */
                if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + (offset++));
                    continue;
                }
                matchStat = NOT_MATCH;
                return negative ? -value : value;
            }
        }
    }

    public boolean scanFieldBoolean(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return false;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        boolean value;
        if (chLocal == 't') {
            if (charAt(bp + (offset++)) != 'r') {
                matchStat = NOT_MATCH;
                return false;
            }
            if (charAt(bp + (offset++)) != 'u') {
                matchStat = NOT_MATCH;
                return false;
            }
            if (charAt(bp + (offset++)) != 'e') {
                matchStat = NOT_MATCH;
                return false;
            }

            value = true;
        } else if (chLocal == 'f') {
            if (charAt(bp + (offset++)) != 'a') {
                matchStat = NOT_MATCH;
                return false;
            }
            if (charAt(bp + (offset++)) != 'l') {
                matchStat = NOT_MATCH;
                return false;
            }
            if (charAt(bp + (offset++)) != 's') {
                matchStat = NOT_MATCH;
                return false;
            }
            if (charAt(bp + (offset++)) != 'e') {
                matchStat = NOT_MATCH;
                return false;
            }

            value = false;
        } else {
            matchStat = NOT_MATCH;
            return false;
        }

        chLocal = charAt(bp + offset++);
        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;

            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return false;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return false;
        }

        return value;
    }

    public long scanFieldLong(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        boolean negative = false;
        if (chLocal == '-') {
            chLocal = charAt(bp + (offset++));
            negative = true;
        }

        long value;
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                } else if (chLocal == '.') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }

            boolean valid = offset - fieldName.length < 21
                    && (value >= 0 || (value == -9223372036854775808L && negative));
            if (!valid) {
                matchStat = NOT_MATCH;
                return 0;
            }
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return negative ? -value : value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return negative ? -value : value;
    }

    public long scanLong(char expectNextChar) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        final boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        long value;
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                } else if (chLocal == '.') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }
            boolean valid = value >= 0 || (value == -9223372036854775808L && negative);
            if (!valid) {
                String val = subString(bp, offset - 1);
                throw new NumberFormatException(val);
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (quote) {
            if (chLocal != '"') {
                matchStat = NOT_MATCH;
                return 0;
            } else {
                chLocal = charAt(bp + (offset++));
            }
        }

        for (;;) {
            if (chLocal == expectNextChar) {
                bp += offset;
                this.currentCursor = this.charAt(bp);
                matchStat = VALUE;
                token = JSONToken.COMMA;
                return negative ? -value : value;
            } else {
                if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + (offset++));
                    continue;
                }

                matchStat = NOT_MATCH;
                return value;
            }
        }
    }

    public final float scanFieldFloat(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        float value;
        if (chLocal >= '0' && chLocal <= '9') {
            long intVal = chLocal - '0';
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    continue;
                } else {
                    break;
                }
            }

            long power = 1;
            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    power = 10;
                    for (;;) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            intVal = intVal * 10 + (chLocal - '0');
                            power *= 10;
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (;;) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + fieldName.length + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp + fieldName.length;
                count = bp + offset - start - 1;
            }

            if ((!exp) && count < 17) {
                value = (float) (((double) intVal) / power);
                if (negative) {
                    value = -value;
                }
            } else {
                String text = this.subString(start, count);
                value = Float.parseFloat(text);
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == '}') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACE;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                bp += (offset - 1);
                token = JSONToken.EOF;
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return value;
    }

    public final float scanFloat(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        float value;
        if (chLocal >= '0' && chLocal <= '9') {
            long intVal = chLocal - '0';
            for (; ; ) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    continue;
                } else {
                    break;
                }
            }

            long power = 1;
            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    power = 10;
                    for (; ; ) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            intVal = intVal * 10 + (chLocal - '0');
                            power *= 10;
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (; ; ) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }
//            int start, count;
//            if (quote) {
//                if (chLocal != '"') {
//                    matchStat = NOT_MATCH;
//                    return 0;
//                } else {
//                    chLocal = charAt(bp + (offset++));
//                }
//                start = bp + 1;
//                count = bp + offset - start - 2;
//            } else {
//                start = bp;
//                count = bp + offset - start - 1;
//            }
//            String text = this.subString(start, count);
//            value = Float.parseFloat(text);
            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp;
                count = bp + offset - start - 1;
            }

            if ((!exp) && count < 17) {
                value = (float) (((double) intVal) / power);
                if (negative) {
                    value = -value;
                }
            } else {
                String text = this.subString(start, count);
                value = Float.parseFloat(text);
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == seperator) {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        } else {
            matchStat = NOT_MATCH;
            return value;
        }
    }

    public double scanDouble(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        double value;
        if (chLocal >= '0' && chLocal <= '9') {
            long intVal = chLocal - '0';
            for (; ; ) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    continue;
                } else {
                    break;
                }
            }

            long power = 1;
            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    power = 10;
                    for (; ; ) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            intVal = intVal * 10 + (chLocal - '0');
                            power *= 10;
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (; ; ) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp;
                count = bp + offset - start - 1;
            }

            if (!exp && count < 17) {
                value = ((double) intVal) / power;
                if (negative) {
                    value = -value;
                }
            } else {
                String text = this.subString(start, count);
                value = Double.parseDouble(text);
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == seperator) {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        } else {
            matchStat = NOT_MATCH;
            return value;
        }
    }

    public BigDecimal scanDecimal(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        BigDecimal value;
        if (chLocal >= '0' && chLocal <= '9') {
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    continue;
                } else {
                    break;
                }
            }

            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    for (;;) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (;;) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return null;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp;
                count = bp + offset - start - 1;
            }

            char[] chars = this.sub_chars(start, count);
            value = new BigDecimal(chars);
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = null;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == '}') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACE;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return null;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return value;
    }

    public final float[] scanFieldFloatArray(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        if (chLocal != '[') {
            matchStat = NOT_MATCH_NAME;
            return null;
        }
        chLocal = charAt(bp + (offset++));

        float[] array = new float[16];
        int arrayIndex = 0;

        for (;;) {
            int start = bp + offset - 1;

            boolean negative = chLocal == '-';
            if (negative) {
                chLocal = charAt(bp + (offset++));
            }

            if (chLocal >= '0' && chLocal <= '9') {
                int intVal = chLocal - '0';
                for (; ; ) {
                    chLocal = charAt(bp + (offset++));
                    if (chLocal >= '0' && chLocal <= '9') {
                        intVal = intVal * 10 + (chLocal - '0');
                        continue;
                    } else {
                        break;
                    }
                }

                int power = 1;
                boolean small = (chLocal == '.');
                if (small) {
                    chLocal = charAt(bp + (offset++));
                    power = 10;
                    if (chLocal >= '0' && chLocal <= '9') {
                        intVal = intVal * 10 + (chLocal - '0');
                        for (; ; ) {
                            chLocal = charAt(bp + (offset++));

                            if (chLocal >= '0' && chLocal <= '9') {
                                intVal = intVal * 10 + (chLocal - '0');
                                power *= 10;
                                continue;
                            } else {
                                break;
                            }
                        }
                    } else {
                        matchStat = NOT_MATCH;
                        return null;
                    }
                }

                boolean exp = chLocal == 'e' || chLocal == 'E';
                if (exp) {
                    chLocal = charAt(bp + (offset++));
                    if (chLocal == '+' || chLocal == '-') {
                        chLocal = charAt(bp + (offset++));
                    }
                    for (;;) {
                        if (chLocal >= '0' && chLocal <= '9') {
                            chLocal = charAt(bp + (offset++));
                        } else {
                            break;
                        }
                    }
                }

                int count = bp + offset - start - 1;

                float value;
                if (!exp && count < 10) {
                    value = ((float) intVal) / power;
                    if (negative) {
                        value = -value;
                    }
                } else {
                    String text = this.subString(start, count);
                    value = Float.parseFloat(text);
                }

                if (arrayIndex >= array.length) {
                    float[] tmp = new float[array.length * 3 / 2];
                    System.arraycopy(array, 0, tmp, 0, arrayIndex);
                    array = tmp;
                }
                array[arrayIndex++] = value;

                if (chLocal == ',') {
                    chLocal = charAt(bp + (offset++));
                } else if (chLocal == ']') {
                    chLocal = charAt(bp + (offset++));
                    break;
                }
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        }


        if (arrayIndex != array.length) {
            float[] tmp = new float[arrayIndex];
            System.arraycopy(array, 0, tmp, 0, arrayIndex);
            array = tmp;
        }

        if (chLocal == ',') {
            bp += (offset - 1);
            this.next();
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return array;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == EOI) {
                bp += (offset - 1);
                token = JSONToken.EOF;
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return array;
    }

    public final float[][] scanFieldFloatArray2(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        if (chLocal != '[') {
            matchStat = NOT_MATCH_NAME;
            return null;
        }
        chLocal = charAt(bp + (offset++));

        float[][] arrayarray = new float[16][];
        int arrayarrayIndex = 0;

        for (;;) {
            if (chLocal == '[') {
                chLocal = charAt(bp + (offset++));

                float[] array = new float[16];
                int arrayIndex = 0;

                for (; ; ) {
                    int start = bp + offset - 1;
                    boolean negative = chLocal == '-';
                    if (negative) {
                        chLocal = charAt(bp + (offset++));
                    }

                    if (chLocal >= '0' && chLocal <= '9') {
                        int intVal = chLocal - '0';
                        for (; ; ) {
                            chLocal = charAt(bp + (offset++));

                            if (chLocal >= '0' && chLocal <= '9') {
                                intVal = intVal * 10 + (chLocal - '0');
                                continue;
                            } else {
                                break;
                            }
                        }

                        int power = 1;
                        if (chLocal == '.') {
                            chLocal = charAt(bp + (offset++));

                            if (chLocal >= '0' && chLocal <= '9') {
                                intVal = intVal * 10 + (chLocal - '0');
                                power = 10;
                                for (; ; ) {
                                    chLocal = charAt(bp + (offset++));

                                    if (chLocal >= '0' && chLocal <= '9') {
                                        intVal = intVal * 10 + (chLocal - '0');
                                        power *= 10;
                                        continue;
                                    } else {
                                        break;
                                    }
                                }
                            } else {
                                matchStat = NOT_MATCH;
                                return null;
                            }
                        }

                        boolean exp = chLocal == 'e' || chLocal == 'E';
                        if (exp) {
                            chLocal = charAt(bp + (offset++));
                            if (chLocal == '+' || chLocal == '-') {
                                chLocal = charAt(bp + (offset++));
                            }
                            for (;;) {
                                if (chLocal >= '0' && chLocal <= '9') {
                                    chLocal = charAt(bp + (offset++));
                                } else {
                                    break;
                                }
                            }
                        }

                        int count = bp + offset - start - 1;
                        float value;
                        if (!exp && count < 10) {
                            value = ((float) intVal) / power;
                            if (negative) {
                                value = -value;
                            }
                        } else {
                            String text = this.subString(start, count);
                            value = Float.parseFloat(text);
                        }

                        if (arrayIndex >= array.length) {
                            float[] tmp = new float[array.length * 3 / 2];
                            System.arraycopy(array, 0, tmp, 0, arrayIndex);
                            array = tmp;
                        }
                        array[arrayIndex++] = value;

                        if (chLocal == ',') {
                            chLocal = charAt(bp + (offset++));
                        } else if (chLocal == ']') {
                            chLocal = charAt(bp + (offset++));
                            break;
                        }
                    } else {
                        matchStat = NOT_MATCH;
                        return null;
                    }
                }

                // compact
                if (arrayIndex != array.length) {
                    float[] tmp = new float[arrayIndex];
                    System.arraycopy(array, 0, tmp, 0, arrayIndex);
                    array = tmp;
                }

                if (arrayarrayIndex >= arrayarray.length) {
                    float[][] tmp = new float[arrayarray.length * 3 / 2][];
                    System.arraycopy(array, 0, tmp, 0, arrayIndex);
                    arrayarray = tmp;
                }
                arrayarray[arrayarrayIndex++] = array;

                if (chLocal == ',') {
                    chLocal = charAt(bp + (offset++));
                } else if (chLocal == ']') {
                    chLocal = charAt(bp + (offset++));
                    break;
                }
            } else {
                break;
            }
        }

        // compact
        if (arrayarrayIndex != arrayarray.length) {
            float[][] tmp = new float[arrayarrayIndex][];
            System.arraycopy(arrayarray, 0, tmp, 0, arrayarrayIndex);
            arrayarray = tmp;
        }

        if (chLocal == ',') {
            bp += (offset - 1);
            this.next();
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return arrayarray;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += (offset - 1);
                this.next();
            } else if (chLocal == EOI) {
                bp += (offset - 1);
                token = JSONToken.EOF;
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return arrayarray;
    }

    public final double scanFieldDouble(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        double value;
        if (chLocal >= '0' && chLocal <= '9') {
            long intVal = chLocal - '0';

            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    continue;
                } else {
                    break;
                }
            }

            long power = 1;
            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    power = 10;
                    for (;;) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            intVal = intVal * 10 + (chLocal - '0');
                            power *= 10;
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (;;) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + fieldName.length + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp + fieldName.length;
                count = bp + offset - start - 1;
            }

            if (!exp && count < 17) {
                value = ((double) intVal) / power;
                if (negative) {
                    value = -value;
                }
            } else {
                String text = this.subString(start, count);
                value = Double.parseDouble(text);
            }
        } else if (chLocal == 'n' &&
                   charAt(bp + offset) == 'u' &&
                   charAt(bp + offset + 1) == 'l' &&
                   charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == '}') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACE;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return value;
    }

    public BigDecimal scanFieldDecimal(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        BigDecimal value;
        if (chLocal >= '0' && chLocal <= '9') {
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    continue;
                } else {
                    break;
                }
            }

            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    for (;;) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (;;) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return null;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + fieldName.length + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp + fieldName.length;
                count = bp + offset - start - 1;
            }

            char[] chars = this.sub_chars(start, count);
            value = new BigDecimal(chars);
        } else if (chLocal == 'n' &&
                   charAt(bp + offset) == 'u' &&
                   charAt(bp + offset + 1) == 'l' &&
                   charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = null;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == '}') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACE;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return null;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return value;
    }

    public BigInteger scanFieldBigInteger(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        BigInteger value;
        if (chLocal >= '0' && chLocal <= '9') {
            long intVal = chLocal - '0';
            boolean overflow = false;
            long temp;
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    temp = intVal * 10 + (chLocal - '0');
                    if (temp < intVal) {
                        overflow = true;
                        break;
                    }
                    intVal = temp;
                    continue;
                } else {
                    break;
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return null;
                } else {
                    chLocal = charAt(bp + (offset++));
                }
                start = bp + fieldName.length + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp + fieldName.length;
                count = bp + offset - start - 1;
            }

            if (!overflow && (count < 20 || (negative && count < 21))) {
                value = BigInteger.valueOf(negative ? -intVal : intVal);
            } else {

//            char[] chars = this.sub_chars(negative ? start + 1 : start, count);
//            value = new BigInteger(chars, )
                String strVal = this.subString(start, count);
                value = new BigInteger(strVal);
            }
        } else if (chLocal == 'n' &&
                   charAt(bp + offset) == 'u' &&
                   charAt(bp + offset + 1) == 'l' &&
                   charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = null;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == '}') {
                    bp += offset;
                    this.currentCursor = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACE;
                    return value;
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return null;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return value;
    }

    public java.util.Date scanFieldDate(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        // int index = bp + fieldName.length;

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final java.util.Date dateVal;
        if (chLocal == '"'){
            int startIndex = bp + fieldName.length + 1;
            int endIndex = indexOf('"', startIndex);
            if (endIndex == -1) {
                throw new JSONException("unclosed str");
            }

            int startIndex2 = bp + fieldName.length + 1; // must re compute
            String stringVal = subString(startIndex2, endIndex - startIndex2);
            if (stringVal.indexOf('\\') != -1) {
                for (;;) {
                    int slashCount = 0;
                    for (int i = endIndex - 1; i >= 0; --i) {
                        if (charAt(i) == '\\') {
                            slashCount++;
                        } else {
                            break;
                        }
                    }
                    if (slashCount % 2 == 0) {
                        break;
                    }
                    endIndex = indexOf('"', endIndex + 1);
                }

                int chars_len = endIndex - (bp + fieldName.length + 1);
                char[] chars = sub_chars( bp + fieldName.length + 1, chars_len);

                stringVal = readString(chars, chars_len);
            }

            offset += (endIndex - (bp + fieldName.length + 1) + 1);
            chLocal = charAt(bp + (offset++));

            JSONScanner dateLexer = new JSONScanner(stringVal);
            try {
                if (dateLexer.scanISO8601DateIfMatch(false)) {
                    Calendar calendar = dateLexer.getCalendar();
                    dateVal = calendar.getTime();
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            } finally {
                dateLexer.close();
            }
        } else if (chLocal == '-' || (chLocal >= '0' && chLocal <= '9')) {
            long millis = 0;

            boolean negative = false;
            if (chLocal == '-') {
                chLocal = charAt(bp + (offset++));
                negative = true;
            }

            if (chLocal >= '0' && chLocal <= '9') {
                millis = chLocal - '0';
                for (; ; ) {
                    chLocal = charAt(bp + (offset++));
                    if (chLocal >= '0' && chLocal <= '9') {
                        millis = millis * 10 + (chLocal - '0');
                    } else {
                        break;
                    }
                }
            }

            if (millis < 0) {
                matchStat = NOT_MATCH;
                return null;
            }

            if (negative) {
                millis = -millis;
            }

            dateVal = new java.util.Date(millis);
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return dateVal;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return dateVal;
    }

    public java.util.Date scanDate(char seperator) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        final java.util.Date dateVal;
        if (chLocal == '"'){
            int startIndex = bp + 1;
            int endIndex = indexOf('"', startIndex);
            if (endIndex == -1) {
                throw new JSONException("unclosed str");
            }

            int startIndex2 = bp + 1; // must re compute
            String stringVal = subString(startIndex2, endIndex - startIndex2);
            if (stringVal.indexOf('\\') != -1) {
                for (;;) {
                    int slashCount = 0;
                    for (int i = endIndex - 1; i >= 0; --i) {
                        if (charAt(i) == '\\') {
                            slashCount++;
                        } else {
                            break;
                        }
                    }
                    if (slashCount % 2 == 0) {
                        break;
                    }
                    endIndex = indexOf('"', endIndex + 1);
                }

                int chars_len = endIndex - (bp + 1);
                char[] chars = sub_chars( bp + 1, chars_len);

                stringVal = readString(chars, chars_len);
            }

            offset += (endIndex - (bp + 1) + 1);
            chLocal = charAt(bp + (offset++));

            JSONScanner dateLexer = new JSONScanner(stringVal);
            try {
                if (dateLexer.scanISO8601DateIfMatch(false)) {
                    Calendar calendar = dateLexer.getCalendar();
                    dateVal = calendar.getTime();
                } else {
                    matchStat = NOT_MATCH;
                    return null;
                }
            } finally {
                dateLexer.close();
            }
        } else if (chLocal == '-' || (chLocal >= '0' && chLocal <= '9')) {
            long millis = 0;

            boolean negative = false;
            if (chLocal == '-') {
                chLocal = charAt(bp + (offset++));
                negative = true;
            }

            if (chLocal >= '0' && chLocal <= '9') {
                millis = chLocal - '0';
                for (; ; ) {
                    chLocal = charAt(bp + (offset++));
                    if (chLocal >= '0' && chLocal <= '9') {
                        millis = millis * 10 + (chLocal - '0');
                    } else {
                        break;
                    }
                }
            }

            if (millis < 0) {
                matchStat = NOT_MATCH;
                return null;
            }

            if (negative) {
                millis = -millis;
            }

            dateVal = new java.util.Date(millis);
        } else if (chLocal == 'n' &&
                   charAt(bp + offset) == 'u' &&
                   charAt(bp + offset + 1) == 'l' &&
                   charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            dateVal = null;
            offset += 3;
            chLocal = charAt(bp + offset++);
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return dateVal;
        }

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return dateVal;
    }

    public java.util.UUID scanFieldUUID(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return null;
        }

        // int index = bp + fieldName.length;

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final java.util.UUID uuid;
        if (chLocal == '"') {
            int startIndex = bp + fieldName.length + 1;
            int endIndex = indexOf('"', startIndex);
            if (endIndex == -1) {
                throw new JSONException("unclosed str");
            }

            int startIndex2 = bp + fieldName.length + 1; // must re compute
            int len = endIndex - startIndex2;
            if (len == 36) {
                long mostSigBits = 0, leastSigBits = 0;
                for (int i = 0; i < 8; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 9; i < 13; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 14; i < 18; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 19; i < 23; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    leastSigBits <<= 4;
                    leastSigBits |= num;
                }
                for (int i = 24; i < 36; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    leastSigBits <<= 4;
                    leastSigBits |= num;
                }
                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + fieldName.length + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else if (len == 32) {
                long mostSigBits = 0, leastSigBits = 0;
                for (int i = 0; i < 16; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 16; i < 32; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    leastSigBits <<= 4;
                    leastSigBits |= num;
                }

                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + fieldName.length + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        } else if (chLocal == 'n'
                && charAt(bp + (offset++)) == 'u'
                && charAt(bp + (offset++)) == 'l'
                && charAt(bp + (offset++)) == 'l') {
            uuid = null;
            chLocal = charAt(bp + (offset++));
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return uuid;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return uuid;
    }

    public java.util.UUID scanUUID(char seperator) {
        matchStat = UNKNOWN;

        // int index = bp + fieldName.length;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        final java.util.UUID uuid;
        if (chLocal == '"') {
            int startIndex = bp + 1;
            int endIndex = indexOf('"', startIndex);
            if (endIndex == -1) {
                throw new JSONException("unclosed str");
            }

            int startIndex2 = bp + 1; // must re compute
            int len = endIndex - startIndex2;
            if (len == 36) {
                long mostSigBits = 0, leastSigBits = 0;
                for (int i = 0; i < 8; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 9; i < 13; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 14; i < 18; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 19; i < 23; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    leastSigBits <<= 4;
                    leastSigBits |= num;
                }
                for (int i = 24; i < 36; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    leastSigBits <<= 4;
                    leastSigBits |= num;
                }
                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else if (len == 32) {
                long mostSigBits = 0, leastSigBits = 0;
                for (int i = 0; i < 16; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    mostSigBits <<= 4;
                    mostSigBits |= num;
                }
                for (int i = 16; i < 32; ++i) {
                    char ch = charAt(startIndex2 + i);
                    int num;
                    if (ch >= '0' && ch <= '9') {
                        num = ch - '0';
                    } else if (ch >= 'a' && ch <= 'f') {
                        num = 10 + (ch - 'a');
                    } else if (ch >= 'A' && ch <= 'F') {
                        num = 10 + (ch - 'A');
                    } else {
                        matchStat = NOT_MATCH_NAME;
                        return null;
                    }

                    leastSigBits <<= 4;
                    leastSigBits |= num;
                }

                uuid = new UUID(mostSigBits, leastSigBits);

                offset += (endIndex - (bp + 1) + 1);
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        } else if (chLocal == 'n'
                && charAt(bp + (offset++)) == 'u'
                && charAt(bp + (offset++)) == 'l'
                && charAt(bp + (offset++)) == 'l') {
            uuid = null;
            chLocal = charAt(bp + (offset++));
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        if (chLocal == ',') {
            bp += offset;
            this.currentCursor = this.charAt(bp);
            matchStat = VALUE;
            return uuid;
        }

        if (chLocal == ']') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.currentCursor = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                currentCursor = EOI;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return null;
        }

        return uuid;
    }

    
    public final void scanNullOrNew() {
        scanNullOrNew(true);
    }
    @Read(desc="跟true false做法是一样的")
    public final void scanNullOrNew(boolean acceptColon) {
        if (currentCursor != 'n') {
            throw new JSONException("error parse null or new");
        }
        next();

        if (currentCursor == 'u') {
            next();
            if (currentCursor != 'l') {
                throw new JSONException("error parse null");
            }
            next();

            if (currentCursor != 'l') {
                throw new JSONException("error parse null");
            }
            next();


            if (currentCursor == ' '
                    || currentCursor == ','
                    || currentCursor == '}'
                    || currentCursor == ']'
                    || currentCursor == '\n'
                    || currentCursor == '\r'
                    || currentCursor == '\t'
                    || currentCursor == EOI
                    || (currentCursor == ':' && acceptColon)
                    || currentCursor == '\f'
                    || currentCursor == '\b') {

                token = JSONToken.NULL;
            } else {
                throw new JSONException("scan null error");
            }
            return;
        }

        if (currentCursor != 'e') {
            throw new JSONException("error parse new");
        }
        next();

        if (currentCursor != 'w') {
            throw new JSONException("error parse new");
        }
        next();

        if (currentCursor == ' '  ||
            currentCursor == ','  ||
            currentCursor == '}'  ||
            currentCursor == ']'  ||
            currentCursor == '\n' ||
            currentCursor == '\r' ||
            currentCursor == '\t' ||
            currentCursor == EOI  ||
            currentCursor == '\f' ||
            currentCursor == '\b') {
            token = JSONToken.NEW;
        } else {
            throw new JSONException("scan new error");
        }
    }
    
    @Read
    public final void scanTrue() {
    	//一个个往下读，只要不是true，就异常
        if (currentCursor != 't') {
            throw new JSONException("error parse true");
        }
        next();

        if (currentCursor != 'r') {
            throw new JSONException("error parse true");
        }
        next();

        if (currentCursor != 'u') {
            throw new JSONException("error parse true");
        }
        next();

        if (currentCursor != 'e') {
            throw new JSONException("error parse true");
        }
        next();

        //往下读，true后面必须是这些结束符中的一个
        if (currentCursor == ' '  ||
            currentCursor == ','  ||
            currentCursor == '}'  ||
            currentCursor == ']'  ||
            currentCursor == '\n' ||
            currentCursor == '\r' ||
            currentCursor == '\t' ||
            currentCursor == EOI  ||
            currentCursor == '\f' ||
            currentCursor == '\b' ||
            currentCursor == ':'  ||
            currentCursor == '/') {
            token = JSONToken.TRUE;
        } else {
            throw new JSONException("scan true error");
        }
    }
    
    @Read(desc="跟true一样，是配对的 这两个方法可以合并吗")
    public final void scanFalse() {
        if (currentCursor != 'f') {
            throw new JSONException("error parse false");
        }
        next();

        if (currentCursor != 'a') {
            throw new JSONException("error parse false");
        }
        next();

        if (currentCursor != 'l') {
            throw new JSONException("error parse false");
        }
        next();

        if (currentCursor != 's') {
            throw new JSONException("error parse false");
        }
        next();

        if (currentCursor != 'e') {
            throw new JSONException("error parse false");
        }
        next();

        if (currentCursor == ' '  ||
            currentCursor == ','  ||
            currentCursor == '}'  ||
            currentCursor == ']'  ||
            currentCursor == '\n' ||
            currentCursor == '\r' ||
            currentCursor == '\t' ||
            currentCursor == EOI  ||
            currentCursor == '\f' ||
            currentCursor == '\b' ||
            currentCursor == ':'  ||
            currentCursor == '/') {
            token = JSONToken.FALSE;
        } else {
            throw new JSONException("scan false error");
        }
    }

    @Read(desc="扫描标识符  就是关键字")
    public final void scanIdent() {
        numberStartPos = bp - 1;
        hasSpecial = false;

        for (;;) {
            sbufPos++;

            next();
            //是字母或者数字，跳过继续
            if (Character.isLetterOrDigit(currentCursor)) {
                continue;
            }

            //这里stringVal是抽象方法，如何拿到标识符
            String ident = stringVal();
            //直接判断标志符是不是这些关键字中的一个，是直接返回对应的代码
            if ("null".equalsIgnoreCase(ident)) {
                token = JSONToken.NULL;
            } else if ("new".equals(ident)) {
                token = JSONToken.NEW;
            } else if ("true".equals(ident)) {
                token = JSONToken.TRUE;
            } else if ("false".equals(ident)) {
                token = JSONToken.FALSE;
            } else if ("undefined".equals(ident)) {
                token = JSONToken.UNDEFINED;
            } else if ("Set".equals(ident)) {
                token = JSONToken.SET;
            } else if ("TreeSet".equals(ident)) {
                token = JSONToken.TREE_SET;
            } else {
                token = JSONToken.IDENTIFIER;
            }
            return;
        }
    }


    public static String readString(char[] chars, int chars_len) {
        char[] sbuf = new char[chars_len];
        int len = 0;
        for (int i = 0; i < chars_len; ++i) {
            char ch = chars[i];

            if (ch != '\\') {
                sbuf[len++] = ch;
                continue;
            }
            ch = chars[++i];

            switch (ch) {
                case '0':
                    sbuf[len++] = '\0';
                    break;
                case '1':
                    sbuf[len++] = '\1';
                    break;
                case '2':
                    sbuf[len++] = '\2';
                    break;
                case '3':
                    sbuf[len++] = '\3';
                    break;
                case '4':
                    sbuf[len++] = '\4';
                    break;
                case '5':
                    sbuf[len++] = '\5';
                    break;
                case '6':
                    sbuf[len++] = '\6';
                    break;
                case '7':
                    sbuf[len++] = '\7';
                    break;
                case 'b': // 8
                    sbuf[len++] = '\b';
                    break;
                case 't': // 9
                    sbuf[len++] = '\t';
                    break;
                case 'n': // 10
                    sbuf[len++] = '\n';
                    break;
                case 'v': // 11
                    sbuf[len++] = '\u000B';
                    break;
                case 'f': // 12
                case 'F':
                    sbuf[len++] = '\f';
                    break;
                case 'r': // 13
                    sbuf[len++] = '\r';
                    break;
                case '"': // 34
                    sbuf[len++] = '"';
                    break;
                case '\'': // 39
                    sbuf[len++] = '\'';
                    break;
                case '/': // 47
                    sbuf[len++] = '/';
                    break;
                case '\\': // 92
                    sbuf[len++] = '\\';
                    break;
                case 'x':
                    sbuf[len++] = (char) (digits[chars[++i]] * 16 + digits[chars[++i]]);
                    break;
                case 'u':
                    sbuf[len++] = (char) Integer.parseInt(new String(new char[] { chars[++i], //
                                    chars[++i], //
                                    chars[++i], //
                                    chars[++i] }),
                            16);
                    break;
                default:
                    throw new JSONException("unclosed.str.lit");
            }
        }
        return new String(sbuf, 0, len);
    }


//空行输入
    public boolean isBlankInput() {
        for (int i = 0;; ++i) {
            char chLocal = charAt(i);
            if (chLocal == EOI) {
                token = JSONToken.EOF;
                break;
            }

            if (!isWhitespace(chLocal)) {
                return false;
            }
        }

        return true;
    }

    //跳过空格  为啥要跳过注释
    public final void skipWhitespace() {
        for (;;) {
            if (currentCursor <= '/') {
                if (currentCursor == ' '  ||
                    currentCursor == '\r' ||
                    currentCursor == '\n' ||
                    currentCursor == '\t' ||
                    currentCursor == '\f' ||
                    currentCursor == '\b') {
                    next();
                    continue;
                } else if (currentCursor == '/') {
                    skipComment();
                    continue;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
    }
    
    @Read
    public static boolean isWhitespace(char ch) {
        // 专门调整了判断顺序   是按照出现的频率高低？
        return ch <= ' '  &&
              (ch == ' '  ||
               ch == '\n' ||
               ch == '\r' ||
               ch == '\t' ||
               ch == '\f' ||
               ch == '\b');
    }

    //跳过注释
    @Read
    protected void skipComment() {
    	/* 思路
    	 * 读到/时候会调skipComment，继续往下读，
    	 * 注释分为//和/**\/两种
    	 * 继续往下读第二个字符，如果读到/则是//，继续读一直到\n或eoi结束
    	 * 如果读到**，则是/**\/，继续直到/结束
    	 * 如果既不是*也不是/，就不是正规注释  异常
    	 */	
        next();
        /** 如果下一个字符还是左反斜杠/ */
        if (currentCursor == '/') {
            for (;;) {
                next();
                /** 如果遇到换行符，继续读取下一个字符并返回 */
                if (currentCursor == '\n') {
                    next();
                    return;//return到for  这个地方会不会多执行了一次next?
                } else if (currentCursor == EOI) {
                	/** 如果已经遇到流结束，返回 */
                    return;
                }
            }
        } else if (currentCursor == '*') {
            next();
            //遇到*继续往下读，读到/结束
            for (; currentCursor != EOI;) {
            	//如果遇到*,继续尝试读取下一个字符，看看是否是/   
                if (currentCursor == '*') {
                    next();
                    if (currentCursor == '/') {
                    	// 如果确实是/字符，提前预读下一个有效字符后终止 
                        next();
                        return;
                    } else {
                    	// 遇到非/ 继续跳过读下一个字符    
                        continue;
                    }
                }
                next();
            }
        } else {
            throw new JSONException("invalid comment");
        }
    }
    /**
     * Append a character ch to sbuf.
     * 将ch放到缓冲区sbuf末尾
     */
    @Read
    protected final void putChar(char ch) {
    	//如果缓冲区数组已经满了，扩容为2倍
        if (sbufPos == sbuf.length) {
            char[] newsbuf = new char[sbuf.length * 2];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
        sbuf[sbufPos++] = ch;
    }
    

    //扫描单引号  返回值为空
    private void scanStringSingleQuote() {
        numberStartPos = bp;
        hasSpecial = false;
        char chLocal;
        for (;;) {
            chLocal = next();

            if (chLocal == '\'') {
                break;
            }

            //单引号没有闭合
            if (chLocal == EOI) {
                if (!isEOF()) {
                    putChar((char) EOI);
                    continue;
                }
                throw new JSONException("unclosed single-quote string");
            }

            if (chLocal == '\\') {
                if (!hasSpecial) {
                	//特殊字符
                    hasSpecial = true;

                    if (sbufPos > sbuf.length) {
                        char[] newsbuf = new char[sbufPos * 2];
                        System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
                        sbuf = newsbuf;
                    }

                    // text.getChars(offset, offset + count, dest, 0);
                    this.copyTo(numberStartPos + 1, sbufPos, sbuf);
                    // System.arraycopy(buf, np + 1, sbuf, 0, sp);
                }

                chLocal = next();

                switch (chLocal) {
                    case '0':
                        putChar('\0');
                        break;
                    case '1':
                        putChar('\1');
                        break;
                    case '2':
                        putChar('\2');
                        break;
                    case '3':
                        putChar('\3');
                        break;
                    case '4':
                        putChar('\4');
                        break;
                    case '5':
                        putChar('\5');
                        break;
                    case '6':
                        putChar('\6');
                        break;
                    case '7':
                        putChar('\7');
                        break;
                    case 'b': // 8
                        putChar('\b');
                        break;
                    case 't': // 9
                        putChar('\t');
                        break;
                    case 'n': // 10
                        putChar('\n');
                        break;
                    case 'v': // 11
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                    case 'F':
                        putChar('\f');
                        break;
                    case 'r': // 13
                        putChar('\r');
                        break;
                    case '"': // 34
                        putChar('"');
                        break;
                    case '\'': // 39
                        putChar('\'');
                        break;
                    case '/': // 47
                        putChar('/');
                        break;
                    case '\\': // 92
                        putChar('\\');
                        break;
                    case 'x':
                        char x1 = next();
                        char x2 = next();

                        boolean hex1 = (x1 >= '0' && x1 <= '9')
                                || (x1 >= 'a' && x1 <= 'f')
                                || (x1 >= 'A' && x1 <= 'F');
                        boolean hex2 = (x2 >= '0' && x2 <= '9')
                                || (x2 >= 'a' && x2 <= 'f')
                                || (x2 >= 'A' && x2 <= 'F');
                        if (!hex1 || !hex2) {
                            throw new JSONException("invalid escape character \\x" + x1 + x2);
                        }

                        putChar((char) (digits[x1] * 16 + digits[x2]));
                        break;
                    case 'u':
                        putChar((char) Integer.parseInt(new String(new char[] { next(), next(), next(), next() }), 16));
                        break;
                    default:
                        this.currentCursor = chLocal;
                        throw new JSONException("unclosed single-quote string");
                }
                continue;
            }

            if (!hasSpecial) {
                sbufPos++;
                continue;
            }

            if (sbufPos == sbuf.length) {
                putChar(chLocal);
            } else {
                sbuf[sbufPos++] = chLocal;
            }
        }

        token = LITERAL_STRING;
        this.next();
    }
    
    
    /*******抽象方法************/
    public abstract String numberString();
    //是否是文件结尾
    public abstract boolean isEOF();
    public abstract byte[] bytesValue();
    public abstract BigDecimal decimalValue();
    /**
     * 这里提供的stringVal()需要由子类实现，原因：
     * 在android6.0和jdk6版本 获取子字符串会共享外层String的char[] 
     * 会导致String占用内存无法释放（特别是打文本字符串）。
     */
    public abstract String stringVal();
    public abstract String subString(int offset, int count);
    public abstract char charAt(int index);
    //光标读取下一个字符
    public abstract char next();
    
    protected abstract boolean charArrayCompare(char[] chars);
    protected abstract char[] sub_chars(int offset, int count);
    protected abstract void arrayCopy(int srcPos, char[] dest, int destPos, int length);
    protected abstract void copyTo(int offset, int count, char[] dest);

    
    /********不重要的方法***************/
    //根据类型转成名字
    @Read
    public final String tokenName() {
        return JSONToken.name(token);
    }
    //当前类型为error ，可以去掉参数
//    protected void lexError(String key, Object... args) {
//        token = ERROR;
//    }
    public String info() {
        return "";
    }
    /*******get set方法************/
    //拿到当前字符
    public final char getCurrent() {
        return currentCursor;
    }
    /**
     * internal method, don't invoke
     * @param token
     */
    public Calendar getCalendar() {
        return this.calendar;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }
    public void setToken(int token) {
        this.token = token;
    }
    public final int token() {
        return token;
    }
    public final int pos() {
    	return pos;
    }
    public final String stringDefaultValue() {
    	return stringDefaultValue;
    }
    public int getFeatures() {
        return this.features;
    }
    public final int matchStat() {
        return matchStat;
    }
    
    /**********不支持操作异常************* 
     * 
     * 
     * 
     * 
     * 
     * 
     * 
     * 
     * 
     */
    public int matchField(long fieldNameHash) {
        throw new UnsupportedOperationException();
    }

    public boolean seekArrayToItem(int index) {
        throw new UnsupportedOperationException();
    }

    public int seekObjectToField(long fieldNameHash, boolean deepScan) {
        throw new UnsupportedOperationException();
    }

    public int seekObjectToField(long[] fieldNameHash) {
        throw new UnsupportedOperationException();
    }

    public int seekObjectToFieldDeepScan(long fieldNameHash) {
        throw new UnsupportedOperationException();
    }

    public void skipObject() {
        throw new UnsupportedOperationException();
    }

    public void skipObject(boolean valid) {
        throw new UnsupportedOperationException();
    }

    public void skipArray() {
        throw new UnsupportedOperationException();
    }
    /**
     * hsf support
     * @param fieldName
     * @param argTypesCount
     * @param typeSymbolTable
     * @return
     */
    public String[] scanFieldStringArray(char[] fieldName, int argTypesCount, SymbolTable typeSymbolTable) {
        throw new UnsupportedOperationException();
    }

    public boolean matchField2(char[] fieldName) {
        throw new UnsupportedOperationException();
    }
    
    /*****************不重要的************************/
    //未用到
    public final boolean isEnabled(int features, int feature) {
        return (this.features & feature) != 0 || (features & feature) != 0;
    }

    // public final char next() {
    // ch = doNext();
    //// if (ch == '/' && (this.features & Feature.AllowComment.mask) != 0) {
    //// skipComment();
    //// }
    // return ch;
    // }
}
