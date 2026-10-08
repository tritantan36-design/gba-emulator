# ADR-002-offline-v1

Status: Accepted
Date: 2026-10-06

Decision: zero Android permissions, no INTERNET, Analytics, Ads, account, dynamic downloads or networking SDK.
Build-time source/dependency acquisition is separate from app runtime; app is fully offline.
Any future networking requires updating PROJECT_SPEC and reviewing the security model first.
