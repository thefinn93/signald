# Debian Installation

Add the following to your `sources.list`:

```
deb https://updates.signald.org master main
```

And trust the signing key:

```
curl https://updates.signald.org/apt-signing-key.asc | sudo apt-key add -
```

Now you can install signald:

```
sudo apt install signald
```
