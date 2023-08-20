package org.excelfore.tomcat.valve.acl;

public class Util {

    public static String sTrim(String s) {

        if (s == null) { return null; }
        s = s.trim();
        if (s.isEmpty()) { return null; }
        return s;

    }

}
