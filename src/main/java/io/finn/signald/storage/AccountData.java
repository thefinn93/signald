/*
 * Copyright (C) 2020 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.finn.signald.exceptions.InvalidStorageFileException;
import io.finn.signald.util.JSONHelper;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.util.Base64;

import java.io.File;
import java.io.IOException;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountData {
    public String username;
    public String password;
    public int deviceId;
    public String signalingKey;
    public int preKeyIdOffset;
    public int nextSignedPreKeyId;
    public String profileKey;

    public boolean registered;

    public SignalProtocolStore axolotlStore;
    public GroupStore groupStore;
    public ContactStore contactStore;
    public ThreadStore threadStore;

    private static String dataPath;

    public static AccountData load(File storageFile) throws IOException {
        ObjectMapper mapper = JSONHelper.GetMapper();

        // TODO: Add locking mechanism to prevent two instances of signald from using the same account at the same time.
        AccountData a = mapper.readValue(storageFile, AccountData.class);
        a.validate(); // Storage path passed for exceptions to include in messages
        return a;
    }

    public void save() throws IOException {
        save(false);
    }

    public void save(boolean allowBlankPassword) throws IOException {
        validate();

        ObjectWriter writer = JSONHelper.GetWriter();

        File dataPathFile = new File(dataPath);
        if(!dataPathFile.exists()) {
            dataPathFile.mkdirs();
        }
        writer.writeValue(new File(dataPath + "/" + username), this);
    }

    public void validate() throws InvalidStorageFileException {
        if (!PhoneNumberFormatter.isValidNumber(this.username, null)) {
            throw new InvalidStorageFileException("phone number " + this.username + " is not valid");
        }
    }

    public void init() {
        if (groupStore == null) {
            groupStore = new GroupStore();
        }

        if (contactStore == null) {
            contactStore = new ContactStore();
        }

        if (threadStore == null) {
            threadStore = new ThreadStore();
        }

        if (profileKey == null) {
            profileKey = "";
        }
    }

    public static void setDataPath(String path) {
        dataPath = path + "/data";
    }

    public byte[] getProfileKey() throws IOException {
        if(profileKey == null || profileKey.equals("")) {
            return null;
        }
        return Base64.decode(profileKey);
    }

    public void setProfileKey(byte[] key) {
        if(key == null) {
            profileKey = "";
        }
        profileKey = Base64.encodeBytes(key);
    }
}
