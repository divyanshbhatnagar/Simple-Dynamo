package edu.buffalo.cse.cse486586.simpledynamo;

/**
 * Created by divya on 5/1/2017.
 */

import java.io.Serializable;
import java.util.HashMap;

public class Message implements Serializable {
    public String succ;
    public String pred;
    public String mType;
    public String origin;
    public String dest;
    public String key;
    public String value;
    public HashMap<String, String> allQResult;
    public String source;

    public Message(String s, String p, String t, String o, String d, String k, String v ) {
        succ = s;
        pred = p;
        mType = t;
        origin = o;
        dest = d;
        key = k;
        value = v;
    }
    public Message(String s, String p, String t, String o, String d, String k, String v, HashMap<String, String> aQR) {
        succ = s;
        pred = p;
        mType = t;
        origin = o;
        dest = d;
        key = k;
        value = v;
        allQResult = aQR;
    }
    public Message(String s, String p, String t, String o, String d, String k, String v, HashMap<String, String> aQR, String src) {
        succ = s;
        pred = p;
        mType = t;
        origin = o;
        dest = d;
        key = k;
        value = v;
        allQResult = aQR;
        source = src;
    }
    public Message(String s, String p, String o){
        succ = s;
        pred = p;
        origin = o;
    }
}
