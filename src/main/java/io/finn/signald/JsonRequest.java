package io.finn.signald;

import java.util.List;

class JsonRequest {
    public String type;
    public String id;
    public String messageBody;
    public String recipientNumber;
    public String sourceNumber;
    public String recipientGroupId;
    public List<String> attachmentFilenames;

    JsonRequest() {}
}
