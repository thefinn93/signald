# Guide to linking `signald` to an existing account using `qrencode`

## Requirements
* A phone with signal and pre-existing signal account
* `signald` installed
* `qrencode` installed

## Instructions
### Step 1:
Ensure that `signald` is running, **and** that `signald` has finished initalizing. Look for a message similar to the following, if checking the output of `signald`:

```
23:34:14.665 [main] INFO  signald - Started signald 0.7.0+git2019-03-24rf98e1fcb.44
```

Once `signald` is ready, connect via netcat (aka `nc`):

```bash
netcat -U /var/run/signald/signald.sock
```

### Step 2:
Once connected, issue the link command by entering the following into `netcat` and pressing enter:

```json
{"type": "link"}
```

### Step 3:
After a little wait you should recieve a notice in the following format:

```json
{"type":"linking_uri","data":{"uri":"tsdevice:/?uuid=o_0123456789abcedfghij&pub_key=0123456789abcedfghijklmnopqrstuvwxyz01234567"}}
```
**NOTE:** As soon as this notice arrives, you will have a limited time to finish the linking process.


### Step 4:
Copy the returned uri (the double quoted string staring with 'tsdevice'), and use qrencode to output a qr code in the terminal with:

```bash
qrencode -t ANSI "tsdevice:/?uuid=o_0123456789abcedfghij&pub_key=0123456789abcedfghijklmnopqrstuvwxyz01234567"
```

**NOTE:** Both ANSI and ASCII qr code outputs will be larger than a minimal 80x24 terminal.

### Step 5:
Scan the qr code in your phone's singal app.  

**NOTE:** Refer to the singnal [documentation](https://support.signal.org/hc/en-us/articles/360007320551-Linked-Devices).

### Done
Your `signald` instance should now be linked!

## Document Revision History
v1.0.0 - 2019-04-01: Created by demure.
