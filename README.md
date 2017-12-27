# signald - An (unofficial) Signal Daemon

signald is a daemon that facilitates communication over Signal.


## Setup

1. Build signald: `./gradlew installDist`
1. Using [signal-cli](https://github.com/Asamk/signal-cli), register your number(s) on signal or link to your existing device(s). Note this step should be available via the control socket soon.
1. Run `signald`. It will be at `build/install/signald/bin/signald` if you ran the command in step 1.

## Usage
When run, `signald` will create a unix domain socket called `signald.sock`. Connect to it (eg `nc -U signald.sock`).
When any registered numbers receive a message, any clients connected to the socket will receive a JSON object with the
message and metadata about it. To send a message, write a JSON object to the socket, like this:

```json
{
  "type": "send",
  "sourceNumber": "+12024561414",
  "recipientNumber": "+14235290302",
  "messageBody": "Hello, Dave"
}
```


Note that due to reasons, it should all be sent on the same line.
