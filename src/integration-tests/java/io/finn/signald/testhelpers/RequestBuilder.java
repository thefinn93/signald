package io.finn.signald.testhelpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class RequestBuilder {
    private static ObjectMapper mpr = new ObjectMapper();


    public static String register(String username) {
        ObjectNode req = mpr.createObjectNode();

        req.put("type", "register");
        req.put("username", username);

        return req.toString();
    }


    public static String verify(String username, String code) {
        ObjectNode req = mpr.createObjectNode();

        req.put("type", "verify");
        req.put("username", username);
        req.put("code", code);

        return req.toString();
    }


    public static String listAccounts() {
        ObjectNode req = mpr.createObjectNode();

	req.put("type", "list_accounts");

	return req.toString();
    }
}
