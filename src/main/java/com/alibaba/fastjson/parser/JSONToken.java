/*
 * Copyright 1999-2017 Alibaba Group.
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

/**
 * @author wenshao[szujobs@hotmail.com]
 * 枚举常量类 json字符的种类，给不同类别的字符 设置 一个编号
 * String name(int value)根据编号返回类别名字
 */
public class JSONToken {

    //
    public final static int ERROR                = 1;
    //
    public final static int LITERAL_INT          = 2;//int整形
    //
    public final static int LITERAL_FLOAT        = 3;//浮点
    //
    public final static int LITERAL_STRING       = 4;
    //
    public final static int LITERAL_ISO8601_DATE = 5;//iso8601

    public final static int TRUE                 = 6;
    //
    public final static int FALSE                = 7;
    //
    public final static int NULL                 = 8;
    //
    public final static int NEW                  = 9;// new  新类型
    //
    public final static int LPAREN               = 10; // ("("),
    //
    public final static int RPAREN               = 11; // (")"),
    //
    public final static int LBRACE               = 12; // ("{"),
    //
    public final static int RBRACE               = 13; // ("}"),
    //
    public final static int LBRACKET             = 14; // ("["),
    //
    public final static int RBRACKET             = 15; // ("]"),
    //
    public final static int COMMA                = 16; // (","),
    //
    public final static int COLON                = 17; //冒号 (":"),
    //
    public final static int IDENTIFIER           = 18;//"ident"
    //
    public final static int FIELD_NAME           = 19;//"fieldName"

    public final static int EOF                  = 20;//eof  end of file

    public final static int SET                  = 21;//Set
    public final static int TREE_SET             = 22;//TreeSet
    
    public final static int UNDEFINED            = 23; //未定义 undefined

    public final static int SEMI                 = 24;//分号
    public final static int DOT                  = 25;//点号
    public final static int HEX                  = 26;// hex  十六进制

    //根据枚举值返回类型名字，一共分成了26中json字符类型
    public static String name(int value) {
        switch (value) {
            case ERROR:
                return "error";
            case LITERAL_INT:
                return "int";
            case LITERAL_FLOAT:
                return "float";
            case LITERAL_STRING:
                return "string";
            case LITERAL_ISO8601_DATE:
                return "iso8601";
            case TRUE:
                return "true";
            case FALSE:
                return "false";
            case NULL:
                return "null";
            case NEW:
                return "new";
            case LPAREN:
                return "(";
            case RPAREN:
                return ")";
            case LBRACE:
                return "{";
            case RBRACE:
                return "}";
            case LBRACKET:
                return "[";
            case RBRACKET:
                return "]";
            case COMMA:
                return ",";
            case COLON:
                return ":";
            case SEMI:
                return ";";
            case DOT:
                return ".";
            case IDENTIFIER:
                return "ident";
            case FIELD_NAME:
                return "fieldName";
            case EOF:
                return "EOF";
            case SET:
                return "Set";
            case TREE_SET:
                return "TreeSet";
            case UNDEFINED:
                return "undefined";
            case HEX:
                return "hex";
            default:
                return "Unknown";
        }
    }
}
