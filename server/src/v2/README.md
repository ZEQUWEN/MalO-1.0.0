# PostgreSQL account/payment module

This module is mounted under `/api/v2` by the normal server. See
[server/README.md](../../README.md) for configuration, endpoint contracts,
legacy cutover warnings, operational prerequisites and security limitations.

The Ko-fi verification token is a shared bearer secret, not a signature.
Do not treat this module as independently verified payment evidence. Manual
review, refund/dispute handling and production hardening remain necessary.
