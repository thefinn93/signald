# Installation using docker

Pull the image from Docker Hub

`docker pull finn/signald`

# Running the image

The container will create a socket: `/var/run/signald/signald.sock`. To access the socket from outside the container, mount a directory to `/var/run/signald`. You will be sending messages to the process in the container.

```bash
# Ensure you create the directory before mounting it
mkdir run

docker run \
    -v "$PWD/run":/var/run/signald \
    finn/signald
```

_Hint: Use `docker --rm` if you're just testing and want the container removed once you hit Ctrl+C_
