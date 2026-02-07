# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| Latest  | Yes                |
| Older   | No                 |

Only the latest release receives security updates.

## Reporting a Vulnerability

If you discover a security vulnerability in IronCompress, please report it responsibly.

**Do NOT open a public issue.**

Instead, send an email to **cburgosro9303@github.com** with:

- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

You can expect an initial response within 72 hours. We will work with you to understand the issue and coordinate a fix before any public disclosure.

## Scope

This policy covers:

- The Rust native library (`rust/`)
- The Java FFM bindings and public API (`java/`)
- CI/CD workflows and publishing pipeline

## Security Considerations

IronCompress uses Foreign Function & Memory (FFM) API to call native Rust code. Key security measures in place:

- **Panic firewall**: All `extern "C"` functions are wrapped in `catch_unwind` to prevent Rust panics from unwinding into Java
- **Null pointer validation**: All FFM entry points validate pointer arguments before dereferencing
- **No `unsafe` in Java**: All native memory access uses the safe FFM API with confined arenas
- **No JNI**: Eliminates a class of memory safety issues common in JNI-based libraries
