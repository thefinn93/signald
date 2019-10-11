# Installation using docker

Pull the image from Docker Hub

`docker pull finn/signald`

# Running the image

You will need to map a folder to `/var/run/signald` in the container
in order to access the socket. You will be sending messages to the
process in the container.

Another folder will have to mapped to `/home/gradle` in order to keep
the config. It will then be found in the subfolder `.config/signald`

```bash
mkdir gradle run
docker run \
    -v "$PWD/gradle":/home/gradle \
    -v $PWD/run":/var/run/signald \
    bisentenialwrug:signald
```

_Hint: Use `docker --rm` if you're just testing and want the container removed once you hit Ctrl+C_
