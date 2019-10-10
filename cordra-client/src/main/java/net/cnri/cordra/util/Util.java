package net.cnri.cordra.util;

import java.io.InputStream;
import java.util.List;

import net.cnri.cordra.api.*;

public class Util {

    public static void copyObject(CordraObject co, CordraClient destination) throws Exception {
        destination.create(co);
    }

    public static void copyObjectWithoutPayloads(String id, CordraClient source, CordraClient destination) throws Exception {
        CordraObject co = source.get(id);
        copyObject(co, destination);
    }

    public static void copyObjectsWithoutPayloads(List<String> ids, CordraClient source, CordraClient destination) throws Exception {
        for (String id : ids) {
            CordraObject co = source.get(id);
            copyObject(co, destination);
        }
    }

    public static void copyObjectsWithoutPayloads(SearchResults<CordraObject> objects, CordraClient destination) throws Exception {
        for (CordraObject co : objects) {
            copyObject(co, destination);
        }
    }

    public static void copyObjectWithPayloads(CordraObject co, CordraClient source, CordraClient destination) throws Exception {
        if (co.payloads != null) {
            CordraObject sourceCo = source.get(co.id);
              for (Payload payload : sourceCo.payloads) {
                  @SuppressWarnings("resource")
                  InputStream in = source.getPayload(co.id, payload.name);
                  co.addPayload(payload.name, payload.filename, payload.mediaType, in);
              }
          }
        if ("User".equals(co.type)) {
            co.content.getAsJsonObject().addProperty("password", "changeit");
        }
           copyObject(co, destination);
       }

       public static void copyObjectsWithPayloads(SearchResults<CordraObject> objects, CordraClient source, CordraClient destination) throws Exception {
           for (CordraObject co : objects) {
            copyObjectWithPayloads(co, source, destination);
           }
       }

    public static void deleteObjects(List<String> ids, CordraClient client) throws CordraException {
        for (String id : ids) {
            client.delete(id);
        }
    }

    public static void deleteObjects(SearchResults<String> results, CordraClient client) throws CordraException {
        for (String id : results) {
            client.delete(id);
        }
    }

    public static void deleteResultObjects(SearchResults<CordraObject> results, CordraClient client) throws CordraException {
        for (CordraObject co : results) {
            client.delete(co.id);
        }
    }

    public static void deleteMatchingObjects(String query, CordraClient client) throws CordraException {
        try (SearchResults<String> results = client.searchHandles(query)) {
            deleteObjects(results, client);
        }
    }

//    public static void main(String[] args) throws Exception {
//        CordraClient destination = new HttpCordraClient("http://localhost:8082", "admin", "changeit");
//        CordraClient source = new HttpCordraClient("http://localhost:8081", "admin", "changeit");
//
//        deleteMatchingObjects("*:*", destination);
//        SearchResults<CordraObject> schemas = source.search("type:Schema");
//        copyObjectsWithPayloads(schemas, source, destination);
//        SearchResults<CordraObject> objects = source.search("*:* -type:Schema");
//        copyObjectsWithPayloads(objects, source, destination);
//    }
}
