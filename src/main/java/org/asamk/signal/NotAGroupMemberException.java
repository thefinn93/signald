package org.asamk.signal;

import org.whispersystems.signalservice.internal.util.Base64;

public class NotAGroupMemberException extends Exception {

    public NotAGroupMemberException(String message) {
        super(message);
    }

    public NotAGroupMemberException(byte[] groupId, String groupName) {
        super("User is not a member in group: " + groupName + " (" + Base64.encodeBytes(groupId) + ")");
    }
}
