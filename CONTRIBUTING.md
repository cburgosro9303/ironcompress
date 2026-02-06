# Contributing to IronCompress

Thank you for your interest in contributing to IronCompress!

## Getting Started

### Prerequisites

- Rust 1.93.0+
- JDK 25+
- Gradle (wrapper included)

### Building

```bash
# Rust
cd rust && cargo build --release

# Java
cd java && ./gradlew test -Pnative.lib.path=../rust/target/release

# Benchmark
cd benchmark && ./gradlew test -Pnative.lib.path=../rust/target/release
```

## How to Contribute

### Reporting Issues

Use the [issue templates](https://github.com/cburgosro9303/ironcompress/issues/new/choose) to report bugs, request features, or suggest improvements.

### Submitting Changes

1. Fork the repository (if external contributor)
2. Create a feature branch from `master`: `git checkout -b feat/my-feature`
3. Make your changes following the conventions below
4. Ensure all tests pass (Rust + Java + Benchmark)
5. Commit using [Conventional Commits](https://www.conventionalcommits.org/)
6. Open a Pull Request against `master`

### Commit Convention

This project uses Conventional Commits for automatic semantic versioning:

- `feat:` — new feature (triggers minor version bump)
- `fix:` — bug fix (triggers patch version bump)
- `chore:` — maintenance tasks (no version bump)
- `docs:` — documentation changes
- `refactor:` — code refactoring
- `test:` — test additions or changes
- Breaking changes: add `!` after the type (e.g., `feat!:`) to trigger a major version bump

### Pull Request Requirements

- All CI checks must pass (Rust build + test, Java test, Benchmark test)
- At least one approving review from a repository owner
- Follow the PR template provided

## Code Guidelines

### Rust

- Run `cargo fmt` before committing
- Run `cargo clippy` and fix all warnings
- Wrap all `extern "C"` functions with `catch_unwind` (panic firewall)
- Use the `log` crate for structured logging

### Java

- Use `java.lang.System.Logger` for logging (no external dependencies)
- Follow existing package structure: `io.ironcompress` for public API, `io.ironcompress.ffi` for native bindings
- All public methods must handle native error codes and throw `IronCompressException`

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
