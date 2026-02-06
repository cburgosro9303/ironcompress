## Summary

<!-- Brief description of the changes -->

## Type of Change

- [ ] New feature
- [ ] Bug fix
- [ ] Improvement / Refactor
- [ ] Documentation
- [ ] CI / Build

## Related Issues

<!-- Link related issues: Closes #123, Fixes #456 -->

## Changes

-

## How to Test

<!-- Steps to verify the changes -->

1.

## Checklist

- [ ] Rust tests pass (`cd rust && cargo test`)
- [ ] Java tests pass (`cd java && ./gradlew test -Pnative.lib.path=../rust/target/release`)
- [ ] Benchmark tests pass (`cd benchmark && ./gradlew test -Pnative.lib.path=../rust/target/release`)
- [ ] `cargo fmt --check` passes
- [ ] `cargo clippy -- -D warnings` passes
- [ ] No breaking changes to the public API (or documented in this PR)
