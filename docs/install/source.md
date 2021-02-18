# Installation from source

1. Install Java (tested against Java 8 and 12)
1. Run `make installDist` to build signald
1. Run `make setup` to configure the system directories

# Running

Run `build/install/signald/bin/signald` to start signald. It will continue running until killed (or ctrl-C)

# Additional tooling

Consider installing [signaldctl](https://gitlab.com/signald/signald-go/-/blob/main/cmd/signaldctl/README.md).