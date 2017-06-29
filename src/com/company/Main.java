package com.company;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    static Map<String, AtomicInteger> libCounts = new ConcurrentHashMap<String, AtomicInteger>();

    public static void main(String[] args) {
        try {
            String term = readFromStdIn();
            String searchUrl = "https://www.google.com.tr/search?q=" + term;
            System.out.println("Searching for: "+term+"\n");
            String serp = getPage(searchUrl);
            List<String> pageLinks = parseLinks(serp);

            startThreads(pageLinks);

            System.out.println("\nTop 5 libraries are:\n");
            displayTopNLibs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String readFromStdIn() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter search term: ");
        String term = reader.readLine();
        return term;
    }

    static String getPage(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        TrustModifier.relaxHostChecking(connection);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ExampleBot 1.0 (+http://example.com/bot)");
        connection.setReadTimeout(2000);
        connection.setConnectTimeout(2000);
        connection.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder stringBuilder = new StringBuilder();

        String line = null;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line + "\n");
        }
        String result = stringBuilder.toString();
        return result;
    }

    static List<String> parseLinks(final String html) throws Exception {
        List<String> result = new ArrayList<String>();
        String pattern1 = "<h3 class=\"r\"><a href=\"/url?q=";
        String pattern2 = "\">";
        Pattern p = Pattern.compile(Pattern.quote(pattern1) + "(.*?)" + Pattern.quote(pattern2));
        Matcher m = p.matcher(html);

        while (m.find()) {
            String domainName = m.group(0).trim();

            domainName = domainName.substring(domainName.indexOf("/url?q=") + 7);
            domainName = domainName.substring(0, domainName.indexOf("&amp;"));

            domainName = java.net.URLDecoder.decode(domainName, "UTF-8");
            result.add(domainName);
        }
        return result;
    }


    static void startThreads(List<String> pageLinks ) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        System.out.println("Search results:");
        for (String page : pageLinks) {
            System.out.println(page);
            Runnable worker = new LibChecker(page);
            executor.execute(worker);
        }
        System.out.println();
        System.out.println("\nGetting pages...\n");
        executor.shutdown();
        while (!executor.isTerminated()) {}
        System.out.println("\nAll threads finished");
    }

    static void displayTopNLibs() {
        Map<String, Integer> map = getLibCountsMapSortedByValue();
        Set eset = map.entrySet();
        Iterator it = eset.iterator();
        int count = 0;
        while (it.hasNext()) {
            if (count == 5) break;
            Map.Entry me = (Map.Entry) it.next();
            System.out.print(me.getKey() + ": ");
            System.out.println(me.getValue() + " occurences");
            count = count + 1;
        }
    }

    static Map getLibCountsMapSortedByValue() {
        // Since AtomicInteger is not comparable, turn it into a hashmap with Integer values
        Map<String, Integer> normalMap = new HashMap<String, Integer>();
        Set eset=libCounts.entrySet();
        Iterator it= eset.iterator();
        while (it.hasNext()) {
            Map.Entry me = (Map.Entry) it.next();
            normalMap.put(me.getKey().toString(), new Integer(((AtomicInteger) me.getValue()).get()));
        }

        List list = new LinkedList(normalMap.entrySet());
        Collections.sort(list, new Comparator() {
            public int compare(Object o1, Object o2) {
                return -1 * ((Comparable) ((Map.Entry) (o1)).getValue())
                        .compareTo(((Map.Entry) (o2)).getValue());
            }
        });

        HashMap sortedHashMap = new LinkedHashMap();
        for (it = list.iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            sortedHashMap.put(entry.getKey(), entry.getValue());
        }
        return sortedHashMap;
    }

    static class LibChecker implements Runnable {
        private final String url;

        LibChecker(String url) {
            this.url = url;
        }

        @Override
        public void run() {
            String content = null;
            try {
                content = getPage(url);
                System.out.println("got page: "+url);
                Set<String> libs = getJavascriptLibNames(content);
                if (!libs.isEmpty()) {
                    for (String lib : libs) {
                        AtomicInteger count= new AtomicInteger(1);
                        AtomicInteger countRet= libCounts.putIfAbsent(lib, count);
                        if (countRet!=null) {
                            countRet.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error processing page: "+url);
            }
        }
    }

    static Set<String> getJavascriptLibNames(String pageContent) {
        String regex = "(\\w*)\\.js";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(pageContent);
        Set<String> jsSet = new HashSet<String>();

        while (matcher.find()) {
            jsSet.add(matcher.group(1)+".js");
        }
        return jsSet;
    }
}
