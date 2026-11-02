# Fern Entra + Xbox Auth Setup

This guide configures Microsoft account sign-in for the Fern website and uses Xbox Live/XSTS to obtain the player’s Xbox identity and XUID.

The flow is:

```text
Fern website
  -> Microsoft Entra authorization-code OAuth
  -> Microsoft access token
  -> Xbox Live User Token
  -> Xbox Security Token Service token
  -> XUID / Xbox identity
  -> Fern player account and allowlist record
```

Microsoft Entra login and Minecraft server login are related but not identical. A successful Microsoft/Xbox login proves the user controls a Microsoft account with an Xbox identity. It does not automatically prove that the account owns Minecraft Java Edition, and it should not directly expose an admin or RCON endpoint to the browser.

## 1. Choose the Fern callback URL

Use one HTTPS callback URL on the real Fern domain. For example:

```text
https://fern.example.com/auth/microsoft/callback
```

For local testing, add a separate exact callback such as:

```text
http://localhost:8080/auth/microsoft/callback
```

Do not use a wildcard callback. Microsoft requires the redirect URI sent during login to exactly match a registered URI.

## 2. Register the Microsoft Entra application

On your phone:

1. Open [Microsoft Entra admin center](https://entra.microsoft.com/).
2. Open **Entra ID**.
3. Open **App registrations**.
4. Select **New registration**.
5. Set the name to:

```text
Fern Xbox Auth
```

6. Under **Supported account types**, select:

```text
Personal Microsoft accounts only
```

This is the appropriate choice for Minecraft/Xbox players using personal Microsoft accounts. If you later need both work/school and personal accounts, create a separate tested configuration using the broader supported-account option.

7. Under **Redirect URI**, choose **Web** and enter the exact HTTPS callback URL:

```text
https://fern.example.com/auth/microsoft/callback
```

8. Select **Register**.
9. Copy the **Application (client) ID**. This is safe to identify the app, but it is not a password.

For this Xbox website flow, use the `consumers` authority in the URLs below:

```text
https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize
https://login.microsoftonline.com/consumers/oauth2/v2.0/token
```

## 3. Create the server-side client secret

In the new app registration:

1. Open **Certificates & secrets**.
2. Select **New client secret**.
3. Set the description to:

```text
Fern production web callback
```

4. Choose the shortest practical expiration period.
5. Select **Add**.
6. Copy the **Value** immediately. Do not copy the Secret ID; Fern needs the secret value.

Store it only in a private server environment file. Do not put it in:

- `public_html`
- JavaScript
- HTML
- GitHub
- Discord messages
- screenshots

Recommended private variables:

```text
XBOX_CLIENT_ID=your-application-client-id
XBOX_CLIENT_SECRET=your-secret-value
XBOX_REDIRECT_URI=https://fern.example.com/auth/microsoft/callback
XBOX_TENANT=consumers
```

Rotate the secret before it expires. If the value is exposed, delete it immediately and create a replacement.

## 4. Configure the OAuth scopes

The Xbox website flow uses only these scopes:

```text
xboxlive.signin XboxLive.offline_access
```

`xboxlive.signin` allows the application to request Xbox services sign-in. `XboxLive.offline_access` is optional, but it is useful if Fern needs to refresh the sign-in after the initial access token expires.

Do not request Microsoft Graph mail, files, contacts, or unrelated permissions for this feature.

## 5. Build the Microsoft sign-in URL

The Fern backend should generate this URL instead of hard-coding a user-specific state value:

```text
https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=CLIENT_ID&response_type=code&redirect_uri=REDIRECT_URI&response_mode=query&scope=xboxlive.signin%20XboxLive.offline_access&state=RANDOM_STATE&code_challenge=PKCE_CHALLENGE&code_challenge_method=S256
```

Before redirecting the browser:

1. Generate a cryptographically random `state` value.
2. Generate a PKCE `code_verifier`.
3. Hash the verifier with SHA-256 and base64url-encode it to create `code_challenge`.
4. Store the state and verifier in the user’s short-lived server session.
5. Redirect the user to Microsoft.

The `state` prevents login CSRF. PKCE protects the authorization code if it is intercepted. Use the authorization-code flow, not an implicit token flow.

## 6. Receive the callback

Microsoft redirects the browser to:

```text
https://fern.example.com/auth/microsoft/callback?code=AUTHORIZATION_CODE&state=STATE
```

The callback handler must:

1. Read `code` and `state`.
2. Reject the request if `state` does not exactly match the server session value.
3. Reject missing or repeated authorization codes.
4. Send the code to the token endpoint from the server, never from browser JavaScript.
5. Send the same redirect URI used in the authorization request.
6. Include the PKCE `code_verifier`.

Exchange the code with:

```text
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token
Content-Type: application/x-www-form-urlencoded
```

Form fields:

```text
client_id=YOUR_CLIENT_ID
grant_type=authorization_code
code=AUTHORIZATION_CODE
redirect_uri=https://fern.example.com/auth/microsoft/callback
client_secret=YOUR_CLIENT_SECRET
code_verifier=ORIGINAL_PKCE_CODE_VERIFIER
```

The response contains an access token. Keep it server-side. Do not send the Microsoft access token to the browser.

## 7. Exchange the Microsoft token for an Xbox User Token

Call the Xbox user authentication endpoint from the Fern backend:

```text
POST https://user.auth.xboxlive.com/user/authenticate
Content-Type: application/json
x-xbl-contract-version: 1
```

Body:

```json
{
  "RelyingParty": "http://auth.xboxlive.com",
  "TokenType": "JWT",
  "Properties": {
    "AuthMethod": "RPS",
    "SiteName": "user.auth.xboxlive.com",
    "RpsTicket": "d=MICROSOFT_ACCESS_TOKEN"
  }
}
```

The response includes:

- `Token`: Xbox User Token
- `DisplayClaims.xui[0].uhs`: user hash
- expiry information

Do not log the token or the user hash.

## 8. Exchange the Xbox User Token for an XSTS token

Call:

```text
POST https://xsts.auth.xboxlive.com/xsts/authorize
Content-Type: application/json
x-xbl-contract-version: 1
```

Body:

```json
{
  "Properties": {
    "SandboxId": "XDKS.1",
    "UserTokens": ["XBOX_USER_TOKEN"]
  },
  "RelyingParty": "http://auth.xboxlive.com",
  "TokenType": "JWT"
}
```

The response includes:

- `Token`: XSTS token
- `DisplayClaims.xui[0].xid`: the Xbox user identifier/XUID when present
- `DisplayClaims.xui[0].uhs`: user hash

Store the XUID as a string. Do not convert it to a signed integer and do not remove leading zeroes.

A production implementation must handle Xbox error codes such as:

- `2148916233`: account does not have an Xbox profile
- `2148916235`: account is not eligible or has regional/account restrictions
- `2148916238`: child account or privacy settings prevent the request
- `8015B010`: Xbox account or consent issue

Show the player a normal “Xbox profile or privacy settings required” message. Never display raw tokens or full upstream error payloads.

## 9. Optional Xbox profile lookup

If Fern needs the player’s current Gamertag, call the Xbox profile service from the backend using the XSTS credentials. The authentication header format is:

```text
Authorization: XBL3.0 x=USER_HASH;XSTS_TOKEN
```

Use the returned profile data for display only. The stable internal identity should be the XUID, not the Gamertag, because Gamertags can change.

Store only the minimum data Fern needs:

```text
xuid
current_gamertag
microsoft_subject_or_account_key
created_at
last_login_at
consent_version
```

Do not store the Microsoft password. Fern never sees it.

## 10. Create the Fern player record

After validating the Xbox response, create or update the player record using the XUID as the stable key:

```text
players
  id
  xuid              unique, string
  gamertag
  java_uuid         nullable until Java join is observed
  bedrock_uuid      nullable until Floodgate join is observed
  allowlist_status  pending / approved / denied
  created_at
  last_login_at
```

Recommended first-login policy:

1. Create the player as `pending`.
2. Require email verification or Discord verification if your community policy requires it.
3. Allow an administrator to approve the player.
4. Only then send a server-side allowlist request.

The website must not call RCON directly. It should call the authenticated Fern Control Agent over a private connection. The agent validates the request and invokes a restricted server command.

## 11. Bedrock allowlisting with Geyser and Floodgate

For Bedrock players, install compatible Geyser and Floodgate versions for the server version. Bedrock clients connect through Geyser, and Floodgate supplies the Bedrock identity to the Java server.

Do not try to use the Microsoft/Xbox login page as a substitute for Floodgate. The website XUID record and the server-side Floodgate identity must be linked or matched by a server-side component.

The most reliable initial workflow is:

1. The player signs into Fern with Microsoft/Xbox Auth.
2. Fern stores the XUID as `pending`.
3. The player joins the Geyser server once.
4. Floodgate exposes the Bedrock username and UUID to the server.
5. The server bridge matches the observed XUID to the Fern record.
6. The server bridge approves the player and keeps the player on the allowlist.

Floodgate’s built-in command is:

```text
/fwhitelist add BEDROCK_USERNAME
```

The player must have joined a Geyser server before this command can resolve the Bedrock identity. The alternative is to temporarily disable the whitelist, let the player join once, add the Floodgate identity, and immediately enable the whitelist again.

Floodgate usernames commonly use a prefix such as `.`. Do not remove the prefix casually because Java and Bedrock users can have identical visible names.

Floodgate also supports linking a Java account and a Bedrock account. Linking is useful when the same person should share Java inventory, location, and achievements. Explain this consequence to the player before linking because the Java account’s data becomes the active data set.

## 12. Java allowlisting

Xbox XUID is not a Java UUID. For Java players, the server should observe the authenticated Java join and store the Java UUID. Then the Control Agent or server bridge can run:

```text
/whitelist add JAVA_USERNAME
```

For stronger identity matching, the server bridge should use the Java UUID from the authenticated server connection rather than trusting a name typed into the Fern website.

Do not add a player to the Java whitelist solely because someone typed a matching Gamertag into a form.

## 13. Minecraft ownership check: optional and separate

If Fern must require Minecraft ownership, add a separate server-side Minecraft Services check after Xbox/XSTS authentication. Treat this as an independent integration and test it with a small number of accounts first. Microsoft may restrict or reject Minecraft Services calls for an app registration even when Microsoft OAuth, Xbox Live, and XSTS authentication succeed.

Do not make the whole Fern login unusable while this optional check is being tested. Store a separate status:

```text
minecraft_ownership_status = unknown / verified / rejected / unavailable
```

For a private community server, Xbox identity plus administrator approval and a first-join Floodgate/Java identity match is usually simpler than making Minecraft Services access the only gate.

## 14. ReadySpace/cPanel configuration

Put these values in a private file outside `public_html`, or in the hosting provider’s environment-variable system:

```text
XBOX_CLIENT_ID=
XBOX_CLIENT_SECRET=
XBOX_REDIRECT_URI=https://fern.example.com/auth/microsoft/callback
XBOX_TENANT=consumers
FERN_AGENT_BASE_URL=https://private-agent.example.com
FERN_AGENT_HMAC_SECRET=
```

The website public files may contain the client ID and the login button, but must never contain:

- client secret
- Microsoft access token
- Xbox User Token
- XSTS token
- user hash
- Fern Control Agent HMAC secret
- RCON password

If ReadySpace cannot keep server environment variables private, keep the token exchange in a separate private backend service and let the cPanel site call only that service.

## 15. Test in this order

1. Register the app and test the exact HTTPS callback.
2. Confirm Microsoft sign-in returns a one-time code.
3. Confirm the backend exchanges the code for an access token.
4. Confirm the Xbox User Token exchange succeeds.
5. Confirm XSTS succeeds and returns an XUID.
6. Confirm Fern creates one player record and does not duplicate it on a second login.
7. Test an Xbox account without a profile and confirm the error is friendly.
8. Test a Bedrock account through Geyser/Floodgate.
9. Test a Java account separately.
10. Test approval and denial in the Fern panel.
11. Test that an unapproved player cannot trigger a server command.
12. Test token and secret redaction in PHP, Node, web server, and Control Agent logs.
13. Rotate the client secret in a staging copy before enabling production.

## 16. Important architecture decision

Use Entra/Xbox Auth for website identity and player registration. Use the Minecraft server’s actual authenticated join identity for final allowlist enforcement. Connect the two through a private server-side bridge or the Fern Control Agent.

Never expose RCON or the Control Agent directly to the public internet just to make the website login work.

## References

[1]: https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app "Register an application in Microsoft Entra ID"
[2]: https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow "Microsoft identity platform OAuth 2.0 authorization code flow"
[3]: https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-website-authentication?view=gdk-2604 "Xbox services sign-in for title websites"
[4]: https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc "OpenID Connect on the Microsoft identity platform"
[5]: https://geysermc.org/wiki/geyser/faq/ "Geyser FAQ"
[6]: https://geysermc.org/wiki/floodgate/linking/ "Floodgate account linking"
