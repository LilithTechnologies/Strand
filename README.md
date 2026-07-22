# Strand
> Open your Minecraft world to anyone, anywhere.

**Note: Strand is an open-alpha mod. Bugs are expected. Please report them to our [issue tracker](https://github.com/LilithTechnologies/Strand/issues)!**

## What does it do?

**Strand opens your Minecraft world to anyone, anywhere.**

Using [peer-to-peer](https://en.wikipedia.org/wiki/Peer-to-peer) networking powered by [Epic Online Services](https://onlineservices.epicgames.com/), a product from Epic Games which offers production-grade P2P networking infrastructure for completely free. Whenever networking conditions allow, players connect directly to one another, reducing latency and avoiding the bottlenecks of a proxy-based architecture. If unavailable, Strand will automatically fall back to the best available relay server and route your traffic through that. Strand lets you host multiplayer worlds directly from your game without needing to configure your router, open ports or otherwise mess with your network. Sign in with your Minecraft account (Offline accounts are not supported!), share an invite code or send player-to-player invites, and connect across the internet as if you were playing on the same LAN.

Strand handles authorisation, session management, invitations, and secure, fast P2P connections behind the scenes, making it easy for you to play with your friends from anywhere in the world.

## Why is it better than existing solutions?

Unlike traditional solutions, Strand is built around true peer-to-peer networking powered by EOS. Whenever possible, Strand will attempt a direct connection (firewall may affect the availability of this!) whenever possible, reducing latency and bandwidth bottlenecks. Even then, Epic's battle-tested, powerful relays all around the globe have proven to be better than traditional solutions with limited relay servers. This means the infrastructure only needs to handle authorisation, invitations and sessions, allowing us to scale to far more players while keeping operating costs at a minimum.

Strand also provides a richer multiplayer experience through its built-in invitation system, allowing players to invite each other by username, receive join requests in-game, and connect with each other without sharing IP addresses, ports or temporary domains. The only caveat is that **vanilla clients cannot connect to a Strand-backed server**, and this is not solvable due to the design of Epic's P2P networking. However, the result feels less like opening a local server to the world and more like a modern multiplayer platform that lets you open your world, send an invite and start playing from anywhere, with anyone (provided they have the mod installed :P).

## How do I use it?

Download the mod, it has [Fabric Language Kotlin](https://modrinth.com/project/Ha28R6CL) and [Fabric API](https://modrinth.com/project/P7dR8mSH) as dependencies, so ensure you have them enabled. Depending on your platform, the first run may take a minute or two as it downloads required files. Once you're in, you can join a session using an **invite code** or if you've otherwise received an invite from a user. To access either, press the button labelled "Strand" in the main menu, the pause menu, and the multiplayer menu. You cannot join a session while already in-game. To host a world, you must be in a singleplayer world. You can use the `/strand` command to open the Strand Hub, through which you can press the "Host this world" button to host a world, "Copy invite code" button to copy the invite code, and the "Invite a player" button to invite a player by name. If you're already hosting a world, you can press the "Stop hosting" button to stop hosting the world. This will disconnect all players, leaving the world or quitting the game has the same effect.

## Privacy

Strand needs to exchange some data with our backend and with Epic Online Services in order to work. 

Essentially,
- We do not, and we cannot see what you do in game.
- We share your data with Epic Games, Inc. to provide login functionality and P2P networking as described in Section 3 of the Privacy Policy.
- We do not use your data for advertising, and we do not build user profiles for any purpose beyond operating Strand.
- We do not sell, rent, or otherwise disclose your data to third parties such as advertisers or data brokers.

This is not the full Privacy Policy but a summary of it. Please read the full [Privacy Policy](PRIVACY_POLICY.md) for more information.

## License

Strand, including its backend, is licensed under AGPL-3.0. See [LICENSE.md](LICENSE.md) for the full license text.

### Epic Online Services (EOS) SDK

This project integrates with the Epic Online Services (EOS) SDK, proprietary software owned by Epic Games, Inc. The EOS SDK is **not** covered by the AGPL-3.0 license above and is governed separately by the [Epic Online Services Terms of Service and Developer Agreement](https://onlineservices.epicgames.com/services/terms/agreements).

This repository does **not** distribute or bundle any EOS SDK binaries (e.g. `EOSSDK-Win64-Shipping.dll`, `libEOSSDK-Linux-Shipping.so`, `libEOSSDK-Mac-Shipping.dylib`). Anyone building this project must obtain the SDK directly from Epic Games under their own developer agreement.

"Epic Online Services," "EOS," and "Epic Games" are trademarks or registered trademarks of Epic Games, Inc.
