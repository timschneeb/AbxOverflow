package com.example.abxoverflow.droppedapk;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {
    public static String printStream(String tag, InputStream stream, boolean isError) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
            if (isError) {
                Log.e(tag, line);
            } else {
                Log.i(tag, line);
            }
        }
        return output.toString();
    }
}
