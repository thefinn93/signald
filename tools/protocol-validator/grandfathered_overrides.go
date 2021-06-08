package main

var (
	uppercaseFields = map[string]map[string][]string{
		"v0": {
			"ConfigurationMessage":            []string{"readReceipts", "unidentifiedDeliveryIndicators", "typingIndicators", "linkPreviews"},
			"HangupMessage":                   []string{"deviceId"},
			"JsonAccount":                     []string{"deviceId"},
			"JsonAttachment":                  []string{"contentType", "storedFilename", "customFilename", "voiceNote"},
			"JsonCallMessage":                 []string{"destinationDeviceId", "isMultiRing", "offerMessage", "answerMessage", "busyMessage", "hangupMessage", "iceUpdateMessages"},
			"JsonTypingMessage":               []string{"groupId"},
			"JsonStickerPackOperationMessage": []string{"packKey", "packID"},
			"JsonQuotedAttachment":            []string{"contentType", "fileName"},
			"JsonSticker":                     []string{"deviceId", "packID", "packKey", "stickerID"},
			"RemoteDelete":                    []string{"targetSentTimestamp"},
			"Success":                         []string{"needsSync"},
		},
		"v1": {
			"AcceptInvitationRequest":           []string{"groupID"},
			"ApproveMembershipRequest":          []string{"groupID"},
			"GroupList":                         []string{"legacyGroups"},
			"GetGroupRequest":                   []string{"groupID"},
			"JsonDataMessage":                   []string{"endSession", "profileKeyUpdate", "remoteDelete", "groupV2", "expiresInSeconds", "viewOnce"},
			"JsonGroupV2Info":                   []string{"pendingMembers", "accessControl", "pendingMemberDetail", "requestingMembers", "inviteLink", "memberDetail"},
			"JsonMessageEnvelope":               []string{"dataMessage", "syncMessage", "sourceDevice", "timestampISO", "isUnidentifiedSender", "hasLegacyMessage", "hasContent", "callMessage", "serverTimestamp", "serverDeliveredTimestamp"},
			"JsonSendMessageResult":             []string{"identityFailure", "networkFailure", "unregisteredFailure"},
			"RemoveLinkedDeviceRequest":         []string{"deviceId"},
			"SendRequest":                       []string{"recipientAddress", "recipientGroupId", "messageBody"},
			"ReactRequest":                      []string{"recipientAddress", "recipientGroupId"},
			"JsonGroupInfo":                     []string{"avatarId", "groupId"},
			"JsonBlockedListMessage":            []string{"groupIds"},
			"JsonSentTranscriptMessage":         []string{"expirationStartTimestamp", "unidentifiedStatus", "isRecipientUpdate"},
			"JsonMessageRequestResponseMessage": []string{"groupId"},
			"JsonGroupJoinInfo":                 []string{"groupID", "memberCount", "addFromInviteLink", "pendingAdminApproval"},
			"SetProfile":                        []string{"avatarFile"},
			"LeaveGroupRequest":                 []string{"groupID"},
			"DeviceInfo":                        []string{"lastSeen"},
			"JsonReaction":                      []string{"targetAuthor", "targetSentTimestamp"},
			"JsonSyncMessage":                   []string{"blockedList", "readMessages", "stickerPackOperations", "contactsComplete", "fetchType", "messageRequestResponse", "viewOnceOpen"},
			"UpdateGroupRequest":                []string{"updateTimer", "addMembers", "removeMembers", "updateRole", "updateAccessControl", "resetLink", "groupID"},
			"JsonVerifiedMessage":               []string{"identityKey"},
		},
	}
)
