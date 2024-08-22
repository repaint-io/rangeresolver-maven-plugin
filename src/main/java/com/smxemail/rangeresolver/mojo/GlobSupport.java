package com.smxemail.rangeresolver.mojo;

import java.util.regex.Pattern;

public class GlobSupport {
    private static final Pattern globReplacement = Pattern.compile("\\*");

    private GlobSupport() {}

    public static Pattern convertGlobToRegex(String glob) {
        String globbed = globReplacement.matcher(glob).replaceAll("\\.*");
        return Pattern.compile(globbed);
    }
}
