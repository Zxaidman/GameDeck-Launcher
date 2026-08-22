# Testing signing key

**Document:** `signing/README.md`  
**Status:** Active — testing only  

## What this is

`kestrel-testing.p12` signs every build this repository produces. Its password is
`kestrel-testing`, written here on purpose, and it is **not a secret**.

## Why it is committed, and what it buys

Android treats an application's signature as its identity. Two builds signed by different keys are
two different applications, so the second cannot install over the first — the user has to uninstall,
which takes **every permission they granted and every setting they had** with it.

Without a fixed key that is exactly what happened. Gradle's default debug signing config generates
`~/.android/debug.keystore` on the machine that builds, and a CI runner is a fresh machine every
time, so **every build was signed by a different key** and every install was a reinstall. The
workflow's own release notes said so: *"signed with a per-machine debug key, so a newer build may
not count as an update."*

One committed key means a build from CI, a build from a laptop and a build from six months ago are
all the same application, and installing a new one is an update.

## What it is not

**Not a release key, and it must never become one.** A key checked into a public repository with its
password beside it can be used by anyone to sign an application that installs *as an update* over a
user's Kestrel. That is acceptable for builds people are testing deliberately, and unacceptable for
builds people are trusting.

A real release needs a key that is generated once, kept out of the repository, and injected from a
secret. When the first release is prepared:

1. Generate a release key and store it as a repository secret.
2. Add a `release` signing config that uses it, failing the build if the secret is absent rather
   than falling back to this one.
3. Leave this key in place for debug builds, which is all it was ever for.

Until that happens, every artifact this repository produces is a testing build and is described as
one wherever it is published.

## Regenerating it

Only if it is lost — regenerating it breaks the update path for anyone who installed a build signed
by the old one, exactly as if there had been no key at all.

```bash
keytool -genkeypair -v \
  -keystore signing/kestrel-testing.p12 -storetype PKCS12 \
  -alias kestrel-testing \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass kestrel-testing -keypass kestrel-testing \
  -dname "CN=Kestrel Testing Key, OU=Testing, O=Kestrel"
```
