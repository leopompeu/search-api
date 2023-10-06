package com.serachapi.backend;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import spark.HaltException;

import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static spark.Spark.*;

public class Main {
    
    //The list of used ID's
    static ArrayList<String> idList = new ArrayList<>();

    //The list of link which will be searched
    static ArrayList<String> linksList = new ArrayList<>();

    //The list of search results
    static ArrayList<ArrayList<String>> searchArray = new ArrayList<>();

    //The list of the status for each ID
    static ArrayList<String> status = new ArrayList<>();

    //The list of already used strings to search
    static ArrayList<String> usedStrings = new ArrayList<>();

    //The current last index for ID list
    static Integer index = 0;

    //The secure random variable
    static SecureRandom rnd = new SecureRandom();

    //Provided URL to be scanned
    static String providedUrl = System.getenv("BASE_URL");

    //The alphanumeric range for the ID generation
    static final String alphanumericRange = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static void main(String[] args) throws Exception {
        if(!providedUrl.isBlank()) {
            correctProvidedUlr();
            linksList.add(providedUrl);
            getLinks();

            get("/crawl/:id", (req, res) -> {
                if (idList.contains(req.params(":id"))) {
                    res.type("application/json");
                    return getResponse(req.params(":id"));
                }
                return null;
            });

            post("/crawl", (req, res) -> {
                res.type("application/json");
                JsonObject object = JsonParser.parseString(req.body()).getAsJsonObject();
                if (object.has("keyword")) {
                    String keyword = object.get("keyword").getAsString();
                    if (validateStringLength(keyword)) {
                        return receiveString(keyword);
                    } else {
                        return getHaltException(keyword);
                    }
                } else {
                    return halt(400, "{\"status\":400,\"message\":\"Field 'keyword' is missing!\"}");
                }
            });

            notFound((req, res) -> {
                res.type("application/json");
                if(req.uri().contains("/crawl/") && req.uri().length() == 15){
                    return "{\"status\":404,\"message\":\"crawl not found: " + req.uri().replace("/crawl/", "") + "\"}";
                }
                return "{\"status\":404,\"message\":\"This page was not found.\"}";
            });

        } else {
            throw new Exception("The environment variable 'BASE_URL' is blank.");
        }
    }

    //This class guarantees that the url has the "/" at its end
    static void correctProvidedUlr(){
        if(!String.valueOf(providedUrl.charAt(providedUrl.length() - 1)).equals("/")){
            providedUrl = providedUrl + "/";
        }
    }

    //This class will search for all the links in the provided website
    static void getLinks() throws Exception{
        for(int i = 0; i < linksList.size(); i++) {
            pageMatcher(setConnection(new URL(linksList.get(i)).openConnection()));
        }
    }

    //This class will set up the connection for the links
    static String setConnection(URLConnection connection) {
        String content = "";
        try {
            Scanner scanner = new Scanner(connection.getInputStream());
            scanner.useDelimiter("\\Z");
            content = scanner.next();
            scanner.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return content;
    }

    //This class will select the links that will be scanned
    static void pageMatcher(String content){
        Pattern linkPattern = Pattern.compile("href=\"(.*?)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher pageMatcher = linkPattern.matcher(content);
        while (pageMatcher.find()) {
            if (pageMatcher.group().contains(".html") && !pageMatcher.group().contains(providedUrl)) {
                String link = providedUrl + pageMatcher.group()
                        .replace("href=", "")
                        .replaceAll("\"", "")
                        .replaceAll("/../", "/")
                        .replace("//", "/");
                if (!linksList.contains(link)) {
                    linksList.add(link);
                }
            } else if(pageMatcher.group().contains(providedUrl)){
                String link = pageMatcher.group()
                        .replace("href=", "")
                        .replaceAll("\"", "")
                        .replaceAll("/../", "/")
                        .replace("//", "/");
                if (!linksList.contains(link)) {
                    linksList.add(link);
                }
            }
        }
    }

    //This class will filter the string passed by the user in the requisition body
    static JsonObject receiveString(String string) {
        JsonObject response = new JsonObject();
        addProperties(string);
        response.addProperty("id", idList.get(index));
        index++;
        ExecutorService service = Executors.newFixedThreadPool(4);
        service.submit(() -> searchString(linksList, string));
        return response;
    }

    //This class will set the 'halt' value for the invalid string
    static HaltException getHaltException(String string){
        if (string.length() < 4) {
            return halt(400, "{\"status\":400,\"message\":\"The string entered must have at least 4 characters\"}");
        } else if(string.length() > 32){
            return halt(400, "{\"status\":400,\"message\":\"The string entered cannot be longer than 32 characters\"}");
        } else if (usedStrings.contains(string)) {
            return halt(400, "{\"status\":400,\"message\":\"This string was already used for this id: " + idList.get(usedStrings.indexOf(string)) + "\"}");
        }
        return null;
    }

    //This class will set the string as valid or invalid
    static Boolean validateStringLength(String string){
        return string.length() > 4 && string.length() < 32 && !usedStrings.contains(string);
    }

    //This class add the properties for the GET response
    static void addProperties(String string){
        usedStrings.add(string);
        idList.add(randomString());
        status.add("active");
    }

    //This class will search for the provided String in the URLs that were found by the getLinks() class
    static void searchString(ArrayList<String> urls, String string){
        String content;
        URLConnection connection;
        ArrayList<String> urlsListed = new ArrayList<>();
        searchArray.add(urlsListed);
        try {
            for (String url : urls) {
                connection = new URL(url).openConnection();
                content = setConnection(connection);
                if (Pattern.compile(Pattern.quote(string), Pattern.CASE_INSENSITIVE).matcher(Objects.requireNonNull(content)).find()) {
                    urlsListed.add(url);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        status.set(usedStrings.indexOf(string), "done");
    }

    //This class will build the response to the GET requisition on the provided id scope
    static JsonObject getResponse(String id){
        JsonObject response = new JsonObject();
        response.addProperty("id", id);
        response.addProperty("status", status.get(idList.indexOf(id)));
        Gson gson = new Gson();
        JsonArray urlArray = gson.toJsonTree(searchArray.get(idList.indexOf(id))).getAsJsonArray();
        response.add("urls", urlArray);
        return response;
    }

    //This class will generate a random String with 8 characters in the alphanumeric range defined.
    static String randomString(){
        StringBuilder sb = new StringBuilder(8);
        for(int i = 0; i < 8; i++)
            sb.append(alphanumericRange.charAt(rnd.nextInt(alphanumericRange.length())));
        return sb.toString();
    }
}