package org.prebid.mobile.testutils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public interface ParseCallable<T> {
        T call(JSONArray jsonArray, int index) throws JSONException;
    }

    public static <T> List<T> getList(JSONArray jsonArray, ParseCallable<T> callable) throws Exception {

        List<T> list = new ArrayList<>(jsonArray.length());

        for (int i = 0; i < jsonArray.length(); i++) {
            T t = callable.call(jsonArray, i);
            list.add(t);
        }

        return list;

    }

    public static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            if (line.isEmpty()) {
                break;
            }
        }
        return builder.toString();
    }
}
