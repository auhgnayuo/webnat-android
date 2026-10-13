# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.4.0] - 2026-10-13

### Added

- **`onRenderProcessGone()`**: tells Webnat that the render process behind the `WebView` has exited (crash, or the `WebView` is about to be destroyed); Webnat then ends the page's connections itself. Same semantics on Android, Darwin, OHOS and Electron. The method is named after each platform's own hook: `webViewWebContentProcessDidTerminate` on Darwin, `onRenderProcessGone` on Android and Electron, `onRenderExited` on OHOS. The host calls it from its own `WebViewClient.onRenderProcessGone` and before it destroys the `WebView`. It is not a `@JavascriptInterface`, so page scripts cannot call it.

### Fixed

- A method handler that throws now makes the caller receive an error reply instead of waiting for its timeout (the exception used to be swallowed by the outermost `catch`).
- A failed send is no longer swallowed: `method` fails at once with `SERIALIZATION_FAILED` when the parameter cannot be serialized (JSON rejects `NaN` / `Infinity`) and with `CLOSED` when the `WebView` has been recycled or can no longer run posted work. A script failure inside the page is still not observable (`evaluateJavascript` has no error channel).
- A method result that cannot be serialized is answered with a `SERIALIZATION_FAILED` error reply instead of being dropped.
- A method handler that completes synchronously no longer leaves its cancel / close hooks registered forever.

### Changed

- `WebnatError.from(Throwable)` now uses the exception's `message` ("Unknown Error" when it has none) instead of `toString()`, so a plain exception reaches the peer as `boom` rather than `java.lang.IllegalStateException: boom`, as on the other platforms.

### Tests

- JVM unit tests now run `android.os` calls as stubs returning defaults (`unitTests.isReturnDefaultValues`) and use the real `org.json` implementation (`testImplementation`), because the `android.jar` one only has stubs.

## [1.2.1] - 2026-09-10

### Added

- `WebnatError.from` now accepts Kotlin `Map` error objects and recognizes the same code/message field aliases as `JSONObject`.
- Native method replies now serialize errors only through `WebnatError.from`; the extra `WebnatError` cast was redundant.

## [1.2.0] - 2026-07-02

### Added

- **`Webnat.method` broadcast**: when called without an explicit `connection`, the method call is now **broadcast to all connections** (main frame + every iframe) instead of an arbitrary first connection. The first connection to send a *meaningful* signal — a `notify`, a successful result, or a real business error — wins; the remaining sub-calls are immediately aborted and only the winner's notifications / final result are forwarded. Applies to both the callback and coroutine (`suspend`) overloads.
- **Elimination semantics for broadcast**: `unimplemented` (`-1010`) and `closed` (`-1004`) replies never win the race; they only remove that connection from the candidate pool. If *all* connections are eliminated, the call fails with `closed` (when any connection closed) otherwise `unimplemented`. Timeout / cancellation apply at the group level and abort all in-flight sub-calls (sub-calls carry no individual timeout). This is designed for the common "pass-through to iframe" case where only one frame registers a given handler.

### Note

- Passing an explicit `connection` keeps the previous single-connection behavior unchanged.

## [1.0.3] and earlier

See [git history](https://github.com/auhgnayuo/webnat-android/commits/main) for changes in prior releases.
