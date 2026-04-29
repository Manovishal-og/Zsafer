package com.zsafer.cli;

import java.io.*;
import java.util.Properties;

public class Config {

    private static final String FILE =
            System.getProperty("user.home") + "/.zsafer";

    public static String get(String key) throws Exception {
        Properties p = new Properties();
        File f = new File(FILE);

        if (!f.exists()) return null;

        try (FileInputStream fis = new FileInputStream(f)) {
            p.load(fis);
        }

        return p.getProperty(key);
    }

    public static void set(String key, String value) throws Exception {
        Properties p = new Properties();
        File f = new File(FILE);

        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                p.load(fis);
            }
        }

        p.setProperty(key, value);

        try (FileOutputStream fos = new FileOutputStream(f)) {
            p.store(fos, "zsafer config");
        }
    }
}
