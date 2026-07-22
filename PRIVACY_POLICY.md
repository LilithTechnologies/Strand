# Strand Privacy Policy

**Last updated: July 23, 2026**
**Effective date: July 23, 2026**

This policy explains what data Strand ("the Mod," "we," "us," "our") collects, processes, and shares when you use it to host or join a Minecraft world. Strand is developed by Lilith Technologies LLC ("Lilith"), 30 N Gould St Ste R, Sheridan, WY 82801. If you have questions, contact us at hello@lilith.re.

This policy covers the Strand mod and the Strand backend at `strand.lilith.re`. It does not cover Minecraft itself, Mojang/Microsoft, or other mods you may have installed.

By using Strand, you acknowledge that you have read this policy and, where required by law, consent to the practices described in it.

---

## 1. Who this policy applies to, and our role

Lilith acts as the **data controller** for the data described in Section 2, meaning we decide why and how it's processed for purposes of running Strand. For the data described in Section 3, Epic Games, Inc. ("Epic") acts as an independent controller of the data it processes through Epic Online Services ("EOS"), Epic is not our data processor, and we don't control how Epic uses that data beyond what's described in our agreement with Epic.

This policy is written for a general, worldwide audience. Section 8 describes rights that may apply to you depending on where you live.

## 2. What Strand does

Strand lets you host and join Minecraft worlds over the internet without port forwarding, by authenticating players and brokering peer-to-peer (P2P) connections through Epic Online Services, a service operated by Epic. To do this, Strand and Epic both need to process some data about you and your session.

## 3. Data we collect

| Data                           | Why we collect it                                                                                                                                                       | Where it comes from                                         | Retention                                                                           |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Minecraft UUID and username    | To identify your account and create/link your Strand and EOS identities                                                                                                 | Read locally from your Minecraft profile when you log in    | Retained while your account is active; deleted within 30 days of a deletion request |
| Session tokens / access tokens | To authenticate your requests to the Strand backend and to EOS, without repeatedly re-sending credentials                                                               | Issued by the Strand backend and EOS during login           | Expire automatically (typically within hours); not retained after expiry            |
| EOS Product User ID (PUID)     | To link your Minecraft account to an EOS identity for P2P networking                                                                                                    | Created by EOS during login, linked via our backend         | Retained while your account is active; deleted within 30 days of a deletion request |
| Hosting/session metadata       | To let others join your world; includes a generated invite code, an internal session ID, and a socket name used to route P2P traffic                                    | Created when you start hosting                              | Deleted when the session ends, or automatically after [X days] of inactivity        |
| Invite data                    | To deliver invites; includes the inviter's and invitee's usernames/PUIDs and invite status (pending/accepted/declined)                                                  | Created when you or another player send an invite           | Deleted once resolved (accepted/declined/expired), or after [X days]                |
| Invite-privacy setting         | To respect your choice of who can invite you (open invites vs. code-only)                                                                                               | Set by you in-game                                          | Retained while your account is active                                               |
| Connection metadata            | To establish and maintain P2P links; includes your IP address (visible to peers or Epic's relay servers, depending on connection type), NAT type, and connection status | Generated during play by the EOS P2P service                | Not stored by Lilith beyond the active session; see Section 4 for Epic's handling   |
| Diagnostic/error logs          | To debug connectivity and backend issues; includes timestamps, error codes, and the session/account identifiers above                                                   | Generated automatically by the Strand backend during errors | Retained up to [X days], then deleted or anonymized                                 |

We do **not** collect chat messages, world data, or gameplay content. Strand only bridges raw network traffic between you and other players; it does not inspect or log the contents of that traffic.

We do not use cookies or similar tracking technologies, since Strand operates as a Minecraft mod and backend service rather than a website with a user-facing login.

## 4. Epic Online Services

Login and networking are powered by Epic Online Services. Using Strand means some of your data is also processed by Epic, under Epic's own [Developer Agreement](https://onlineservices.epicgames.com/services/terms/agreements) and Epic's privacy policy, including:

- **Connect login**: Strand exchanges an authentication token with Epic to create or retrieve an EOS Product User ID for you.
- **Peer-to-peer networking**: When you play, Epic's infrastructure helps establish direct connections between players where possible, and routes traffic through Epic's relay servers when a direct connection isn't available. Your IP address is exposed to whichever party, a peer or an Epic relay, is carrying your connection.
- **Quality of Service metrics**: Per Epic's Developer Agreement, the EOS SDK automatically collects a randomly generated session identifier, the number of API calls made, latency, and HTTP/internal status codes, for the sole purpose of operating the service.
- **International processing**: Epic may process this data on servers outside your country, including in the United States, under whatever transfer mechanism Epic has in place (see Epic's own privacy policy for details).

Epic Games, Inc. is the entity Lilith contracts with, not the user directly, but Epic's terms mean your data can be processed by Epic as part of using the service, regardless of who "owns" your account with Lilith. We do not control, and cannot make representations about, Epic's own data retention practices, security measures, or further sub-processors; see [Epic's privacy policy](https://www.epicgames.com/site/privacypolicy) for that.

## 5. Legal basis for processing (where applicable)

If you're located somewhere that requires a legal basis for processing (for example, under the GDPR), we rely on the following:

- **Performance of a contract**: processing your Minecraft UUID, username, PUID, session tokens, and connection metadata is necessary to provide the hosting/joining service you're requesting.
- **Legitimate interests**: processing diagnostic and error logs to maintain, secure, and debug the service, in a way that doesn't override your own privacy interests.
- **Consent**: where you actively choose settings such as your invite-privacy preference.

You can withdraw consent-based processing at any time by changing your settings or contacting us, without affecting processing already carried out.

## 6. How we use your data

We use the data above only to:

- Authenticate you and issue session tokens
- Create, host, and join sessions
- Deliver and manage invites
- Enforce your invite-privacy setting
- Diagnose and fix connectivity issues
- Detect, prevent, and respond to abuse, fraud, or security incidents affecting the service

We do not use your data for advertising, and we do not build user profiles for any purpose beyond operating Strand. We do not make decisions about you using automated processing that would produce legal or similarly significant effects.

## 7. Sharing

- **With Epic**, as described in Section 4, to provide login and P2P networking.
- **With other players**, limited to what's needed to connect and identify you in-game: your username, your EOS PUID (needed for peers to establish a P2P connection with you), and your IP address (only to the peer or relay actually carrying your connection).
- **With service providers**, limited to infrastructure providers who help us run the Strand backend (e.g., hosting), bound by confidentiality and data protection obligations. Our hosting services are powered by https://packetharbor.com.
- **For legal reasons**, if required to comply with a legal obligation, enforce our terms, or protect the rights, property, or safety of Lilith, our users, or others.
- We do **not** sell, rent, or otherwise disclose your data to third parties such as advertisers or data brokers, and we have not done so in the preceding 12 months.

## 8. Your rights and choices

- You can toggle whether other players can invite you directly, restricting joins to those who have your invite code.
- You can stop hosting or leave a session at any time, which ends the associated session and stream data.
- You may request deletion of your data by contacting us at hello@lilith.re. We will act on verified deletion requests within 30 days, except where we're required or permitted to retain data (e.g., ongoing abuse investigations, legal obligations).

**If you're in the European Economic Area, UK, or Switzerland (GDPR):** you have the right to access, correct, delete, restrict, or port your data, and to object to certain processing. You also have the right to lodge a complaint with your local data protection supervisory authority. Where we transfer your data internationally, we do so under an appropriate safeguard such as the EU Standard Contractual Clauses.

**If you're a California resident (CCPA/CPRA):** you have the right to know what personal information we collect and how it's used and shared, to request deletion, to correct inaccurate information, and to opt out of the sale or sharing of personal information (we do not sell or share personal information as those terms are defined by the CCPA). You can exercise these rights by contacting hello@lilith.re. We will not discriminate against you for exercising these rights.

**Other jurisdictions:** you may have similar rights under your local law (e.g., Brazil's LGPD, Canada's PIPEDA). Contact hello@lilith.re to exercise them, and we'll respond consistent with applicable law.

To protect your data, we may need to verify your identity before acting on a request, by confirming control of the Minecraft account associated with the data.

## 9. Children's privacy

Strand is not directed at children under 13, and we do not knowingly collect personal information from children under 13. Epic's own terms similarly restrict use of EOS Account-linked features for such users. If you believe a child under 13 has provided us data, contact hello@lilith.re and we will delete it.

## 10. Data breach notification

If a breach of your data occurs that creates a meaningful risk to you, we will notify affected users and any relevant regulator as required by applicable law, and without undue delay.

## 11. Security

We take reasonable technical and organizational steps to protect data in transit and at rest, and rely on Epic's own security measures for data processed through EOS. No system is perfectly secure, and we cannot guarantee absolute security.

## 12. Changes to this policy

We may update this policy from time to time. If we make material changes, we will update the "Last updated" date above. The latest version will always be available at this page.

## 13. Governing law

This policy is governed by the laws of the United States, without regard to conflict-of-law principles, except where local mandatory data protection law gives you additional rights that can't be waived.

## 14. Contact

Questions about this policy or your data can be sent to hello@lilith.re.