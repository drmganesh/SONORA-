# NewPipe Extractor Android API 26 Compatibility Patch

- **Upstream Project:** `TeamNewPipe/NewPipeExtractor` (<https://github.com/TeamNewPipe/NewPipeExtractor>)
- **Upstream Base Tag:** `v0.27.1`
- **Patched Pinned Artifact:** `com.github.TeamNewPipe:NewPipeExtractor:v0.27.1-sonora.1`
- **License:** GNU General Public License v3.0 or later (see `LICENSE` in this directory and `META-INF/LICENSE` inside the JAR; original copyright and license notices are preserved verbatim).

## Problem Fixed

`org.schabi.newpipe.extractor.utils.Utils` in `v0.27.1` called:
- `URLEncoder.encode(string, StandardCharsets.UTF_8)`
- `URLDecoder.decode(url, StandardCharsets.UTF_8)`

Both `(String, Charset)` overloads were introduced in Java 10 and added to Android only in **API 33 (Android 13)**. Core library desugaring (`desugar_jdk_libs:2.1.5`) does not rewrite `java.net.URLDecoder.decode(String, Charset)` or `java.net.URLEncoder.encode(String, Charset)`, causing a fatal `NoSuchMethodError` on Android 8.0–12 (API 26–32).

## Patch Summary

Replaced the API 33 overloads in `patch/src/main/java/org/schabi/newpipe/extractor/utils/Utils.java` with the API 1–36+ charset-name overloads:
- `URLEncoder.encode(string, StandardCharsets.UTF_8.name())`
- `URLDecoder.decode(url, StandardCharsets.UTF_8.name())`

handling checked `UnsupportedEncodingException` cleanly (UTF-8 is guaranteed by the Java specification and Android runtime).
