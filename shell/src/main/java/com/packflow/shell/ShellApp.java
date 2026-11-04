package com.packflow.shell;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** PackFlow error catcher (Android side) for quick-built apps: saves uncaught crashes. */
public class ShellApp extends Application {
    static final String DIR = "packflow_errors";
    static final String CRASH_FILE = "last_crash.txt";
    static final String LOG_FILE = "error_log.txt";
    private static final long MAX_LOG_BYTES = 64 * 1024;

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writeCrash(this, thread, error);
            } catch (Throwable ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    static String header(Context c) {
        String version = "?";
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {
        }
        return c.getPackageName() + " " + version + " | Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ") | " + Build.MANUFACTURER + " " + Build.MODEL;
    }

    static void writeCrash(Context c, Thread thread, Throwable error) throws IOException {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String text = "CRASH " + stamp() + " on thread '" + thread.getName() + "'\n" + header(c) + "\n\n" + sw;
        write(new File(dir(c), CRASH_FILE), text, false);
        appendLog(c, "CRASH", String.valueOf(error));
    }

    static synchronized void appendLog(Context c, String kind, String message) {
        try {
            File f = new File(dir(c), LOG_FILE);
            if (f.length() > MAX_LOG_BYTES) {
                String all = read(f);
                write(f, all.substring(all.length() / 2), false);
            }
            write(f, stamp() + " " + kind + " " + message.replace('\n', ' ') + "\n", true);
        } catch (IOException ignored) {
        }
    }

    static String read(File f) throws IOException {
        if (!f.exists()) return "";
        byte[] b = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    static void write(File f, String text, boolean append) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f, append)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
