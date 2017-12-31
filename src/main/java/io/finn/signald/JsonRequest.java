package io.finn.signald;

import java.util.List;

class JsonRequest {
    public String type;
    public String id;
    public String username;
    public String messageBody;
    public String recipientNumber;
    public String recipientGroupId;
    public Boolean voice;
    public String code;
    public List<String> attachmentFilenames;

    JsonRequest() {}
}
