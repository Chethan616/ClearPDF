# Licensing and attribution policy

Artefact is distributed under the MIT License or Apache License, Version 2.0, at the recipient's
option. Third-party code, data, fonts, models, and media retain their own licenses and required
notices; the project license does not relicense them.

Before adding material that will be committed, bundled into a release, or used to build a release:

1. Record its exact source, creator, version or revision where available, license, and intended use
   in `ATTRIBUTION.toml` (or the build-input / fetched sections for material supplied at build time).
2. Keep the required license text or notice in the repository or the distributed application, and
   summarize material obligations in `NOTICE`.
3. Check that the license allows the planned use and redistribution, including any attribution,
   share-alike, trademark, or reserved-font-name terms. A download being free is not, by itself,
   evidence that redistribution is permitted.
4. Do not commit third-party material when its source or redistribution rights cannot be verified.
   Use a project-authored replacement or ask the rights holder for permission.
5. Keep hashes for tracked binary assets in `ATTRIBUTION.toml` so later changes can be reviewed.

For Rust dependencies, keep package license metadata and applicable notices. Review the actual
dependency graph for each distributed target; declared license metadata alone does not establish
compatibility. Review Android dependencies and generated/bundled resources as part of the same
release audit.

This policy is a maintenance checklist, not a legal opinion or a guarantee against claims. For
uncertain rights, obtain permission or advice from qualified counsel before distribution.
