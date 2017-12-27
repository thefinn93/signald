package org.asamk.signal;

import org.whispersystems.signalservice.internal.util.Base64;

public class GroupNotFoundException extends Exception {

    public GroupNotFoundException(String message) {
        super(message);
    }

    public GroupNotFoundException(byte[] groupId) {
        super("Group not found: " + Base64.encodeBytes(groupId));
    }
}
