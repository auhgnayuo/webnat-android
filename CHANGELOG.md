# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.0] - 2026-07-02

### Added

- **`Webnat.method` broadcast**: when called without an explicit `connection`, the method call is now **broadcast to all connections** (main frame + every iframe) instead of an arbitrary first connection. The first connection to send a *meaningful* signal — a `notify`, a successful result, or a real business error — wins; the remaining sub-calls are immediately aborted and only the winner's notifications / final result are forwarded. Applies to both the callback and coroutine (`suspend`) overloads.
- **Elimination semantics for broadcast**: `unimplemented` (`-1010`) and `closed` (`-1004`) replies never win the race; they only remove that connection from the candidate pool. If *all* connections are eliminated, the call fails with `closed` (when any connection closed) otherwise `unimplemented`. Timeout / cancellation apply at the group level and abort all in-flight sub-calls (sub-calls carry no individual timeout). This is designed for the common "pass-through to iframe" case where only one frame registers a given handler.

### Note

- Passing an explicit `connection` keeps the previous single-connection behavior unchanged.

## [1.0.3] and earlier

See [git history](https://github.com/auhgnayuo/webnat-android/commits/main) for changes in prior releases.
