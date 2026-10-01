# iOS first implementation

Scope: native Save / Library / Settings; iOS 18+; Liquid Glass on iOS 26+; material fallback; native Mac preview for local verification.

V1: accepted input → persisted job → transfer → media validation → Saved. Never label queued work Saved.
V2: share extension uses App Group + its own background URLSession. Containing app reattaches only for completion events.
V3: direct HTTPS media downloads on device. Social posts require explicit private bridge configuration. No default third-party server.
V4: preferences snapshot per job; duplicate active/saved request reused only while saved file exists.
V5: cancellation cannot become Saved; failed storage cannot become Saved; originals survive export.
V6: transparency changes surfaces only; text opaque; system Reduce Transparency wins; Reduce Motion disables custom motion.
V7: no automatic clipboard reading; explicit Paste only; invalid input preserved; accepted input cleared.
V8: endpoint token in Keychain; no telemetry; no external publication.

Architecture: Swift package (core + shared UI) / iOS app / Share Extension / optional Python bridge. Each job stored atomically under cross-process file lock. Native background URLSession owns transfers. Library owns validated media; Photos export separate, explicit result.

Validation: core tests; bridge tests; real direct-file transfer; native Mac build and visual inspection. Full iOS compile, simulator and iPhone checks require Xcode + iOS SDK (not installed at start).

Deferred: App Store submission; platform authorisation; on-device social extraction; scheduled downloads; arbitrary folder persistence; native media trimming; Live Activities. No claim of complete Android feature parity.
