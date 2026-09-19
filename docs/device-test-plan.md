# Galaxy S24 Ultra acceptance checklist

Run on the real phone before trusting unattended downloads. Record Android/One UI version and the engine version shown in SnapTok Settings. An emulator cannot establish Samsung power-management behaviour.

- Install the APK and open SnapTok. Grant notification permission and confirm the engine version in Settings. This build already includes the verified 2026.08.19 payload.
- Share a permitted public TikTok video via TikTok's share sheet. Confirm the receiver dismisses without a second download tap and returns to TikTok.
- Wait for completion. Check the gallery's Movies/SnapTok folder, play the whole file and verify sound. Confirm history says Saved and Play video opens it.
- Paste the same canonical URL with different tracking parameters. Confirm no duplicate download.
- Share several different posts quickly. Confirm every request remains visible and eventually saves or produces an actionable failure.
- Lock the screen during transfer; unlock and verify a complete file.
- Disconnect Wi-Fi/mobile data mid-transfer; restore connectivity. Check bounded automatic retries and eventual completion.
- Reboot with a queued request. Restore connectivity and confirm recovery after unlock.
- Force-stop SnapTok. Do not expect work while force-stopped. Reopen it and verify incomplete work is reconciled.
- Cancel an active request and verify it does not publish a partial gallery entry. Retry it and verify one finished file.
- Test a private/deleted post, slideshow, unrelated URL and low-storage condition. Confirm there is no Saved status or partial visible file.
- Deny notification permission. Confirm the queue still works and errors remain visible in history.
- Increase system font size. Verify buttons and status text remain usable.
- Samsung: if downloads are delayed, check Settings > Apps > SnapTok > Battery and any Sleeping apps list. Do not disable battery protections globally.

Evidence must distinguish queue persistence, extraction success, valid playback and real-device background recovery. Passing one is not proof of the others.
