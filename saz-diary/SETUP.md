# Saz's Diary: your own offline app

This folder is the whole app. It doesn't depend on Claude. Pages are stored on your device and encrypted with your passphrase (AES-256, key derived with PBKDF2). Backups are the same sealed data, so they're safe to keep in Google Drive.

## 1. Put it online once (free, about 5 minutes)

Phones only install web apps from an `https://` address. After the first visit, the app works fully offline.

**Easiest: Netlify Drop**
1. Go to https://app.netlify.com/drop and sign up (free).
2. Drag this whole `saz-diary` folder onto the page.
3. You'll get an address like `https://something.netlify.app`. Rename the site to something hard to guess.

**Or GitHub Pages**
1. Create a repo (it can be private if your plan allows Pages on private repos) and push these files to it.
2. Go to Settings → Pages → Deploy from branch → `main` / root.
3. The app will be at `https://<you>.github.io/<repo>/`.

Hosting the files reveals nothing private. The host only serves the empty app; your writing never leaves your devices except as the encrypted backups you choose to make.

## 2. Install it

- **Android (Chrome):** open the address, then ⋮ → Install app (or "Add to Home screen").
- **iPhone (Safari):** open the address, then Share → Add to Home Screen.
- **Laptop (Chrome/Edge):** open the address, then click the install icon in the address bar.

Open it once while online so it caches itself. After that it opens offline.

## 3. Back up to Google Drive

- **Phone:** tap **Back up to Drive**. In the share sheet, choose **Drive** and save it to a folder such as `Diary`.
- **Laptop with Google Drive for Desktop:** tap **Auto-save to a Drive file** and pick a file inside your Google Drive folder. From then on, every save also updates that file, and Drive syncs it. (Chrome or Edge only. After restarting the browser it may ask you to allow access again.)

The app warns you in red when your last backup is more than a week old.

## 4. Move or restore your pages

- **From the Claude diary:** unlock it and press **Export backup**. Upload that file to Drive.
- **New phone or laptop:** open the app, tap **Restore from a backup file** on the cover, pick the backup from Drive, then unlock with the same passphrase.
- **Already using the app:** press **Restore** to merge a backup in. For each day, the newer version wins.

## Things to know

- Each device keeps its own copy. Back up on one device and restore on another to bring them in line.
- If you clear the browser's site data, or uninstall the app, that device's copy is gone. Your Drive backup is what protects you.
- The **Speak** button uses the browser's speech service, which needs internet. Offline, use your keyboard's microphone key.
- A forgotten passphrase cannot be recovered. Not by you, not by anyone.
