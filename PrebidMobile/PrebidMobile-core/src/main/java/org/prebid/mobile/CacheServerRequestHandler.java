/*
 *    Copyright 2018-2019 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;

public class CacheServerRequestHandler implements Runnable {

    private final static int STARTING_LINE_COUNT = 3;
    private final static int STARTING_LINE_METHOD_INDEX = 0;
    private final static int STARTING_LINE_URI_INDEX = 1;
    private final static int STARTING_LINE_HTTP_VERSION_INDEX = 2;
    private final static int HTTP_HEADER_INDEX = 0;

    private final static String OK_RESPONSE = "200 OK";
    private final static String NOT_FOUND_RESPONSE = "404 Not Found";

    private final Socket socket;
    private final HashMap<String, String> cache;

    public CacheServerRequestHandler(Socket socket, HashMap<String, String> cache) {
        this.socket = socket;
        this.cache = cache;
    }

    @Override
    public void run() {
        try {
            LogUtil.d("Running request...");
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            String inputData = readInput(inputStream);
            String[] httpFields = inputData.split("\n");
            if (httpFields.length > 1) {
                String httpHeader = httpFields[HTTP_HEADER_INDEX];
                String[] startingLineFields = httpHeader.split(" ");
                if (startingLineFields.length == STARTING_LINE_COUNT) {
                    String method = startingLineFields[STARTING_LINE_METHOD_INDEX];
                    String uri = startingLineFields[STARTING_LINE_URI_INDEX];
                    String httpVersion = startingLineFields[STARTING_LINE_HTTP_VERSION_INDEX];
                    LogUtil.d(String.format("Parsed: method[%s] uri[%s] version[%s]", method, uri, httpVersion));
                    writeOutput(uri, httpVersion, outputStream);
                }
            }
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            LogUtil.d(e.getMessage());
        }
    }

    private String readInput(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append("\n");
            if (line.isEmpty()) {
                break;
            }
        }
        return builder.toString();
    }

    private void writeOutput(String uri, String httpVersion, OutputStream outputStream) throws IOException {
        String cacheId = uri.replaceFirst("/", ""); // remove first backslash from URI
        String content = "";
        String responseResult;
        if (cache.containsKey(cacheId)) {
            content = cache.get(cacheId);
            responseResult = OK_RESPONSE;
        } else {
            responseResult = NOT_FOUND_RESPONSE;
        }
        responseResult = String.format(Locale.getDefault(), "%s %s\r\n", httpVersion, responseResult);
        StringBuilder responseBuilder = new StringBuilder();
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"));
        responseBuilder.append(responseResult)
                .append(String.format(Locale.getDefault(), "Content-Length: %d\r\n", content.length()))
                .append("Content-Type: text/html; charset=utf-8\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("\r\n")
                .append(content);
        bufferedWriter.write(responseBuilder.toString());
        bufferedWriter.close();
    }
}
