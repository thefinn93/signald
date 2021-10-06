# signald - An (unofficial) Signal Daemon

<<<<<<< HEAD

## Docker image is modified by Deanosim to run openjdk/jre using openj9 instead of hotspot

=======
a daemon that facilitates communication over Signal. It is unofficial, unapproved, and [not nearly as secure as the real Signal clients](https://gitlab.com/signald/signald/-/issues/101)
>>>>>>> 7fb323214faf9924355b73a6a383ce8c0137c8d0

Signal does not offer any sort of official API. Unlike traditional messaging applications, the Signal server expects the
client software to preform encryption and key management. signald handles all of these client-side requirements and
exposes a plain-text API which can be easily used by developers to build custom Signal clients.

Documentation is available on [signald.org](https://signald.org)

## Installation

* [From source](https://signald.org/articles/install/source/)
* [Debian](https://signald.org/articles/install/debian/)
* [Docker](https://signald.org/articles/install/docker/)

## Usage

When started, signald will create a unix socket file which clients connect to. Clients software uses this socket file to
interact with signald. Most users will want to use an existing [client](https://signald.org/articles/clients/).
Developers wanting to write their own clients should consult the list of [libraries](https://signald.org/articles/libraries/).
Alternatively, build your own library: the socket protocol is documented in a machine-readable format that can be used to
generate libraries. For details, see the [Protocol Documentation](https://signald.org/articles/protocol/documentation/) page.

## Contributing/Questions/Feedback/Bugs

[Issues and MRs are accepted via GitLab.com](https://gitlab.com/signald/signald). There is also an [IRC/matrix channel](https://signald.org/articles/IRC/).
if you have a question, open an issue or come by the channel. Some aspects of signald aren't well documented, don't be afraid to ask "stupid" questions.

## License
This software is licensed under the GPLv3. See `LICENSE` file in this repository.
