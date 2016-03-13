package iai.co.il.streamer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Asaf on 12/03/2016.
 */
public class DataHolder {
    public static ArrayList<Category> categories= new ArrayList<>();
    public static Category selected=null;

    private static ObjectMapper mapper= new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static <T> List<T> jsonToList(String json,TypeReference<List<T>> ref) throws IOException {
        JsonNode node = mapper.readTree(json);
        List<T> list = mapper.readValue(node.traverse(), ref);
        return list;
    }


    public static String objectToJson(Object o) throws JsonProcessingException{
        return mapper.writeValueAsString(o);
    }

    public static <T> T jsonToObject(String json,Class<T> c) throws  IOException{


        return mapper.readValue(json, c);
    }
}
