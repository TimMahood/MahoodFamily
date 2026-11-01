# Google Cloud setup (one-off, free, about 10 minutes)

The app signs in with Google to use Drive. Google needs to know the app exists.
Nothing here costs money and no server is created.

1. Go to <https://console.cloud.google.com/> and create a project called **Mahood Family**.
2. **Enable the Drive API:** APIs & Services › Library › search "Google Drive API" › Enable.
3. **Consent screen:** APIs & Services › OAuth consent screen (Google Auth Platform).
   - App name: Mahood Family. User type: **External**. Add your email as support/contact.
   - Data access / scopes: add `.../auth/drive`.
   - Audience: leave publishing status as **Testing** and add every family member's
     Gmail address under **Test users** (up to 100).
4. **Android client:** APIs & Services › Credentials › Create credentials › OAuth client ID.
   - Application type: **Android**
   - Package name: `com.mahood.family`
   - SHA-1 certificate fingerprint:
     `90:57:B4:37:55:1A:0B:2A:63:57:EA:04:B2:34:A4:A6:FA:DF:68:6F`
5. Done. No keys need copying into the app.

### Known limits

- In Testing mode Google may ask members to approve access again every week or so.
  The app shows a "Sign in again" banner when that happens.
- Only the Gmail addresses listed as test users can sign in.
