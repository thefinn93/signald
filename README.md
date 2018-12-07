# signald - An (unofficial) Signal Daemon


signald is a daemon that facilitates communication over Signal.


## Quick Start

*if you run Debian and would prefer an apt repo, see [Debian Installation](#debian-installation) below*

1. Run `make installDist` to build signald
1. Run `make setup` to configure the system directories
1. Run `build/install/signald/bin/signald` to start signald. It will continue running until killed (or ctrl-C)
1. In a second terminal window, connect to the signald control socket: `nc -U /var/run/signald/signald.sock`
1. Register a new number on signal by typing this: `{"type": "register", "username": "+12024561414"}` (replace `+12024561414` with your own number)
1. Once you receive the verification text, submit it like this: `{"type": "verify", "username": "+12024561414", "code": "000-000"}` where `000-000` is the verification code.
1. Incoming messages will be sent to the socket and shown on your screen. To send a message, use something like this:

```json
{"type": "send", "username": "+12024561414", "recipientNumber": "+14235290302", "messageBody": "Hello, Dave"}
```

## Contributing
[Issues and MRs are accepted via GitLab.com](https://gitlab.com/thefinn93/signald). There is an IRC channel, `#signald` on Freenode,
for those that go for that sort of thing. MRs gladly accepted.


## Stability
This is currently beta software. The public API may have backwards-incompatible, breaking changes before it stabilizes, although we will make an
effort to not do that. Further, there are no guarantees of safety or security with this software.

## Interacting with signald

### Use a library
signald's protocol can be somewhat annoying to interact with, and several libraries are available to assist with that:

* Python:
  * [pysignald](https://pypi.org/project/pysignald/) - a third party signald library in python
  * [Janky Signal Bot Framework](https://github.com/JankySolutions/SignalBotFramework) - a janky framework for writing simple call and response bots
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
| `recipientNumber` | string | no | The number you are sending to. Required if not sending to a group |
| `recipientGroupId` | string | no | The base64 encoded group ID to send to. Required if sending to a group |
| `messageBody` | string | no | The text of the message. |
| `attachmentFilenames` | list of strings | no | A list of files to attach, by path on the local disk. |
| `quote` | quote | no | The message to quote |

**Quote objects** can have these keys:

| Field | Type   | Required? | Description |
|-------|--------|-----------|-------------|
| `id`  | number | yes\*      | The timestamp of the original message. |
| `author` | string | yes\*   | The username (full e164 phone number) of the author of the quoted message. |
| `text` | string | yes\*     | The text of the quoted message. |
| `attachments` | list of quoted attachments | no | A list of attachments in the quoted message. |

\* If you don't put these values it will send it but the Signal app doesn't seem to render it (Signal Desktop does though?)


**Quoted** attachment objects can have these keys:

| Field | Type   | Required? | Description |
|-------|--------|-----------|-------------|
| `contentType` | string | yes | The content type of the quoted attachment |
| `fileName` | string | no | The original filename of the quoted attachment |


### `register`

Begins the process of registering a new number on signal for use with signald. Possible values are:

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The phone number to register |
| `voice` | boolean | no | Indicates if the verification code should be sent via a phone call. If `false` or not set the verification is done via SMS |


### `verify`

Completes the registration process, by providing a verification code sent after the `register` command. Possible values are:

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `username` | string | yes | The phone number that is being verified |
| `code` | string | yes | The verification code. The `-` in the middle code is optional.


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
*Takes no argument*

### `get_user`

Checks whether a contact is currently registered with the server. Returns the contact's registration state.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to use to check the registration. It may be possible remove this requirement |
| `recipientNumber` | `string` | yes | The full number to look up. |


### `get_identities`

Returns all known identities/keys, optionally just for a specific number.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to check the identity |
| `recipientNumber` | `string` | no | The full number to look up. |


### `trust`

Trust's a safety number or fingerprint.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The local account to use to check the identity |
| `recipientNumber` | `string` | yes | The full number to look up. |
| `fingerprint` | `string` | yes | the safety number or fingerprint to trust. |

### `version`

Returns the version of signald in use

### `subscribe`

Causes inbound messages to the specified account to be sent to the socket. If no clients are subscribed to a given account, signald
will not listen for messages from the Signal server and the server will store them until a signald begins receiving again.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The user to unsubscribe to messages for. |

### `unsubscribe`

Unsubscribes from messages to the specified account. See `subscribe` for more details.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The user to unsubscribe to messages for. |

### `list_contacts`

Lists all of the contacts in the contact store for the specified user.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to list the contacts of |

### `sync_contacts`

Sends a contact sync request to the other devices on this account.

**NOTE**: Contact sync responses are received like all other messages, and won't come in until that account is subscribed.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to sync contacts for. |


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
| `name` | `string | no | The name for this contact. |
| `color` | string | no | The color for conversations with this contact. |

### `set_expiration`

Sets or changes the expiration time for messages in a group or PM.

As one might expect, `recipientNumber` and `recipientGroupId` are mutually exclusive and one of them is required.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | yes | The account to use. |
| `recipientNumber` | `string` | no | The PM to change expiration for. |
| `recipientGroupId` | `string` | no | The group ID to update expiration for. |
| `expiresInSeconds` | `int` | yes | The number of seconds after which messages in the conversation should expire. Set to 0 to turn of disappearing messages. |

## Debian Installation

Add the following to your `sources.list`:

```
deb https://updates.signald.org stable main
```

And trust the signing key:

```
curl https://updates.signald.org/apt-signing-key.asc | sudo apt-key add -
```

Now you can install signald:

```
sudo apt install signald
```
## License
This software is licensed under the GPLv3. It is based on [signal-cli](https://github.com/Asamk/signal-cli)

## Contributing
I would like to get this to the point that anything one can do in the signal app can also be done via signald. There should be open issues for all missing features. If you have a feature you want feel free to work on it and submit a pull request. If you don't want to work on it, follow the relevant issue and get notified when there is progress.
