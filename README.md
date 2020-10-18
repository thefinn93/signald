# signald - An (unofficial) Signal Daemon


signald is a daemon that facilitates communication over Signal.


## Installation

 - [From source](./docs/install/source.md)
 - [Debian](./docs/install/debian.md)
 - [Docker](./docs/install/docker.md)

## signald clients
* [libpurple](https://github.com/hoehermann/libpurple-signald)
* [matrix](https://github.com/tulir/mautrix-signal)

## Quick Start for developers

1. Startup signald depending on your installation method
1. In a second terminal window, connect to the signald control socket: `nc -U /var/run/signald/signald.sock` (Debian users will need to have `netcat-openbsd` installed)
1. Register a new number on signal by sending something like this : `{"type": "register", "username": "+12024561414"}` (see [link](#link) to add signald as another device on your existing signal account)
1. Once you receive the verification text, submit it like this: `{"type": "verify", "username": "+12024561414", "code": "000-000"}` where `000-000` is the verification code.
1. Incoming messages will be sent to the socket and shown on your screen. To send a message, use something like this:

```json
{"type": "send", "username": "+12024561414", "recipientAddress": {"number": "+14235290302"}, "messageBody": "Hello, Dave"}
```

## Contributing/Feedback/Bugs
[Issues and MRs are accepted via GitLab.com](https://gitlab.com/thefinn93/signald). There is an IRC channel, `#signald` on Freenode,
for those that go for that sort of thing. MRs gladly accepted.


## Stability
This is currently beta software. The public API may have backwards-incompatible, breaking changes before it stabilizes, although we will make an
effort to not do that. Further, there are no guarantees of safety or security with this software.

Breaking changes that have been made:
* As of [!86](https://git.callpipe.com/finn/signald/merge_requests/68), the data folder has moved from `~/.config/signal` to `~/.config/signald`.
  You will need to migrate your data manually, unless you're using the debian package, which should handle this automatically.
* As of [!66](https://git.callpipe.com/finn/signald/merge_requests/66), JSON keys that previously had values of `null` will simply not be sent.

## Interacting with signald

### Use a library
signald's protocol can be somewhat annoying to interact with, and several libraries are available to assist with that:

* Python:
  * [pysignald](https://pypi.org/project/pysignald/) - general purpose signald library
  * [Semaphore](https://github.com/lwesterhof/semaphore) - a simple (rule-based) bot library for Signal Private Messenger in Python
* Go:
  * [signald-go](https://git.callpipe.com/finn/signald-go) - a signald library in go

### Write a library
When started, signald will create a unix socket at `/var/run/signald/signald.sock` (can be overridden on the command line).
To interact with it, connect to that socket and send new line (`\n`) terminated JSON strings. The specific protocol is described below.

## Socket protocol.
Each message sent to the control socket must be valid JSON and have a `type` field. The possible message types and their
arguments are enumerated below. All messages may optionally include an `id` field. When signald follows up on a previous
command, it will include the same `id` value. Most commands (but not all) require `username` field, which is the number
to use for this action, as multiple numbers can be registered with signald at the same time.

### `send`
Sends a signal message to another user or a group. Possible values are:

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The signal number you are sending *from*. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | no | The address you are sending to. Required if not sending to a group |
| `recipientGroupId` | string | no | The base64 encoded group ID to send to. Required if sending to a group |
| `messageBody` | string | no | The text of the message. |
| `attachments` | list of [`JsonAttachment`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/JsonAttachment) | no | A list of attachments |
| `quote` | [`JsonQuote`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/JsonQuote) | no | The message to quote |
| `timestamp` | int | The timestamp (in milliseconds) for the message, which also acts as the message identifier. Defaults to the current time. |

### `register`

Begins the process of registering a new number on signal for use with signald. Possible values are:

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The phone number to register |
| `captcha` | string | no | The captcha value to use, if you get `CaptchaRequiredException` when trying to register. See the [Captchas](https://gitlab.com/thefinn93/signald/-/wikis/Captchas) wiki page for info. |
| `voice` | boolean | no | Indicates if the verification code should be sent via a phone call. If `false` or not set the verification is done via SMS |


### `verify`

Completes the registration process, by providing a verification code sent after the `register` command. Possible values are:

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The phone number that is being verified |
| `code` | string | yes | The verification code. The `-` in the middle code is optional.


### `typing_started`

Send a typing started message.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to send the typing message. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | yes | The full number to send typing message to. |
| `recipientGroupId` | string | no | The base64 encoded group ID. |


### `typing_stopped`

Send a typing stopped message.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to send the typing message. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | yes | The full number to send typing message to. |
| `recipientGroupId` | string | no | The base64 encoded group ID. |


### `mark_read`

Mark a received message as "read" by sending a receipt message.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to send the read receipt. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | yes | The full number that sent the original message. |
| `timestamps` | `list of numbers` | yes | The timestamps of the messages to mark as read. |
| `when` | `number` | no | The timestamp of when the message was read. If omitted, defaults to the current time. |


### `add_device`

Adds another device to a signal account that signald controls the master device on. Possible values are:

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The account to add the device to. |
| `uri` | string | yes | The `tsdevice:` URI that is provided by the other device (displayed as a QR code normally) |


### `list_accounts`

Returns a list of all currently known accounts in signald, including ones that have not completed registration. No other fields are used.


### `list_groups`

Returns a list of all groups the specified user is in.

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The account to list the groups of |

### `update_group`

Creates or modifies a group. Only specify fields that should be updated.

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The account to use to update the group |
| `recipientGroupId` | string | no | The base64 encoded group ID. If left out, a new group will be created. |
| `groupName` | string | no | The value to set the group name to. |
| `members` | list of strings | no | A list of users (eg full international format phone numbers) that should be added to the group. |
| `groupAvatar` | string | no | The avatar to set as the group's avatar. Actual format unknown, probably a path to a file on the disk |

### `leave_group`

Leaves a group

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | string | yes | The account to leave the group |
| `recipientGroupId` string | yes | the base64 encoded group ID |

### `link`

Adds a new account to signald by linking to another signal device that has already registered. Provides a URI that should be used to
link. To link with the Signal app, encode the URI as a QR code, open the Signal app, go to settings -> Linked Devices, tap the + button
in the bottom right and scan the QR code.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `deviceName` | string | no | The device name to show in the "Linked Devices" section in the Signal app. Defaults to "signald" |

### `get_user`

Checks whether a contact is currently registered with the server. Returns the contact's registration state.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to use to check the registration. It may be possible remove this requirement |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | yes | The address of the user to look up. |


### `get_identities`

Returns all known identities/keys, optionally just for a specific number.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to check the identity |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | no | The full number to look up. |


### `trust`

Trust's a safety number or fingerprint.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to check the identity |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | yes | The full number to look up. |
| `fingerprint` | `string` | yes | The safety number or fingerprint to trust. |
| `trustLevel` | `string` | no | The level at which to trust the identity. |

If `trustLevel` is not specified, defaults to `TRUSTED_VERIFIED`. Possible values are:

- `TRUSTED_VERIFIED`
- `TRUSTED_UNVERIFIED`
- `UNTRUSTED`

### `version`

Returns the version of signald in use

### `subscribe`

Causes inbound messages to the specified account to be sent to the socket. If no clients are subscribed to a given account, signald
will not listen for messages from the Signal server and the server will store them until a signald begins receiving again.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The user to subscribe to messages for. |

### `unsubscribe`

Unsubscribes from messages to the specified account. See `subscribe` for more details.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The user to unsubscribe to messages for. |

### `sync_contacts`

Sends a contact sync request to the other devices on this account.

**NOTE**: Sync responses are received like all other messages, and won't come in until that account is subscribed.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to sync contacts for. |

### `sync_groups`

Sends a group sync request to the other devices on this account.

**NOTE**: Sync responses are received like all other messages, and won't come in until that account is subscribed.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to sync contacts for. |

### `sync_configuration`

Sends a configuration sync request to the other devices on this account.

**NOTE**: Sync responses are received like all other messages, and won't come in until that account is subscribed.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to sync contacts for. |

### `list_contacts`

Lists all of the contacts in the contact store for the specified user.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to list the contacts of |


### `update_contact`

Create or update a contact in our contact store.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to update contacts for. |
| `contact` | `contact` | yes | The contact to create or update. |


**contact** objects can have these keys:

| Field | Type   | Required? | Description |
|-------|--------|-----------|-------------|
| `number` | `string` | yes | The phone number of the contact. If no contact exists with this number, a new one will be created. |
| `name` | `string` | no | The name for this contact. |
| `color` | `string` | no | The color for conversations with this contact. |

### `set_expiration`

Sets or changes the expiration time for messages in a group or PM.

As one might expect, `recipientAddress` and `recipientGroupId` are mutually exclusive and one of them is required.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to use. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | no | The address to change the expiration with. |
| `recipientGroupId` | `string` | no | The group ID to update expiration for. |
| `expiresInSeconds` | `int` | yes | The number of seconds after which messages in the conversation should expire. Set to 0 to turn of disappearing messages. |


### `get_profile`

Gets a user's profile. At this time only the name is available. Must have the user's profileKey already, otherwise you'll get a `profile_not_available`.

| Field             | Type     | Required | Description |
|-------------------|----------|----------|-------------|
| `username`        | `string` | yes      | The account to use. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | yes      | The number of the user who's profile is being checked. |


### `set_profile`

Sets the user's profile. At this time only the name is available.

| Field      | Type     | Required | Description |
|------------|----------|----------|-------------|
| `username` | `string` | yes      | The account to use. |
| `name`     | `string` | yes      | The number of the user who's profile is being checked. |

### `react`

React to a message. For details see the [`JsonReaction`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonReaction) wiki page.

| Field      | Type     | Required | Description |
|------------|----------|----------|-------------|
| `username` | `string` | yes      | The account to use. |
| `recipientAddress` | [`JsonAddress`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonAddress) | no | The address you are sending to. Required if not sending to a group |
| `recipientGroupId` | string | no | The base64 encoded group ID to send to. Required if sending to a group |
| `reaction` | [`JsonReaction`](https://gitlab.com/thefinn93/signald/-/wikis/Protocol/v1/JsonReaction) | yes | the reaction message to send |

## Transition An Account From signal-cli

signald's on-disk data structures are generally the same as or very similar to signal-cli's. Until recently, signald used the same
location to store the accounts on the disk. To transition all of your accounts from signal-cli to signald, simply rename `~/.config/signal`
to `~/.config/signald`. Please note that you should **not** copy and use the same account with both programs. Link them to the same user
if you would like to use both signald and signal-cli.

If you have installed the `.deb` and are using the system-wide signald service, copy to `/var/lib/signald`

## License
This software is licensed under the GPLv3. It is based on [signal-cli](https://github.com/Asamk/signal-cli)

## Contributing
Contributions are welcome and appreciated. for larger changes, consider reaching out first (via the issue tracker, IRC or email).  
