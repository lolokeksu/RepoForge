# Security

[English](SECURITY.md) | [Русский](SECURITY.ru.md)

RepoForge is designed to create clean project archives while excluding common build artifacts, local files, signing keys, and secrets.

## Excluded sensitive files

RepoForge excludes common sensitive files such as:

- `local.properties`
- `.env`
- `.env.local`
- `secrets.properties`
- `google-services.json`
- `service-account.json`
- `*.jks`
- `*.keystore`
- `*.pem`
- `*.key`
- `*.p12`
- `*.pfx`
- `*.crt`
- `*.cer`

## Important note

RepoForge reduces the risk of accidentally sharing sensitive files, but users should still review generated archives before publishing or sending them to third parties.

## Reporting issues

Security issues can be reported through GitHub Issues.
